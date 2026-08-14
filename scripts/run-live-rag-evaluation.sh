#!/usr/bin/env bash
set -euo pipefail

if [[ "${KANGBAN_RUN_LIVE_RAG:-}" != "true" ]]; then
  echo "请先设置 KANGBAN_RUN_LIVE_RAG=true"
  exit 2
fi

if [[ -z "${APP_AI_API_KEY:-}" && -z "${DASHSCOPE_API_KEY:-}" ]]; then
  echo "请通过 APP_AI_API_KEY 或 DASHSCOPE_API_KEY 注入 Qwen 密钥"
  exit 2
fi

cd "$(dirname "$0")/../kangban-server"
mvn -q -Dspring.profiles.active=test \
  -DargLine=-Djava.net.preferIPv4Stack=true \
  -Dtest=QwenLiveRagGoldenSetEvaluationTest test
