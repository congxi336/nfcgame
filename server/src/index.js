/**
 * 服务入口：加载环境变量 → 读取证书 → 启动 HTTPS 服务。
 * 监听 2999 端口。
 */
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const https = require('https');

const app = require('./app');

const PORT = parseInt(process.env.PORT || '2999', 10);
const SSL_KEY_PATH = process.env.SSL_KEY_PATH || path.join(__dirname, '..', 'certs', 'key.pem');
const SSL_CERT_PATH = process.env.SSL_CERT_PATH || path.join(__dirname, '..', 'certs', 'cert.pem');

// 校验证书文件是否存在
if (!fs.existsSync(SSL_KEY_PATH) || !fs.existsSync(SSL_CERT_PATH)) {
  console.error('[FATAL] 未找到 SSL 证书，请先运行 scripts/generate-cert.sh 生成。');
  console.error(`        期望文件: ${SSL_KEY_PATH} 和 ${SSL_CERT_PATH}`);
  process.exit(1);
}

const options = {
  key: fs.readFileSync(SSL_KEY_PATH),
  cert: fs.readFileSync(SSL_CERT_PATH),
};

// 启动 HTTPS 服务
const server = https.createServer(options, app);
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[NFC-SERVER] HTTPS 服务已启动: https://0.0.0.0:${PORT}`);
  console.log(`[NFC-SERVER] 健康检查: https://<服务器IP>:${PORT}/health`);
});
