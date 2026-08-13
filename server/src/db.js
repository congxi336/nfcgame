/**
 * 数据库层：负责 SQLite 连接的初始化、建表，以及 cards 表的增删改查。
 * 使用 better-sqlite3（同步 API，简单可靠，适合本系统的数据规模）。
 */
const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

// 解析数据库文件路径（优先环境变量，默认 ./data/nfc.db）
const dbPath = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'nfc.db');

// 确保数据目录存在
const dataDir = path.dirname(dbPath);
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

// 初始化数据库连接
const db = new Database(dbPath);
db.pragma('journal_mode = WAL'); // 提升并发读写性能

/**
 * 建表语句：uid 为主键（大写十六进制字符串）。
 * created_at / updated_at 使用 SQLite 默认当前时间（本地时区）。
 */
const CREATE_TABLE_SQL = `
  CREATE TABLE IF NOT EXISTS cards (
    uid        TEXT PRIMARY KEY,
    title      TEXT NOT NULL DEFAULT '',
    content    TEXT NOT NULL DEFAULT '',
    image_url  TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
  );
`;

// 启动时建表
db.exec(CREATE_TABLE_SQL);

/**
 * 查询指定 UID 的信息。
 * @param {string} uid 大写十六进制 UID
 * @returns {object|null} 命中返回行对象，否则返回 null
 */
function getInfo(uid) {
  const row = db
    .prepare('SELECT uid, title, content, image_url, created_at, updated_at FROM cards WHERE uid = ?')
    .get(uid);
  return row || null;
}

/**
 * 新增或更新一条信息（uid 已存在则更新）。
 * @param {object} info { uid, title, content, image_url }
 * @returns {object} 操作后的行对象
 */
function upsertInfo({ uid, title, content, image_url }) {
  db.prepare(
    `INSERT INTO cards (uid, title, content, image_url)
     VALUES (@uid, @title, @content, @image_url)
     ON CONFLICT(uid) DO UPDATE SET
       title     = excluded.title,
       content   = excluded.content,
       image_url = excluded.image_url,
       updated_at = datetime('now','localtime')`
  ).run({ uid, title, content, image_url: image_url || null });

  return getInfo(uid);
}

/**
 * 删除指定 UID 的信息。
 * @param {string} uid
 * @returns {boolean} 是否真的删除了记录
 */
function deleteInfo(uid) {
  const result = db.prepare('DELETE FROM cards WHERE uid = ?').run(uid);
  return result.changes > 0;
}

module.exports = {
  db,
  getInfo,
  upsertInfo,
  deleteInfo,
};
