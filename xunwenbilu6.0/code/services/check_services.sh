#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
set -a; source "${PROJECT_ROOT}/code/config/deploy.env"; set +a
echo "=== GPU ==="; nvidia-smi --query-gpu=index,name,memory.used,memory.total --format=csv,noheader
echo "=== MinerU ==="; curl -fsS "http://${MINERU_HOST}:${MINERU_PORT}/health"; echo
echo "=== vLLM ==="; curl -fsS "http://${VLLM_HOST}:${VLLM_PORT}/v1/models"; echo

