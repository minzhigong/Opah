-- Opah V1 schema (SQLite)
-- 表结构对齐架构文档 §4.2

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'ADMIN',
    created_at    TEXT NOT NULL
);

-- Git 凭据（加密存储）
CREATE TABLE credential (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    type          TEXT NOT NULL,             -- USERNAME_TOKEN / SSH_KEY / PASSWORD
    secret_cipher TEXT NOT NULL,             -- AES-256-GCM 密文
    created_at    TEXT NOT NULL
);

-- Git 仓库（项目）
CREATE TABLE project (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT NOT NULL,
    git_url        TEXT NOT NULL,
    default_branch TEXT NOT NULL DEFAULT 'main',
    credential_id  INTEGER,
    created_at     TEXT NOT NULL,
    FOREIGN KEY (credential_id) REFERENCES credential(id)
);

-- 部署单元（PROJ-06：一个仓库可含多个单元）
CREATE TABLE service (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id   INTEGER NOT NULL,
    name         TEXT NOT NULL,
    type         TEXT NOT NULL,              -- JAVA / REACT / VUE / COMPOSE / CUSTOM
    sub_path     TEXT NOT NULL DEFAULT '.',
    build_config TEXT,                       -- JSON：JDK/Node 版本、构建命令覆盖
    nginx_config TEXT,                       -- JSON：路由模式/反代/gzip（前端单元）
    current_build_id INTEGER,                -- 当前运行版本（逻辑引用，无物理 FK）
    created_at   TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE UNIQUE INDEX idx_service_name ON service(project_id, name);

-- 目标主机
CREATE TABLE host (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT NOT NULL,
    ip                  TEXT NOT NULL,
    ssh_port            INTEGER NOT NULL DEFAULT 22,
    username            TEXT NOT NULL,
    auth_credential_id  INTEGER,             -- 引用 credential 表（PASSWORD/SSH_KEY）
    status              TEXT NOT NULL DEFAULT 'UNKNOWN',   -- ONLINE / OFFLINE / UNKNOWN
    docker_version      TEXT,
    os_info             TEXT,
    last_seen_at        TEXT,
    created_at          TEXT NOT NULL
);

-- 配置中心：环境变量
CREATE TABLE config_entry (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id    INTEGER NOT NULL,
    key           TEXT NOT NULL,
    value_cipher  TEXT NOT NULL,             -- 敏感项加密；非敏感明文 base64 区分
    is_sensitive  INTEGER NOT NULL DEFAULT 0,
    updated_at    TEXT NOT NULL,
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_config_entry ON config_entry(service_id, key);

-- 配置中心：配置文件
CREATE TABLE config_file (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id INTEGER NOT NULL,
    path       TEXT NOT NULL,                -- 容器内相对路径，如 application-prod.yml
    content    TEXT NOT NULL,                -- 加密原文（整体加密）
    updated_at TEXT NOT NULL,
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_config_file ON config_file(service_id, path);

-- 构建
CREATE TABLE build (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id   INTEGER NOT NULL,
    commit_sha   TEXT,
    commit_msg   TEXT,
    version_tag  TEXT NOT NULL,              -- {yyyyMMddHHmmss}-{commit短hash}
    status       TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    error_msg    TEXT,
    duration_ms  INTEGER,
    triggered_by TEXT,
    queued_at    TEXT NOT NULL,
    started_at   TEXT,
    finished_at  TEXT,
    log_excerpt TEXT,
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_build_version ON build(service_id, version_tag);
CREATE INDEX idx_build_status ON build(status);

-- 构建日志（逐行）
CREATE TABLE build_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    build_id   INTEGER NOT NULL,
    line_no    INTEGER NOT NULL,
    content    TEXT NOT NULL,
    is_stderr  INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    FOREIGN KEY (build_id) REFERENCES build(id) ON DELETE CASCADE
);

CREATE INDEX idx_build_log_build ON build_log(build_id, line_no);

-- 部署记录
CREATE TABLE deployment (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id      INTEGER NOT NULL,
    build_id        INTEGER NOT NULL,
    host_id         INTEGER NOT NULL,
    status          TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING/SUCCESS/FAILED/ROLLED_BACK
    config_snapshot TEXT NOT NULL,            -- JSON：端口/环境变量/挂载/配置文件内容
    started_by      TEXT,
    started_at      TEXT NOT NULL,
    finished_at     TEXT,
    error_msg       TEXT,
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE,
    FOREIGN KEY (build_id) REFERENCES build(id),
    FOREIGN KEY (host_id) REFERENCES host(id)
);

CREATE INDEX idx_deployment_service ON deployment(service_id, started_at DESC);

-- 运行时容器（定时同步覆盖）
CREATE TABLE runtime_container (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    deployment_id        INTEGER,
    host_id              INTEGER NOT NULL,
    docker_container_id  TEXT NOT NULL,
    name                 TEXT NOT NULL,
    status               TEXT NOT NULL,
    image_tag            TEXT,
    started_at           TEXT,
    updated_at           TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_runtime_container ON runtime_container(host_id, docker_container_id);

-- 审计日志
CREATE TABLE audit_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER,
    action     TEXT NOT NULL,                -- DEPLOY / ROLLBACK / BUILD / CONTAINER_ACTION ...
    target_type TEXT NOT NULL,
    target_id  TEXT,
    detail     TEXT,                          -- JSON
    ip         TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
