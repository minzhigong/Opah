#!/bin/bash
# Opah GitHub Release 发布脚本
# 用法:
#   ./release.sh                # 用 VERSION 文件的版本；tag 已存在则更新资产，不存在则创建
#   ./release.sh 1.1-beta       # 指定版本（大阶段递增）
# 约定:
#   - 日常改代码 -> 跑 ./release.sh，覆盖最新版本同 tag 的 zip 资产
#   - 大阶段递增 -> 改 VERSION 文件(或传参)，打新 tag + 新 release
#   - 项目完成 -> VERSION 去掉 beta 后缀，脚本自动按稳定版发布(prerelease=false)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-$(tr -d '[:space:]' < VERSION)}"
TAG="v${VERSION}"
ZIP="packager/out/opah-windows.zip"
OWNER="minzhigong"
REPO="Opah"

# 含 beta/rc/alpha 视为预发布
case "$VERSION" in
  *beta*|*rc*|*alpha*) PRE=true ;;
  *) PRE=false ;;
esac

# 提取 GitHub token（wincred）
TOKEN=$(git -c credential.helper= -c credential.helper=wincred credential fill 2>/dev/null \
  <<< $'protocol=https\nhost=github.com\n\n' | grep -E '^password=' | sed 's/password=//')
[ -n "$TOKEN" ] || { echo "错误：GitHub token 提取失败"; exit 1; }
AUTH="Authorization: token $TOKEN"

[ -f "$ZIP" ] || { echo "错误：$ZIP 不存在，请先运行 build.ps1 打包"; exit 1; }

echo "=== 发布 Opah $VERSION (tag: $TAG, prerelease: $PRE) ==="

EXISTING=$(curl -s -H "$AUTH" "https://api.github.com/repos/$OWNER/$REPO/releases/tags/$TAG")
RELEASE_ID=$(echo "$EXISTING" | grep -oE '"id": [0-9]+' | head -1 | grep -oE '[0-9]+' || true)
UPLOAD_URL=$(echo "$EXISTING" | grep -oE '"upload_url": "[^"]+"' | sed 's/"upload_url": "//;s/"//;s/{?name,label}//' || true)

if [ -n "$RELEASE_ID" ]; then
  echo "release 已存在 (id=$RELEASE_ID)，清理旧 zip 资产..."
  ASSETS=$(curl -s -H "$AUTH" "https://api.github.com/repos/$OWNER/$REPO/releases/$RELEASE_ID/assets")
  for aid in $(echo "$ASSETS" | python -c "import json,sys; [print(a['id']) for a in json.load(sys.stdin)]" 2>/dev/null); do
    curl -s -X DELETE -H "$AUTH" "https://api.github.com/repos/$OWNER/$REPO/releases/assets/$aid" > /dev/null
    echo "  已删除资产 $aid"
  done
else
  echo "创建新 release..."
  BODY="{\"tag_name\":\"$TAG\",\"name\":\"Opah $VERSION\",\"body\":\"Opah 绿色包 $VERSION\",\"prerelease\":$PRE}"
  CREATE=$(curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" -d "$BODY" \
    "https://api.github.com/repos/$OWNER/$REPO/releases")
  RELEASE_ID=$(echo "$CREATE" | grep -oE '"id": [0-9]+' | head -1 | grep -oE '[0-9]+' || true)
  UPLOAD_URL=$(echo "$CREATE" | grep -oE '"upload_url": "[^"]+"' | sed 's/"upload_url": "//;s/"//;s/{?name,label}//' || true)
  if [ -z "$RELEASE_ID" ]; then
    echo "创建失败：$CREATE"; exit 1
  fi
  echo "  已创建 release id=$RELEASE_ID"
  git tag -f "$TAG" && git -c credential.helper= -c credential.helper=wincred push -f origin "$TAG"
  echo "  已打 tag $TAG"
fi

[ -n "$RELEASE_ID" ] || { echo "错误：release id 获取失败"; exit 1; }
[ -n "$UPLOAD_URL" ] || { echo "错误：upload_url 获取失败"; exit 1; }

echo "上传 opah-windows.zip ($(du -h "$ZIP" | cut -f1))..."
curl -sf -X POST -H "$AUTH" -H "Content-Type: application/zip" \
  --data-binary "@$ZIP" "$UPLOAD_URL?name=opah-windows.zip" > /dev/null \
  || { echo "上传失败"; exit 1; }
echo "DONE: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
exit 0
