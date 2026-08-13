/**
 * 统一响应结构辅助函数。
 */
function ok(data) {
  return { code: 200, ...data };
}

function fail(code, message) {
  return { code, message };
}

module.exports = { ok, fail };
