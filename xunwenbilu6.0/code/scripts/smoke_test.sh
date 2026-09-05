#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
bash "${PROJECT_ROOT}/code/services/check_services.sh"
bash "${PROJECT_ROOT}/code/scripts/infer_batch.sh" "${PROJECT_ROOT}/data/sample_case" --run-id sample_smoke --force

