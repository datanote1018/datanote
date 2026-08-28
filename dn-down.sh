#!/bin/bash
# ============================================================
# 一键关闭 DataNote 全栈
# 顺序：先停应用 → 再按反序停 Docker 集群（上层先停，底层后停）
# 用法：./dn-down.sh
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF_FILE="$SCRIPT_DIR/datanote.conf"
[ -f "$CONF_FILE" ] && source "$CONF_FILE"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info(){ echo -e "${GREEN}[INFO]${NC} $1"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $1"; }

# ---------- 1) 先停应用 ----------
info "停止 DataNote 应用..."
"$SCRIPT_DIR/setup-datanote.sh" stop || true

# ---------- 2) 反序停容器（datax→hiveserver2→metastore→datanode→namenode→mysql）----------
ORDER=(datanote-datax datanote-hiveserver2 datanote-metastore datanote-datanode datanote-namenode datanote-mysql)
info "停止 Docker 集群（按反序）..."
for c in "${ORDER[@]}"; do
  if docker ps --format '{{.Names}}' | grep -qx "$c"; then
    docker stop "$c" >/dev/null && info "  ■ 停止 $c"
  else
    info "  $c 未运行"
  fi
done

info "DataNote 全栈已关闭，资源已释放。"
