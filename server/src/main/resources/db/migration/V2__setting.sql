-- V2: 全局设置 KV 表（自定义 Docker host 等）
CREATE TABLE IF NOT EXISTS setting (
    key        TEXT PRIMARY KEY,
    value      TEXT,
    updated_at TEXT
);
