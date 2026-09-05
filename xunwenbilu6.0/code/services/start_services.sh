#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CONFIG_FILE="${LEXPRO_CONFIG:-${PROJECT_ROOT}/code/config/deploy.env}"

if [[ ! -f "${CONFIG_FILE}" ]]; then
  cp "${PROJECT_ROOT}/code/config/deploy.env.example" "${CONFIG_FILE}"
  echo "已生成 ${CONFIG_FILE}，请检查配置后重新运行。"
  exit 1
fi

set -a
source "${CONFIG_FILE}"
set +a

resolve_path() {
  local value="$1"
  if [[ "${value}" = /* ]]; then printf '%s\n' "${value}"; else printf '%s\n' "${PROJECT_ROOT}/${value#./}"; fi
}

export LEXPRO_PROJECT_ROOT="${PROJECT_ROOT}"
export MINERU_MODEL_PATH="$(resolve_path "${MINERU_MODEL_PATH}")"
export LEXPRO_MODEL_PATH="$(resolve_path "${LEXPRO_MODEL_PATH}")"
SERVICE_DIR="${PROJECT_ROOT}/output/services"
MINERU_HOME="${PROJECT_ROOT}/output/mineru_service/home"
mkdir -p "${SERVICE_DIR}" "${MINERU_HOME}"

[[ -d "${MINERU_MODEL_PATH}" ]] || { echo "MinerU 模型不存在: ${MINERU_MODEL_PATH}"; exit 1; }
[[ -f "${LEXPRO_MODEL_PATH}/config.json" ]] || { echo "LexPro 模型不完整: ${LEXPRO_MODEL_PATH}"; exit 1; }

if curl -fsS "http://${MINERU_HOST}:${MINERU_PORT}/health" >/dev/null 2>&1; then
  echo "MinerU 已在运行。"
else
  HOME="${MINERU_HOME}" CUDA_VISIBLE_DEVICES="${MINERU_GPU}" nohup python "${PROJECT_ROOT}/code/services/mineru_server.py" \
    >"${SERVICE_DIR}/mineru.log" 2>&1 &
  echo $! >"${SERVICE_DIR}/mineru.pid"
fi

if curl -fsS "http://${VLLM_HOST}:${VLLM_PORT}/v1/models" >/dev/null 2>&1; then
  echo "vLLM 已在运行。"
else
  CUDA_VISIBLE_DEVICES="${VLLM_GPU}" nohup python -m vllm.entrypoints.openai.api_server \
    --model "${LEXPRO_MODEL_PATH}" --served-model-name "${VLLM_SERVED_MODEL_NAME}" \
    --host "${VLLM_HOST}" --port "${VLLM_PORT}" --dtype auto \
    --max-model-len "${VLLM_MAX_MODEL_LEN}" --max-num-seqs "${VLLM_MAX_NUM_SEQS}" \
    --gpu-memory-utilization "${VLLM_GPU_MEMORY_UTILIZATION}" \
    >"${SERVICE_DIR}/vllm.log" 2>&1 &
  echo $! >"${SERVICE_DIR}/vllm.pid"
fi

wait_for_url() {
  local name="$1" url="$2" log="$3"
  for _ in $(seq 1 180); do
    if curl -fsS "${url}" >/dev/null 2>&1; then echo "${name} 就绪: ${url}"; return 0; fi
    sleep 2
  done
  echo "${name} 启动超时。日志: ${log}"
  tail -n 80 "${log}" || true
  return 1
}

wait_for_url "MinerU" "http://${MINERU_HOST}:${MINERU_PORT}/health" "${SERVICE_DIR}/mineru.log"
wait_for_url "vLLM" "http://${VLLM_HOST}:${VLLM_PORT}/v1/models" "${SERVICE_DIR}/vllm.log"

