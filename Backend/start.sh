#!/bin/bash
# =============================================
# English Study Backend - 启动服务（后台运行，断开SSH不中断）
# 用法: ./start.sh
# =============================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_BACKEND="/tmp/english_study_backend.log"
LOG_TUNNEL="/tmp/cloudflared_english_study.log"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# 检测 Python
PY_CMD=""
if command -v python3 &> /dev/null; then
    PY_CMD="python3"
elif command -v python &> /dev/null; then
    PY_CMD="python"
else
    echo -e "${RED}未找到 Python${NC}"
    exit 1
fi

# 先停止旧进程（按端口杀，比 pkill 更可靠）
echo "检查旧进程..."
fuser -k 8000/tcp 2>/dev/null && echo "  已停止占用 8000 端口的旧进程" || true
pkill -f "cloudflared.*english_study" 2>/dev/null && echo "  已停止旧隧道" || true
sleep 1

# 再次确认端口释放
if fuser 8000/tcp &>/dev/null; then
    echo -e "${RED}❌ 8000 端口仍被占用，请手动停止: fuser -k 8000/tcp${NC}"
    exit 1
fi

# 1. 启动后端
echo "启动后端服务..."
cd "$SCRIPT_DIR"
nohup $PY_CMD -m app.main > "$LOG_BACKEND" 2>&1 &
BACKEND_PID=$!

for i in $(seq 1 15); do
    if curl -s http://localhost:8000/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 后端启动成功 (PID: $BACKEND_PID)${NC}"
        break
    fi
    if [ $i -eq 15 ]; then
        echo -e "${RED}❌ 后端启动超时${NC}"
        echo "最后 20 行日志:"
        tail -20 "$LOG_BACKEND"
        exit 1
    fi
    sleep 1
done

# 2. 启动 Cloudflare 隧道
echo "启动 Cloudflare 隧道（国内网络可能较慢）..."
nohup cloudflared tunnel --url http://localhost:8000 > "$LOG_TUNNEL" 2>&1 &
TUNNEL_PID=$!

for i in $(seq 1 30); do
    TUNNEL_URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' "$LOG_TUNNEL" 2>/dev/null | head -1)
    if [ -n "$TUNNEL_URL" ]; then
        break
    fi
    sleep 1
done

echo ""
echo "========================================="
echo -e "  ${GREEN}✅ 启动成功！${NC}"
echo "========================================="
echo ""
if [ -n "$TUNNEL_URL" ]; then
    echo "  🌐 访问地址: $TUNNEL_URL"
else
    echo "  ⚠️  隧道建立超时，稍后查看: cat $LOG_TUNNEL | grep trycloudflare"
fi
echo ""
echo "  停止服务:     ./stop.sh"
echo "  后端日志:     tail -f $LOG_BACKEND"
echo "  隧道日志:     tail -f $LOG_TUNNEL"
echo ""
