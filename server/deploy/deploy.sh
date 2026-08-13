#!/usr/bin/env bash
# ============================================================
# 一键部署脚本：上传源码 → 服务器安装依赖 → 生成证书 → 启动 systemd 服务
# 用法：bash deploy/deploy.sh
# 可选环境变量：
#   SERVER_IP     服务器 IP（默认 121.37.119.20）
#   SERVER_USER   登录用户（默认 root）
#   REMOTE_DIR    远程部署目录（默认 /opt/nfc-game/server）
#   SERVER_PASS   服务器密码（可选，填写后配合 sshpass 免交互）
# ============================================================
set -euo pipefail

SERVER_IP="${SERVER_IP:-121.37.119.20}"
SERVER_USER="${SERVER_USER:-root}"
REMOTE_DIR="${REMOTE_DIR:-/opt/nfc-game/server}"
SERVER_PASS="${SERVER_PASS:-}"

cd "$(dirname "$0")/.."

# 选择传输方式：优先 sshpass，否则交互式
if command -v sshpass >/dev/null 2>&1 && [ -n "$SERVER_PASS" ]; then
  SSH_CMD="sshpass -p '$SERVER_PASS' ssh -o StrictHostKeyChecking=no"
  SCP_CMD="sshpass -p '$SERVER_PASS' scp -o StrictHostKeyChecking=no"
else
  echo "[提示] 未检测到 sshpass 或未设置 SERVER_PASS，使用交互式传输（需手动输密码）。"
  SSH_CMD="ssh -o StrictHostKeyChecking=no"
  SCP_CMD="scp -o StrictHostKeyChecking=no"
fi

echo "=== 1/6 创建远程目录 ==="
$SSH_CMD ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}/certs ${REMOTE_DIR}/data"

echo "=== 2/6 上传源码 ==="
$SCP_CMD -r package.json package-lock.json .env.example src scripts Dockerfile ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

echo "=== 3/6 上传 systemd 服务文件 ==="
$SCP_CMD deploy/nfc-server.service ${SERVER_USER}@${SERVER_IP}:/etc/systemd/system/nfc-server.service

echo "=== 4/6 生成自签名证书与 .env ==="
$SSH_CMD ${SERVER_USER}@${SERVER_IP} "cd ${REMOTE_DIR} && bash scripts/generate-cert.sh ${SERVER_IP} && cp .env.example .env"

echo "=== 5/6 安装依赖 ==="
$SSH_CMD ${SERVER_USER}@${SERVER_IP} "cd ${REMOTE_DIR} && (command -v node >/dev/null 2>&1 || (curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt-get install -y nodejs)) && npm install --production"

echo "=== 6/6 配置并启动 systemd 服务 ==="
# 将服务文件中的占位路径替换为实际部署路径
$SSH_CMD ${SERVER_USER}@${SERVER_IP} "sed -i 's#/opt/nfc-game/server#${REMOTE_DIR}#g' /etc/systemd/system/nfc-server.service && systemctl daemon-reload && systemctl enable --now nfc-server"

echo ""
echo "部署完成。验证命令："
echo "  ssh ${SERVER_USER}@${SERVER_IP} 'curl -k https://127.0.0.1:2999/health'"
echo "  ssh ${SERVER_USER}@${SERVER_IP} 'systemctl status nfc-server'"
