# Opah

通过 Web UI 完成运维工作的一站式工具：从 Git 仓库拉取代码，自动构建 Docker 镜像，一键部署与持续运维。

- 产品文档：[document/产品文档.md](document/产品文档.md)
- 技术架构：[document/技术架构设计.md](document/技术架构设计.md)

## 仓库结构

```
opah/
├── server/            # Spring Boot 后端（REST API + SSH + Docker）
├── web/               # React 前端（Vite + Ant Design）
├── document/          # 产品与架构文档
└── docker-compose.yml # server + registry 一键启动
```

## 本地开发

后端（Java 17+，构建时自动下载依赖）：

```bash
cd server
mvn spring-boot:run          # 默认 http://localhost:8787，数据在 ./data
```

前端（Node 18+）：

```bash
cd web
npm install
npm run dev                  # http://localhost:5173，/api 代理到 8787
```

默认管理员：`admin / opah-admin`（可用环境变量 `OPAH_ADMIN_PASSWORD` 覆盖）。

## Docker 部署

```bash
docker compose up -d --build
```

环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `OPAH_SECRET_KEY` | 凭据加密主密钥（生产必改） | change-me-in-production |
| `OPAH_ADMIN_PASSWORD` | 初始管理员密码 | opah-admin |

## 开发状态

M1（骨架 + 登录 + 主机管理）开发中。里程碑规划见架构文档 §10。
