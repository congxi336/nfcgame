-- ============================================================
-- NFC 隐藏信息系统 - 数据库初始化 SQL（等价迁移脚本）
-- 兼容 SQLite（默认）与 PostgreSQL（注释见文末说明）
-- ============================================================

-- 建表
CREATE TABLE IF NOT EXISTS cards (
  uid        TEXT PRIMARY KEY,                                  -- 卡片十六进制 UID（大写）
  title      TEXT NOT NULL DEFAULT '',                          -- 标题
  content    TEXT NOT NULL DEFAULT '',                          -- 内容文字
  image_url  TEXT,                                              -- 图片链接（可选）
  created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), -- 创建时间
  updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))  -- 更新时间
);

-- 索引（查询通常按 uid 精确匹配，主键已覆盖；此处为可选示例）
-- CREATE INDEX idx_cards_updated ON cards(updated_at);

-- ============================================================
-- 示例数据（可自行删除）
-- ============================================================
-- INSERT INTO cards (uid, title, content, image_url)
-- VALUES ('04A1B2C3D4E5F6', '示例卡片', '这是一条示例隐藏信息。', NULL);

-- ============================================================
-- PostgreSQL 兼容说明：
--   将 datetime('now','localtime') 替换为 CURRENT_TIMESTAMP：
--   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
--   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
-- 并在 upsert 时使用 ON CONFLICT (uid) DO UPDATE。
-- ============================================================
