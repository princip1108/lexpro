#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
export PYTHONPATH="${PROJECT_ROOT}/code${PYTHONPATH:+:${PYTHONPATH}}"
cd "${PROJECT_ROOT}"
python -m pipeline.cli --input-dir "$1" --recursive "${@:2}"

