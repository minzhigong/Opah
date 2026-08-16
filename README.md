# Opah

一站式运维部署工具：运行在你的 Windows/Linux 机器上，自动拉取 Git 代码、构建 Docker 镜像，把服务一键部署到远程 Linux 服务器，全程在 Web UI 完成，无需手工 SSH。

> 一期（MVP）聚焦：Java 后端 + React/Vue 前端的构建与部署闭环，Windows 绿色自包含包交付。

## 架构概览

```
Git 仓库 ──拉取──> Opah（用户本机，Windows）
                    │ docker build（本机 Docker，可走代理拉基础镜像）
                    ▼
                 版本化镜像
                    │ docker save | ssh docker load（流式，无 Registry）
                    ▼
              Linux 目标主机 ──容器运行──> Java / React / Vue 服务
```

- 构建在本机执行（复用宿主 Docker daemon）；镜像经 SSH 管道分发，目标主机零安装（仅需 SSH + Docker，无 Agent）。
- 元数据存 SQLite（单文件，`./data` 便携）；凭据 AES-256-GCM 加密。
- 详见 `document/产品文档.md` 与 `document/技术架构设计.md`。

## 目录结构

```
opah/
├── server/        Spring Boot 3.3 后端（Java 21）
├── web/           React 18 + AntD 5 前端（Vite）
├── packager/      Windows 绿色包打包脚本
├── document/      产品与架构文档
```

## 本地开发

后端（Java 21 + Maven）：

```bash
cd server
JAVA_HOME=<jdk21> mvn spring-boot:run     # 默认 127.0.0.1:8787
```

前端（Node 22）：

```bash
cd web
npm install
npm run dev                                # Vite 代理 /api 与 /ws 到 8787
```

首次打开 `http://127.0.0.1:5173` 会进入管理员设置向导。

## 一键打包（Windows 绿色包）

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
powershell -File packager/build.ps1
# 产物：packager/out/opah-windows.zip（解压双击 opah.exe 即用）
```

## 使用流程

1. 解压 zip，双击 `opah.exe`（自动打开浏览器，默认 `http://127.0.0.1:8787`）；
2. 首次设置管理员密码；
3. 添加目标主机（SSH 连接信息，自动检测 Docker）；
4. 接入 Git 项目 → 扫描部署单元 → 勾选确认；
5. 触发构建 → 选择版本 + 主机 → 一键部署 / 回滚。

## 依赖版本

| 组件 | 版本 |
| --- | --- |
| Java | 21 (LTS) |
| Spring Boot | 3.3.x |
| Docker client | docker-java 3.5.x |
| Git | JGit 6.10 |
| SSH | Apache MINA sshd 2.13 |
| 存储 | SQLite + Flyway |
