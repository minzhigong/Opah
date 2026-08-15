#!/usr/bin/env bash
# Opah 一键部署脚本（Linux，单文件）
#
# 该脚本可独立下载到任意机器直接运行，自动完成：
#   拉取 Git 代码 -> 生成密钥/.env -> 构建前端 -> Compose 构建启动 -> 健康检查
#
# 用法：
#   ./deploy.sh                       # 部署 / 升级
#   ./deploy.sh --with-nginx          # 同时安装 Nginx 前端配置
#   REPO_URL=git@... BRANCH=dev ./deploy.sh   # 覆盖默认仓库/分支
#
# 说明：未安装 Docker 时会通过 get.docker.com 自动安装（默认走 Aliyun 镜像源）；
#   可用 DOCKER_INSTALL_MIRROR=AzureChinaCloud 换源，或 =off 使用官方源
set -euo pipefail

# ===== 可配置项（可通过环境变量覆盖）=====
REPO_URL="${REPO_URL:-https://github.com/minzhigong/Opah.git}"
BRANCH="${BRANCH:-main}"
INSTALL_DIR="${INSTALL_DIR:-/opt/opah}"   # 代码拉取/部署目录
OPAH_PORT="${OPAH_PORT:-8787}"
# ========================================

WITH_NGINX=0
[[ "${1:-}" == "--with-nginx" ]] && WITH_NGINX=1

info()  { printf '\033[32m[INFO]\033[0m %s\n' "$*"; }
warn()  { printf '\033[33m[WARN]\033[0m %s\n' "$*"; }
error() { printf '\033[31m[ERROR]\033[0m %s\n' "$*"; exit 1; }

# ---------- 1. 环境检查 ----------
info "检查运行环境..."

command -v git >/dev/null 2>&1 || error "未安装 git，请先安装：apt/yum install git"

if ! command -v docker >/dev/null 2>&1; then
  DOCKER_MIRROR="${DOCKER_INSTALL_MIRROR:-Aliyun}"
  MIRROR_ARGS=()
  if [[ "$DOCKER_MIRROR" != "off" ]]; then
    MIRROR_ARGS=(--mirror "$DOCKER_MIRROR")
    info "未检测到 Docker，开始自动安装（镜像源：${DOCKER_MIRROR}）..."
  else
    info "未检测到 Docker，开始自动安装（官方源）..."
  fi
  curl -fsSL https://get.docker.com | sh -s -- "${MIRROR_ARGS[@]}" \
    || error "Docker 自动安装失败，可换源重试：DOCKER_INSTALL_MIRROR=AzureChinaCloud ./deploy.sh，或手动安装：https://docs.docker.com/engine/install/"
fi
docker info >/dev/null 2>&1 || error "Docker 守护进程不可用（权限不足或未启动）"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  command -v docker-compose >/dev/null 2>&1 || error "未安装 Docker Compose v2，请参考官方文档安装"
  COMPOSE="docker-compose"
fi

# ---------- 2. 拉取代码 ----------
if [[ -f docker-compose.yml && -d server && -d web ]]; then
  # 已在仓库内运行
  INSTALL_DIR=$(pwd)
  info "当前目录即仓库：${INSTALL_DIR}"
else
  info "拉取代码：${REPO_URL}（分支 ${BRANCH}）-> ${INSTALL_DIR}"
  if [[ -d ${INSTALL_DIR}/.git ]]; then
    git -C "$INSTALL_DIR" fetch --prune
    git -C "$INSTALL_DIR" checkout "$BRANCH"
    git -C "$INSTALL_DIR" reset --hard "origin/${BRANCH}"
  else
    mkdir -p "$INSTALL_DIR"
    git clone -b "$BRANCH" --depth 1 "$REPO_URL" "$INSTALL_DIR"
  fi
fi
cd "$INSTALL_DIR"

# ---------- 3. 初始化 .env ----------
if [[ ! -f .env ]]; then
  info "生成 .env ..."
  if command -v openssl >/dev/null 2>&1; then
    SECRET_KEY=$(openssl rand -hex 32)
  else
    SECRET_KEY=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')
  fi
  ADMIN_PASSWORD=$(head -c 12 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 12)
  cat > .env <<EOF
OPAH_SECRET_KEY=${SECRET_KEY}
OPAH_ADMIN_PASSWORD=${ADMIN_PASSWORD}
OPAH_PORT=${OPAH_PORT}
EOF
  chmod 600 .env
  info ".env 已生成，管理员密码：${ADMIN_PASSWORD}（请妥善保存，也可编辑 .env 后重新部署）"
else
  # 已有 .env 时补齐端口变量（保持密钥/密码不变）
  grep -q '^OPAH_PORT=' .env || echo "OPAH_PORT=${OPAH_PORT}" >> .env
  info "检测到已有 .env，沿用原配置（密钥/密码不变）。"
fi

# ---------- 4. 构建前端 ----------
if command -v node >/dev/null 2>&1; then
  info "构建前端..."
  (cd web && npm install --no-audit --no-fund && npm run build)
  info "前端产物：web/dist"
else
  warn "未检测到 Node.js，跳过前端构建（服务端构建在 Docker 内完成，不受影响）"
fi

# ---------- 5. 启动服务 ----------
info "构建并启动 opah-server + opah-registry ..."
$COMPOSE up -d --build

# ---------- 6. 健康检查 ----------
info "等待服务就绪（端口 ${OPAH_PORT}）..."
READY=0
for i in $(seq 1 30); do
  if curl -sf -o /dev/null "http://127.0.0.1:${OPAH_PORT}/" ; then
    READY=1
    break
  fi
  sleep 2
done

echo
if [[ $READY -eq 1 ]]; then
  info "部署完成！"
else
  warn "服务 60 秒内未响应，请查看日志：$COMPOSE logs -f opah-server"
fi

$COMPOSE ps
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
cat <<EOF

------------------------------------------------------------
 后端地址：  http://${SERVER_IP:-<服务器IP>}:${OPAH_PORT}
 管理员：    admin /（见 .env 中 OPAH_ADMIN_PASSWORD）
 代码目录：  ${INSTALL_DIR}
 前端：      web/dist（建议由 Nginx 提供并将 /api 反代到 ${OPAH_PORT}）
 数据备份：  docker volume：opah-data、opah-registry
 升级：      重新运行本脚本即可（自动拉取最新代码并重建）
------------------------------------------------------------
EOF

# ---------- 7. 可选：安装 Nginx 配置 ----------
if [[ $WITH_NGINX -eq 1 ]]; then
  command -v nginx >/dev/null 2>&1 || error "未安装 Nginx，无法使用 --with-nginx"
  [[ -d web/dist ]] || error "web/dist 不存在，请先安装 Node.js（18+）后重试"
  NGINX_CONF=/etc/nginx/conf.d/opah.conf
  info "安装 Nginx 配置：${NGINX_CONF}"
  cat > "$NGINX_CONF" <<EOF
server {
    listen 80;
    server_name _;

    root ${INSTALL_DIR}/web/dist;
    index index.html;

    client_max_body_size 200m;

    location /api/ {
        proxy_pass http://127.0.0.1:${OPAH_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
  nginx -t && systemctl reload nginx
  info "Nginx 已配置并重载，访问 http://${SERVER_IP:-<服务器IP>}/ 即可使用"
fi
