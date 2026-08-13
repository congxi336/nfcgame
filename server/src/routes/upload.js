/**
 * /api/upload 路由：接收图片上传，保存到 uploads 目录，返回可访问的相对路径。
 * 使用 multer 处理 multipart/form-data，限制类型与大小。
 */
const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const router = express.Router();

// uploads 目录（server/uploads）
const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads');
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// 允许的图片类型（mimetype -> 扩展名）
const ALLOWED_TYPES = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
};

// 存储配置：随机文件名，避免冲突与路径注入
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => {
    const ext = ALLOWED_TYPES[file.mimetype] || path.extname(file.originalname).toLowerCase();
    const name = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;
    cb(null, name);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5MB
  fileFilter: (req, file, cb) => {
    if (ALLOWED_TYPES[file.mimetype]) {
      cb(null, true);
    } else {
      cb(new Error('仅支持 jpg / png / gif / webp 格式的图片'));
    }
  },
});

/**
 * POST /api/upload
 * 表单字段名：file
 * 成功：{"code":200,"data":{"url":"/uploads/xxxx.png"}}
 */
router.post('/', (req, res) => {
  upload.single('file')(req, res, (err) => {
    if (err) {
      const message = err.code === 'LIMIT_FILE_SIZE'
        ? '图片大小不能超过 5MB'
        : (err.message || '上传失败');
      return res.status(400).json({ code: 400, message });
    }
    if (!req.file) {
      return res.status(400).json({ code: 400, message: '未接收到图片文件' });
    }
    const url = `/uploads/${req.file.filename}`;
    res.json({ code: 200, data: { url } });
  });
});

module.exports = router;
