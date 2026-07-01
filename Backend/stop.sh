#!/bin/bash
# =============================================
# English Study Backend - 停止所有服务
# 用法: ./stop.sh
# =============================================

stopped=false

# 按端口杀后端（比 pkill 更可靠）
if fuser -k 8000/tcp 2>/dev/null; then
    echo "  ✅ 后端已停止（释放 8000 端口）"
    stopped=true
else
    echo "  ⚪ 后端未运行"
fi

# 杀 cloudflared
if pkill -f "cloudflared.*english_study" 2>/dev/null; then
    echo "  ✅ 隧道已停止"
    stopped=true
else
    # 回退：杀所有 cloudflared tunnel（兼容旧脚本）
    if pkill -f "cloudflared tunnel" 2>/dev/null; then
        echo "  ✅ 隧道已停止"
        stopped=true
    else
        echo "  ⚪ 隧道未运行"
    fi
fi

echo ""
if $stopped; then
    echo "全部服务已停止。"
else
    echo "没有运行中的服务。"
fi
