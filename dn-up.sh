#!/bin/bash
# ============================================================
# 一键启动 DataNote 全栈
# 顺序：按依赖启动 Docker 集群 → 等 MySQL/HiveServer2 就绪 → 启动应用
# 用法：./dn-up.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF_FILE="$SCRIPT_DIR/datanote.conf"
[ -f "$CONF_FILE" ] && source "$CONF_FILE"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info(){ echo -e "${GREEN}[INFO]${NC} $1"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $1"; }
error(){ echo -e "${RED}[ERROR]${NC} $1"; }

# ---------- 1) 按依赖顺序启动容器 ----------
ORDER=(datanote-mysql datanote-namenode datanote-datanode datanote-metastore datanote-hiveserver2 datanote-datax)
info "启动 Docker 集群（按依赖顺序）..."
for c in "${ORDER[@]}"; do
  if docker ps --format '{{.Names}}' | grep -qx "$c"; then
    info "  $c 已在运行"
  elif docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    docker start "$c" >/dev/null && info "  ▶ 启动 $c"
  else
    error "  容器 $c 不存在 —— 请先运行 ./setup-hive.sh 和 ./setup-datax.sh 创建集群"
    exit 1
  fi
done

# ---------- 2) 等 MySQL 就绪（应用启动的硬依赖）----------
echo -n "等待 MySQL 就绪"
for i in $(seq 1 30); do
  if docker exec datanote-mysql mysqladmin ping -uroot -p"${MYSQL_PASSWORD:?请在 datanote.conf 中设置 MYSQL_PASSWORD}" --silent &>/dev/null; then
    echo ""; info "MySQL 就绪"; break
  fi
  sleep 2; echo -n "."
  if [ "$i" = "30" ]; then echo ""; warn "MySQL 等待超时，仍尝试继续启动应用"; fi
done

# ---------- 3) 等 HiveServer2 就绪（跑作业才需要，非阻塞）----------
echo -n "等待 HiveServer2"
HS2_OK=false
for i in $(seq 1 20); do
  if docker exec datanote-hiveserver2 bash -c 'exec 3<>/dev/tcp/localhost/10000' &>/dev/null; then
    echo ""; info "HiveServer2 就绪"; HS2_OK=true; break
  fi
  sleep 3; echo -n "."
done
[ "$HS2_OK" = false ] && { echo ""; warn "HiveServer2 暂未就绪，应用照常启动；跑 DataX 作业前请确认它已 ready"; }

# ---------- 4) 启动应用（复用项目脚本：含建库/编译/就绪检测）----------
info "启动 DataNote 应用..."
"$SCRIPT_DIR/setup-datanote.sh"
