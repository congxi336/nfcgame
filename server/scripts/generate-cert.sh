#!/usr/bin/env bash
# ============================================================
# 生成自签名 HTTPS 证书（有效期 10 年）
# 用法：bash scripts/generate-cert.sh [服务器IP或域名]
# 默认 CN/SAN 使用传入的服务器 IP。
# ============================================================
set -euo pipefail

SERVER_IP="${1:-121.37.119.20}"
CERT_DIR="$(cd "$(dirname "$0")/../certs" && pwd)"

mkdir -p "$CERT_DIR"

echo "正在为 ${SERVER_IP} 生成自签名证书（10 年有效期）..."

# 生成私钥 + 证书（含 SAN，适配 Android 对自签名证书的主机名校验）
openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout "$CERT_DIR/key.pem" \
  -out "$CERT_DIR/cert.pem" \
  -days 3650 \
  -subj "/C=CN/ST=Guangdong/O=NFCGame/CN=${SERVER_IP}" \
  -addext "subjectAltName=IP:${SERVER_IP}"

echo ""
echo "证书生成完成："
echo "  私钥: $CERT_DIR/key.pem"
echo "  证书: $CERT_DIR/cert.pem"
echo ""
echo "提示：Android 端需要将 cert.pem 复制到 app 的资源目录中用于证书固定。"
