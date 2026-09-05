#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"
conda env create -f "${PROJECT_ROOT}/code/requirements/environment.yml"
echo "安装完成。请执行: conda activate LexPro"
