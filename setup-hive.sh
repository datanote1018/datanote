#!/bin/bash
# ============================================================
# 第一步：安装 Hadoop + Hive 环境（Docker）
# 启动顺序：MySQL → HDFS(NameNode + DataNode) → Hive(Metastore + HiveServer2)
#
# 使用：chmod +x setup-hive.sh && ./setup-hive.sh
# 停止：./setup-hive.sh stop
# 清理：./setup-hive.sh clean
# 验证：./setup-hive.sh test
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF_FILE="$SCRIPT_DIR/datanote.conf"

# ---------- 加载配置 ----------
if [ ! -f "$CONF_FILE" ]; then
  echo "配置文件不存在，正在生成默认配置: $CONF_FILE"
  cat > "$CONF_FILE" <<'CONF'
# DataNote 部署配置（两个脚本共享）
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_PASSWORD=root
HIVE_PORT=10800
HDFS_WEB_PORT=9870
DATANOTE_PORT=8099
NETWORK=datanote-net
CONF
fi

source "$CONF_FILE"

# ---------- 颜色输出 ----------
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---------- 停止 ----------
if [ "$1" = "stop" ]; then
  info "停止 Hive 环境..."
  for c in datanote-hiveserver2 datanote-metastore datanote-datanode datanote-namenode datanote-mysql; do
    docker stop $c 2>/dev/null && docker rm $c 2>/dev/null && info "已停止 $c" || true
  done
  info "全部停止"
  exit 0
fi

# ---------- 清理 ----------
if [ "$1" = "clean" ]; then
  info "停止并清理所有容器和数据..."
  for c in datanote-hiveserver2 datanote-metastore datanote-datanode datanote-namenode datanote-mysql; do
    docker stop $c 2>/dev/null && docker rm $c 2>/dev/null || true
  done
  docker volume rm datanote-mysql-data datanote-namenode-data datanote-datanode-data 2>/dev/null || true
  docker network rm $NETWORK 2>/dev/null || true
  info "清理完成"
  exit 0
fi

# ---------- 测试连接 ----------
if [ "$1" = "test" ]; then
  info "测试 HiveServer2 连接..."
  docker exec datanote-hiveserver2 beeline -u 'jdbc:hive2://localhost:10000/default;auth=noSasl' -e 'SHOW DATABASES;' 2>/dev/null
  if [ $? -eq 0 ]; then
    info "HiveServer2 连接正常！"
    echo ""
    echo "  连接信息（配置 DataNote 时使用）："
    echo "  ─────────────────────────────────"
    echo "  HiveServer2:  localhost:${HIVE_PORT}"
    echo "  认证方式:      NOSASL"
    echo "  HDFS NameNode: hdfs://localhost:8020"
    echo "  MySQL:         localhost:${MYSQL_PORT} (root / ${MYSQL_PASSWORD})"
    echo ""
    info "Hive 环境就绪，可以运行 ./setup-datanote.sh 安装 DataNote"
  else
    error "HiveServer2 连接失败，请检查容器日志：docker logs datanote-hiveserver2"
  fi
  exit 0
fi

# ---------- 前置检查 ----------
if ! command -v docker &>/dev/null; then
  error "请先安装 Docker：https://docs.docker.com/get-docker/"
  exit 1
fi

if ! docker info &>/dev/null; then
  error "Docker 未启动，请先启动 Docker Desktop"
  exit 1
fi

# ---------- 端口冲突检测 ----------
check_port() {
  local port=$1 name=$2 conf_key=$3
  if lsof -i:${port} &>/dev/null; then
    warn "端口 ${port} 已被占用（${name}）！"
    echo ""
    echo "  请选择："
    echo "  1) 自动换用 $((port+1)) 端口"
    echo "  2) 使用本地已有的 ${name}（不启动 Docker ${name}）"
    echo "  3) 退出，我自己处理"
    echo ""
    read -p "  请输入 [1/2/3]（默认 1）: " choice
    choice=${choice:-1}

    if [ "$choice" = "1" ]; then
      local new_port=$((port+1))
      # 更新配置文件
      sed -i '' "s/^${conf_key}=.*/${conf_key}=${new_port}/" "$CONF_FILE" 2>/dev/null || \
      sed -i "s/^${conf_key}=.*/${conf_key}=${new_port}/" "$CONF_FILE"
      info "已将 ${name} 端口改为 ${new_port}（已写入 datanote.conf）"
      eval "${conf_key}=${new_port}"
      return 0  # 继续启动
    elif [ "$choice" = "2" ]; then
      info "跳过 Docker ${name}，使用本地已有服务"
      return 1  # 跳过启动
    else
      error "退出部署"
      exit 1
    fi
  fi
  return 0
}

SKIP_MYSQL=false
# 只在首次创建 MySQL 容器时检测端口，重跑时 Docker 自己占着端口不算冲突
if ! docker ps -a --format '{{.Names}}' | grep -q datanote-mysql; then
  check_port $MYSQL_PORT "MySQL" "MYSQL_PORT" || SKIP_MYSQL=true
fi

# ---------- MySQL JDBC 驱动 ----------
JDBC_JAR="$SCRIPT_DIR/docker/mysql-connector-j-8.0.33.jar"
if [ ! -f "$JDBC_JAR" ]; then
  info "下载 MySQL JDBC 驱动..."
  mkdir -p "$SCRIPT_DIR/docker"
  curl -sSL -o "$JDBC_JAR" \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar"
  info "驱动已下载"
fi

# ---------- 创建网络 ----------
docker network inspect $NETWORK &>/dev/null || {
  info "创建 Docker 网络: $NETWORK"
  docker network create $NETWORK
}

# ---------- 等待工具 ----------
wait_for() {
  local name=$1 cmd=$2 max=$3
  local i=0
  while [ $i -lt $max ]; do
    if eval "$cmd" &>/dev/null; then
      info "$name 就绪"
      return 0
    fi
    sleep 3
    i=$((i+1))
    echo -n "."
  done
  echo ""
  warn "$name 等待超时，继续执行..."
  return 1
}

# ==================== 1. MySQL ====================
if [ "$SKIP_MYSQL" = "true" ]; then
  info "使用本地 MySQL（端口 ${MYSQL_PORT}）"
elif docker ps -a --format '{{.Names}}' | grep -q datanote-mysql; then
  info "MySQL 已存在，跳过"
else
  info "启动 MySQL（端口 ${MYSQL_PORT}）..."
  docker run -d \
    --name datanote-mysql \
    --network $NETWORK \
    --hostname mysql \
    -p ${MYSQL_PORT}:3306 \
    -e MYSQL_ROOT_PASSWORD=$MYSQL_PASSWORD \
    -e MYSQL_DATABASE=datanote \
    -v datanote-mysql-data:/var/lib/mysql \
    -v "$SCRIPT_DIR/sql/init-all.sql":/docker-entrypoint-initdb.d/01_init.sql \
    --restart unless-stopped \
    mysql:8.0 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    --default-authentication-plugin=mysql_native_password

  echo -n "等待 MySQL 启动"
  wait_for "MySQL" "docker exec datanote-mysql mysqladmin ping -h localhost -p$MYSQL_PASSWORD" 20
fi

# ==================== 2. HDFS NameNode ====================
if docker ps -a --format '{{.Names}}' | grep -q datanote-namenode; then
  info "NameNode 已存在，跳过"
else
  # 初始化卷权限（只 chown，不创建 current 子目录，否则镜像会跳过格式化）
  info "初始化 HDFS NameNode 数据卷..."
  docker run --rm --user root \
    -v datanote-namenode-data:/data \
    apache/hadoop:3 \
    bash -c "chown -R hadoop:hadoop /data" 2>/dev/null || true

  info "启动 HDFS NameNode..."
  docker run -d \
    --name datanote-namenode \
    --platform linux/amd64 \
    --network $NETWORK \
    --hostname namenode \
    -p ${HDFS_WEB_PORT}:9870 \
    -p 8020:8020 \
    -e ENSURE_NAMENODE_DIR="/tmp/hadoop-hadoop/dfs/name/current" \
    -e CORE-SITE.XML_fs.defaultFS="hdfs://namenode:8020" \
    -e HDFS-SITE.XML_dfs.replication=1 \
    -e HDFS-SITE.XML_dfs.permissions.enabled=false \
    -v datanote-namenode-data:/tmp/hadoop-hadoop/dfs/name \
    --restart unless-stopped \
    apache/hadoop:3 \
    hdfs namenode

  sleep 5
fi

# ==================== 3. HDFS DataNode ====================
if docker ps -a --format '{{.Names}}' | grep -q datanote-datanode; then
  info "DataNode 已存在，跳过"
else
  # 初始化卷权限和目录结构
  info "初始化 HDFS DataNode 数据卷..."
  docker run --rm --user root \
    -v datanote-datanode-data:/data \
    apache/hadoop:3 \
    bash -c "mkdir -p /data && chown -R hadoop:hadoop /data" 2>/dev/null || true

  info "启动 HDFS DataNode..."
  docker run -d \
    --name datanote-datanode \
    --platform linux/amd64 \
    --network $NETWORK \
    --hostname datanode \
    -e CORE-SITE.XML_fs.defaultFS="hdfs://namenode:8020" \
    -e HDFS-SITE.XML_dfs.replication=1 \
    -v datanote-datanode-data:/tmp/hadoop-hadoop/dfs/data \
    --restart unless-stopped \
    apache/hadoop:3 \
    hdfs datanode

  echo -n "等待 HDFS 就绪"
  wait_for "HDFS" "docker exec datanote-namenode hdfs dfs -ls /" 20
fi

# 初始化 HDFS 目录
info "初始化 HDFS 目录..."
docker exec datanote-namenode hdfs dfs -mkdir -p /user/hive/warehouse 2>/dev/null || true
docker exec datanote-namenode hdfs dfs -mkdir -p /tmp 2>/dev/null || true
docker exec datanote-namenode hdfs dfs -mkdir -p /tmp/hive 2>/dev/null || true
docker exec datanote-namenode hdfs dfs -chmod -R 777 /user/hive/warehouse 2>/dev/null || true
docker exec datanote-namenode hdfs dfs -chmod -R 777 /tmp 2>/dev/null || true
docker exec datanote-namenode hdfs dfs -chmod -R 777 /tmp/hive 2>/dev/null || true

# YARN 不启动（Tez local mode 不需要，省资源）

# ==================== 6. Hive Metastore ====================
if docker ps -a --format '{{.Names}}' | grep -q datanote-metastore; then
  info "Hive Metastore 已存在，跳过"
else
  # Metastore 连接的 MySQL：Docker 内走容器网络（mysql:3306），本地走 host.docker.internal:实际端口
  if [ "$SKIP_MYSQL" = "true" ]; then
    METASTORE_MYSQL_HOST="host.docker.internal"
    METASTORE_MYSQL_PORT="${MYSQL_PORT}"
  else
    METASTORE_MYSQL_HOST="mysql"
    METASTORE_MYSQL_PORT="3306"  # 容器内部端口固定 3306
  fi

  # 初始化 Metastore schema（首次必须）。
  # 镜像入口在 IS_RESUME=true 时会跳过 schematool，若 MySQL 里没有建表，
  # metastore 启动会报 "Version information not found in metastore." 并无限重启。
  # schematool 会加载 hive-site.xml（engine=tez），需把 tez 放进 classpath，否则报 TezConfiguration ClassNotFound。
  info "初始化 Hive Metastore schema..."
  SCHEMA_LOG=$(docker run --rm --network $NETWORK \
    --platform linux/amd64 \
    -e HADOOP_CLASSPATH='/opt/tez/*:/opt/tez/lib/*' \
    --mount type=bind,source="$JDBC_JAR",target=/opt/hive/lib/mysql-connector-j-8.0.33.jar \
    --entrypoint /opt/hive/bin/schematool \
    apache/hive:3.1.3 \
    -dbType mysql -initSchema \
    -url "jdbc:mysql://${METASTORE_MYSQL_HOST}:${METASTORE_MYSQL_PORT}/hive_metastore?createDatabaseIfNotExist=true&useSSL=false" \
    -driver com.mysql.cj.jdbc.Driver -userName root -passWord "$MYSQL_PASSWORD" 2>&1) || true
  if echo "$SCHEMA_LOG" | grep -qi 'schemaTool completed'; then
    info "Metastore schema 初始化完成"
  elif echo "$SCHEMA_LOG" | grep -qi 'already exists'; then
    info "Metastore schema 已存在，跳过"
  else
    warn "Metastore schema 初始化可能失败，继续启动（详见下方日志）："
    echo "$SCHEMA_LOG" | tail -6
  fi

  info "启动 Hive Metastore..."
  docker run -d \
    --name datanote-metastore \
    --platform linux/amd64 \
    --network $NETWORK \
    --hostname metastore \
    -p 9083:9083 \
    -e SERVICE_NAME=metastore \
    -e DB_DRIVER=mysql \
    -e IS_RESUME=true \
    -e HIVE_CUSTOM_CONF_DIR=/opt/custom-conf \
    -e SERVICE_OPTS="\
-Djavax.jdo.option.ConnectionURL=jdbc:mysql://${METASTORE_MYSQL_HOST}:${METASTORE_MYSQL_PORT}/hive_metastore?createDatabaseIfNotExist=true&useSSL=false \
-Djavax.jdo.option.ConnectionDriverName=com.mysql.cj.jdbc.Driver \
-Djavax.jdo.option.ConnectionUserName=root \
-Djavax.jdo.option.ConnectionPassword=$MYSQL_PASSWORD" \
    --mount type=bind,source="$JDBC_JAR",target=/opt/hive/lib/mysql-connector-j-8.0.33.jar \
    --mount type=bind,source="$SCRIPT_DIR/docker/hive-site.xml",target=/opt/custom-conf/hive-site.xml \
    --mount type=bind,source="$SCRIPT_DIR/docker/core-site.xml",target=/opt/custom-conf/core-site.xml \
    --restart unless-stopped \
    apache/hive:3.1.3

  echo -n "等待 Hive Metastore 启动"
  sleep 15
  info "Hive Metastore 已启动"
fi

# ==================== 5. HiveServer2 ====================
if docker ps -a --format '{{.Names}}' | grep -q datanote-hiveserver2; then
  info "HiveServer2 已存在，跳过"
else
  info "启动 HiveServer2..."
  docker run -d \
    --name datanote-hiveserver2 \
    --platform linux/amd64 \
    --network $NETWORK \
    --hostname hiveserver2 \
    -p ${HIVE_PORT}:10000 \
    -e SERVICE_NAME=hiveserver2 \
    -e IS_RESUME=true \
    -e HIVE_CUSTOM_CONF_DIR=/opt/custom-conf \
    -e SERVICE_OPTS="\
-Xms512m -Xmx2g \
-Dhive.metastore.uris=thrift://metastore:9083 \
-Dhive.server2.authentication=NOSASL \
-Dhive.server2.enable.doAs=false \
-Dhive.server2.thrift.bind.host=0.0.0.0 \
-Dhive.execution.engine=tez \
-Dtez.local.mode=true \
-Dtez.local.mode.without.network=true" \
    --mount type=bind,source="$SCRIPT_DIR/docker/hive-site.xml",target=/opt/custom-conf/hive-site.xml \
    --mount type=bind,source="$SCRIPT_DIR/docker/core-site.xml",target=/opt/custom-conf/core-site.xml \
    --restart unless-stopped \
    apache/hive:3.1.3

  # amd64 镜像在 arm64 主机上走 QEMU 模拟，HiveServer2 首次绑定端口较慢，超时给足
  echo -n "等待 HiveServer2 就绪"
  wait_for "HiveServer2" "docker exec datanote-hiveserver2 beeline -u 'jdbc:hive2://localhost:10000/default;auth=noSasl' -e 'SELECT 1;'" 120
fi

# 创建数仓分层库
info "创建数仓分层库（ods/dwd/dws/ads/dim）..."
for db in ods dwd dws ads dim; do
  docker exec datanote-hiveserver2 beeline -u 'jdbc:hive2://localhost:10000/default;auth=noSasl' \
    -e "CREATE DATABASE IF NOT EXISTS $db;" 2>/dev/null || true
done

# ==================== 完成 ====================
echo ""
echo "============================================"
info "Hadoop + Hive 环境部署完成！"
echo ""
echo "  HDFS Web UI:  http://localhost:${HDFS_WEB_PORT}"
echo "  HiveServer2:  localhost:${HIVE_PORT}"
echo "  MySQL:        localhost:${MYSQL_PORT} (root / ${MYSQL_PASSWORD})"
echo ""
echo "  配置文件：$CONF_FILE"
echo "  验证：./setup-hive.sh test"
echo "  下一步：./setup-datanote.sh"
echo "============================================"
