-- V3: host 表加角色字段（deploy 部署目标机 / build 构建机）
ALTER TABLE host ADD COLUMN role TEXT NOT NULL DEFAULT 'deploy';
