#!/bin/bash
# ============================================================
# 第二步：安装 DataNote
# 前提：已运行 setup-hive.sh 或已有 Hadoop + Hive 环境
#
# 使用：chmod +x setup-datanote.sh && ./setup-datanote.sh
# 停止：./setup-datanote.sh stop
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF_FILE="$SCRIPT_DIR/datanote.conf"
JAR_FILE="$SCRIPT_DIR/target/datanote-1.0.0.jar"

# ---------- 颜色输出 ----------
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---------- 加载配置 ----------
if [ ! -f "$CONF_FILE" ]; then
  error "配置文件不存在: $CONF_FILE"
  echo "  请先运行 ./setup-hive.sh 或手动创建配置文件"
  exit 1
fi

source "$CONF_FILE"

# 从配置文件派生连接信息
DB_HOST=${MYSQL_HOST:-127.0.0.1}
DB_PORT=${MYSQL_PORT}
DB_USER=root
DB_PASS=${MYSQL_PASSWORD}

# AI API Key 从 ai-service/.env 注入（与 Python 侧共用同一份配置，避免两处不一致）
AI_ENV="$SCRIPT_DIR/ai-service/.env"
if [ -f "$AI_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$AI_ENV"
  set +a
fi

# ---------- 停止 ----------
if [ "$1" = "stop" ]; then
  PID=$(lsof -ti:$DATANOTE_PORT 2>/dev/null | head -1)
  if [ -n "$PID" ]; then
    kill $PID
    info "DataNote 已停止 (PID: $PID)"
  else
    warn "DataNote 未在运行"
  fi
  exit 0
fi

# ---------- 前置检查 ----------
if ! command -v java &>/dev/null; then
  error "请先安装 Java 8+：https://adoptium.net/"
  exit 1
fi

info "Java: $(java -version 2>&1 | head -1)"
info "配置文件: $CONF_FILE"
info "MySQL: ${DB_HOST}:${DB_PORT}"

# 检查 MySQL 连通性
if command -v mysql &>/dev/null; then
  if mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS --ssl-mode=DISABLED -e "SELECT 1;" &>/dev/null; then
    info "MySQL 连接正常"
  else
    warn "MySQL 连接失败（${DB_HOST}:${DB_PORT}），请确认 MySQL 已启动"
    warn "继续执行，DataNote 启动时会重试连接..."
  fi
else
  warn "mysql 客户端未安装，跳过连接检查"
fi

# ---------- 检查端口占用 ----------
EXISTING_PID=$(lsof -ti:$DATANOTE_PORT 2>/dev/null | head -1)
if [ -n "$EXISTING_PID" ]; then
  warn "端口 $DATANOTE_PORT 已被占用 (PID: $EXISTING_PID)"
  read -p "是否停止旧进程？[Y/n] " answer
  if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
    kill $EXISTING_PID
    sleep 2
    info "旧进程已停止"
  else
    error "端口被占用，退出"
    exit 1
  fi
fi

# ---------- 编译（如果 JAR 不存在）----------
# 本项目是 Java 8。Maven 若默认用新版 JDK，Lombok 注解处理会失效，
# 编译时报一堆 "找不到符号 setXxx / log" —— 必须显式指定 Java 8。
find_java8() {
  local c
  for c in \
    "$JAVA8_HOME" \
    /Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
    /Library/Java/JavaVirtualMachines/jdk1.8.0_231.jdk/Contents/Home \
    "$(/usr/libexec/java_home -v 1.8 2>/dev/null)"; do
    [ -n "$c" ] && [ -x "$c/bin/javac" ] && { echo "$c"; return 0; }
  done
  return 1
}

if [ ! -f "$JAR_FILE" ]; then
  info "JAR 包不存在，开始编译..."
  if ! command -v mvn &>/dev/null; then
    error "JAR 包不存在且 Maven 未安装"
    echo "  编译：mvn package -DskipTests"
    echo "  或从 GitHub Release 下载 JAR 放到 $SCRIPT_DIR/target/ 目录"
    exit 1
  fi
  if J8="$(find_java8)"; then
    export JAVA_HOME="$J8"
    info "使用 Java 8 编译: $JAVA_HOME"
  else
    warn "未找到 Java 8 JDK，将用默认 JDK 编译（Lombok 可能报「找不到符号」）"
    warn "  安装：brew install --cask zulu@8    或设置 JAVA8_HOME 指向 Java 8"
  fi
  cd "$SCRIPT_DIR"
  # 不加 clean：编译失败时至少保留上一次能用的 JAR
  mvn package -DskipTests -q || { error "编译失败，JAR 未生成"; exit 1; }
  info "编译完成"
fi

# ---------- 初始化数据库（首次运行）----------
if command -v mysql &>/dev/null; then
  DB_EXISTS=$(mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS --ssl-mode=DISABLED -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='datanote';" 2>/dev/null)
  if [ "$DB_EXISTS" = "0" ] || [ -z "$DB_EXISTS" ]; then
    info "首次运行，初始化数据库..."
    mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS --ssl-mode=DISABLED -e "CREATE DATABASE IF NOT EXISTS datanote DEFAULT CHARACTER SET utf8mb4;" 2>/dev/null
    mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS --ssl-mode=DISABLED datanote < "$SCRIPT_DIR/sql/init-all.sql" 2>/dev/null
    info "数据库初始化完成"
  else
    info "数据库已存在（${DB_EXISTS} 张表），跳过初始化"
  fi
fi

# ---------- 启动 DataNote ----------
info "启动 DataNote..."
cd "$SCRIPT_DIR"
nohup java \
  -Xms512m -Xmx1g \
  -jar "$JAR_FILE" \
  --spring.datasource.url="jdbc:mysql://${DB_HOST}:${DB_PORT}/datanote?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=$DB_USER \
  --spring.datasource.password=$DB_PASS \
  --datanote.crypto.key=${CRYPTO_KEY:?请在 datanote.conf 中设置 CRYPTO_KEY} \
  --datax.mode=${DATAX_MODE:-local} \
  --spring.web.resources.static-locations=file:${SCRIPT_DIR}/src/main/resources/static/,classpath:/static/ \
  --server.port=${DATANOTE_PORT:-8099} \
  > /tmp/datanote.log 2>&1 &

info "DataNote 启动中... (PID: $!)"

# 等待启动
STARTED=false
echo -n "等待服务就绪"
for i in $(seq 1 20); do
  if curl -sf http://localhost:${DATANOTE_PORT}/ &>/dev/null; then
    echo ""
    STARTED=true
    break
  fi
  sleep 2
  echo -n "."
done

# ==================== 完成 ====================
echo ""
if [ "$STARTED" = "true" ]; then
echo "============================================"
info "DataNote 启动成功！"
echo ""
echo "  访问地址：http://localhost:${DATANOTE_PORT}"
echo "  日志文件：/tmp/datanote.log"
echo "  配置文件：$CONF_FILE"
echo ""
echo "  下一步：打开浏览器 → 系统管理 → 数据源管理"
echo "  配置 Hive 连接信息，测试通过后保存即可。"
echo ""
echo "  停止：./setup-datanote.sh stop"
echo "============================================"
else
echo "============================================"
error "DataNote 启动超时！"
echo ""
echo "  查看日志：tail -50 /tmp/datanote.log"
echo "============================================"
exit 1
fi
