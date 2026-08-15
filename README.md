# Opah

通过 Web UI 完成运维工作的一站式工具：从 Git 仓库拉取代码，自动构建 Docker 镜像，一键部署与持续运维。

- 产品文档：[document/产品文档.md](document/产品文档.md)
- 技术架构：[document/技术架构设计.md](document/技术架构设计.md)
- 部署文档：[document/部署文档.md](document/部署文档.md)

## 仓库结构

```
opah/
├── server/            # Spring Boot 后端（REST API + SSH + Docker）
├── web/               # React 前端（Vite + Ant Design）
├── document/          # 产品、架构与部署文档
├── deploy.sh          # 一键部署脚本（Linux 服务器单文件）
└── docker-compose.yml # server 编排（Linux 部署用）
```

## 运行方式（双平台）

镜像分发不依赖 Registry：本机构建后 `docker save` 导出，经 SSH 管道直达目标主机 `docker load`。

### Windows 本机运行（主用）

前置条件：JDK 17+、Maven、Docker Desktop（Linux 容器模式，保持启动）。

```bash
cd server
mvn spring-boot:run          # 默认 http://localhost:8787，数据在 server/data
```

- Docker 连接默认走 named pipe（`npipe:////./pipe/docker_engine`），可用 `DOCKER_HOST` 覆盖
- 登录后验证 Docker 连通：`curl -b cookies.txt http://localhost:8787/api/v1/system/docker`

### Linux 服务器部署（Compose）

详见[部署文档](document/部署文档.md)。

### 前端开发（Node 18+）

```bash
cd web
npm install
npm run dev                  # http://localhost:5173，/api 代理到 8787
```

默认管理员：`admin / opah-admin`（可用环境变量 `OPAH_ADMIN_PASSWORD` 覆盖）。

## Linux 一键部署（推荐）

在任意 Linux 机器上（需已安装 git、Docker 及 Compose v2），单条命令完成部署：

```bash
curl -fsSL https://raw.githubusercontent.com/minzhigong/Opah/main/deploy.sh -o deploy.sh
chmod +x deploy.sh && ./deploy.sh
```

脚本自动完成：拉取代码到 `/opt/opah` -> 生成随机密钥与管理员密码（写入 `.env`）-> 构建前端 -> Compose 构建启动 -> 健康检查，结束后打印访问地址和账号信息。

常用用法：

```bash
./deploy.sh                          # 部署 / 升级（重跑即升级，.env 保持不变）
./deploy.sh --with-nginx             # 同时安装 Nginx 配置：静态前端 + /api 反代
REPO_URL=git@... BRANCH=dev ./deploy.sh   # 覆盖默认仓库/分支
OPAH_PORT=9000 ./deploy.sh           # 自定义端口（默认 8787）
```

### 手动 Compose 部署

```bash
docker compose up -d --build
```

环境变量（通过 `.env` 或 shell 传入）：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `OPAH_SECRET_KEY` | 凭据加密主密钥（生产必改） | change-me-in-production |
| `OPAH_ADMIN_PASSWORD` | 初始管理员密码 | opah-admin |
| `OPAH_PORT` | 对外端口 | 8787 |

## 开发状态

M1（骨架 + 登录 + 主机管理）开发中。里程碑规划见架构文档 §10。
