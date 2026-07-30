#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

echo "[1/5] 检查工作区格式与本地密钥文件"
git diff --check -- . ':(exclude)agent/**'
git diff --cached --check -- . ':(exclude)agent/**'
git check-ignore -q kangban-server/.env
git check-ignore -q kangban-web/.env.local

echo "[2/5] 扫描当前版本与 Git 历史中的高风险密钥格式"
openai_prefix="s""k-"
google_prefix="AI""za"
aws_prefix="AK""IA"
key_word="KEY"
secret_pattern="${openai_prefix}[A-Za-z0-9_-]{20,}|${google_prefix}[A-Za-z0-9_-]{20,}|${aws_prefix}[A-Z0-9]{16}|-----BEGIN [A-Z ]*PRIVATE ${key_word}-----"
if git grep -Il -E "$secret_pattern" -- . ':(exclude)agent/**' >/dev/null; then
  echo "检测到疑似真实密钥格式，请先移除并轮换。" >&2
  exit 1
fi
history_hits="$(git log --all --format='%H' -G"$secret_pattern" -- . ':(exclude)agent/**')"
if [[ -n "$history_hits" ]]; then
  echo "Git 历史中检测到疑似真实密钥格式，请先清理历史并轮换。" >&2
  exit 1
fi

echo "[3/5] 前端测试"
(cd kangban-web && npm test)

echo "[4/5] 前端生产构建"
(cd kangban-web && npm run build)

echo "[5/5] 后端测试与打包"
(cd kangban-server && mvn --batch-mode test -Dspring.profiles.active=test)
(cd kangban-server && mvn --batch-mode package -DskipTests)

echo "发布候选版本检查通过。"
