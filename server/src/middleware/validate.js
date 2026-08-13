/**
 * 参数校验中间件：规范化并校验 uid 格式。
 * uid 规则：4~16 位十六进制字符（覆盖 4 字节 MIFARE Classic、7 字节 Ultralight/DESFire）。
 */
function validateUid(uid) {
  if (typeof uid !== 'string') return false;
  const normalized = uid.trim().toUpperCase();
  return /^[0-9A-F]{4,16}$/.test(normalized);
}

/**
 * 从 query 或 body 中提取并校验 uid。
 * 校验失败返回 null，调用方据此返回 400。
 */
function parseUid(raw) {
  if (typeof raw !== 'string') return null;
  const normalized = raw.trim().toUpperCase();
  return validateUid(normalized) ? normalized : null;
}

/**
 * 校验是否为合法 URL（可选字段 image_url）。
 */
function isValidUrl(value) {
  if (value === null || value === undefined || value === '') return true; // 空值允许
  if (typeof value !== 'string') return false;
  try {
    const u = new URL(value);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

module.exports = { validateUid, parseUid, isValidUrl };
