/**
 * Express 应用组装：中间件、频率限制、路由、错误处理。
 */
const path = require('path');
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const infoRouter = require('./routes/info');
const uploadRouter = require('./routes/upload');
const { fail } = require('./middleware/response');

const app = express();

// 基础安全头
app.use(helmet());

// 禁止缓存（隐藏信息不应被缓存）
app.use((req, res, next) => {
  res.set('Cache-Control', 'no-store');
  next();
});

// 解析 JSON 请求体
app.use(express.json({ limit: '100kb' }));

// 全局限流：每 IP 每分钟最多 60 次
const limiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: fail(429, '请求过于频繁，请稍后再试'),
});
app.use('/api', limiter);

// 业务路由
app.use('/api/info', infoRouter);
app.use('/api/upload', uploadRouter);

// 上传图片的静态访问（相对路径 /uploads/xxx.png）
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads')));

// 健康检查（方便部署后验证服务在线）
app.get('/health', (req, res) => {
  res.json({ code: 200, status: 'ok' });
});

// 404 兜底
app.use((req, res) => {
  res.status(404).json(fail(404, '接口不存在'));
});

// 统一错误处理（不泄露堆栈）
app.use((err, req, res, next) => {
  console.error('[ERROR]', err);
  if (res.headersSent) return next(err);
  res.status(500).json(fail(500, '服务器内部错误'));
});

module.exports = app;
