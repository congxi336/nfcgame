/**
 * /api/info 路由：查询、新增/更新、删除卡片隐藏信息。
 */
const express = require('express');
const router = express.Router();

const { getInfo, upsertInfo, deleteInfo } = require('../db');
const { parseUid, isValidUrl } = require('../middleware/validate');
const { ok, fail } = require('../middleware/response');

/**
 * GET /api/info?uid=XXXX
 * 查询指定 UID 对应的信息。
 * 成功：{code:200, data:{title, content, image_url}}
 * 未找到：{code:404, message:"未找到该卡片对应的信息"}
 */
router.get('/', (req, res) => {
  const uid = parseUid(req.query.uid);
  if (!uid) {
    return res.status(400).json(fail(400, '参数 uid 缺失或格式非法（应为 4~16 位十六进制）'));
  }

  const row = getInfo(uid);
  if (!row) {
    return res.status(404).json(fail(404, '未找到该卡片对应的信息'));
  }

  // 仅返回约定字段，不泄露 created_at/updated_at
  res.json(ok({
    data: {
      title: row.title,
      content: row.content,
      image_url: row.image_url || '',
    },
  }));
});

/**
 * POST /api/info
 * 新增或更新一条信息。
 * 请求体：{uid, title, content, image_url?}
 * uid 已存在则更新，否则插入。
 */
router.post('/', (req, res) => {
  const body = req.body || {};

  const uid = parseUid(body.uid);
  if (!uid) {
    return res.status(400).json(fail(400, '参数 uid 缺失或格式非法'));
  }

  const title = typeof body.title === 'string' ? body.title.trim() : '';
  const content = typeof body.content === 'string' ? body.content.trim() : '';
  const image_url = body.image_url;

  if (!title || !content) {
    return res.status(400).json(fail(400, 'title 和 content 均为必填字段'));
  }
  if (!isValidUrl(image_url)) {
    return res.status(400).json(fail(400, 'image_url 必须为合法的 http/https 链接'));
  }

  upsertInfo({ uid, title, content, image_url: image_url || null });
  res.json(ok({ message: '保存成功' }));
});

/**
 * DELETE /api/info?uid=XXXX
 * 删除信息（用于管理）。
 */
router.delete('/', (req, res) => {
  const uid = parseUid(req.query.uid);
  if (!uid) {
    return res.status(400).json(fail(400, '参数 uid 缺失或格式非法'));
  }

  const deleted = deleteInfo(uid);
  if (!deleted) {
    return res.status(404).json(fail(404, '未找到该卡片对应的信息'));
  }

  res.json(ok({ message: '删除成功' }));
});

module.exports = router;
