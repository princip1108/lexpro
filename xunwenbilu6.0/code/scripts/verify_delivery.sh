#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUN_DIR="${1:-output/runs/sample_9files_final}"
cd "${PROJECT_ROOT}"

python -m unittest discover -s code/tests -v
python -m compileall -q code
bash -n code/services/start_services.sh code/services/check_services.sh code/services/stop_services.sh \
  code/scripts/infer_file.sh code/scripts/infer_batch.sh code/scripts/smoke_test.sh code/scripts/install_env.sh
python code/scripts/audit_results.py "${RUN_DIR}" --expected-count 9

[[ "$(find data/sample_case -maxdepth 1 -type f | wc -l)" -eq 9 ]]
[[ "$(find docs -maxdepth 1 -type f | wc -l)" -eq 2 ]]
[[ "$(find model/LexPro_8B -type f | wc -l)" -eq 15 ]]
[[ "$(find model/MinerU2.5-2509-1.2B -type f | wc -l)" -eq 43 ]]
sha256sum -c model/checksums.sha256 >/dev/null

if grep -R -E '/mnt/cfair_dataset_2T_0|/home/softengine|xunwenbilu5\.0|xunwenbilu4\.0' \
  README.md code docs/*.md --exclude='server-pip-freeze.txt' --exclude='server-conda-explicit.txt' \
  --exclude='verify_delivery.sh' --exclude-dir='__pycache__'; then
  echo "发现不可迁移的旧绝对路径"
  exit 12
fi
if grep -R -E -i '训练|标注|评估|training|annotation|evaluation' README.md docs/*.md; then
  echo "部署文档包含范围外流程词"
  exit 13
fi

bash code/services/check_services.sh
echo "delivery_verification=pass"
