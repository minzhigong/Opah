CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL,
    created_at    TEXT NOT NULL
);

CREATE TABLE hosts (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT NOT NULL,
    ip             TEXT NOT NULL,
    ssh_port       INTEGER NOT NULL DEFAULT 22,
    username       TEXT NOT NULL,
    auth_type      TEXT NOT NULL DEFAULT 'PASSWORD',
    secret_cipher  TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'UNKNOWN',
    docker_version TEXT,
    os_info        TEXT,
    last_seen_at   TEXT,
    created_at     TEXT NOT NULL
);
