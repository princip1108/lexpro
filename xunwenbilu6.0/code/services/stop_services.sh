#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SERVICE_DIR="${PROJECT_ROOT}/output/services"

stop_one() {
  local name="$1" pid_file="$2" expected="$3"
  [[ -f "${pid_file}" ]] || { echo "${name}: 无 PID 文件"; return; }
  local pid; pid="$(cat "${pid_file}")"
  if kill -0 "${pid}" 2>/dev/null; then
    local command; command="$(ps -p "${pid}" -o args= || true)"
    [[ "${command}" == *"${expected}"* ]] || { echo "${name}: PID 命令不匹配，拒绝停止"; return 1; }
    kill "${pid}"
    for _ in $(seq 1 30); do kill -0 "${pid}" 2>/dev/null || break; sleep 1; done
    kill -0 "${pid}" 2>/dev/null && kill -KILL "${pid}"
  fi
  rm -f "${pid_file}"
  echo "${name}: 已停止"
}

stop_one "MinerU" "${SERVICE_DIR}/mineru.pid" "mineru_server.py"
stop_one "vLLM" "${SERVICE_DIR}/vllm.pid" "vllm.entrypoints.openai.api_server"

