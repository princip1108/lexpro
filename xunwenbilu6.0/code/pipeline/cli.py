from __future__ import annotations

import argparse
import os
import sys
from datetime import datetime
from pathlib import Path

from .discovery import collect_documents, is_supported, portable_path
from .mineru_client import MinerUClient
from .orchestrator import PipelineProcessor
from .output_manager import RunOutput
from .vllm_client import VLLMClient


PROJECT_ROOT = Path(__file__).resolve().parents[2]


def load_env(path: Path) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="LexPro 多模态询问笔录实体识别")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--input-file", type=Path)
    group.add_argument("--input-dir", type=Path)
    parser.add_argument("--recursive", action="store_true", default=True)
    parser.add_argument("--run-id", default=None)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--batch-size", type=int, default=None)
    parser.add_argument("--mineru-api", default=None)
    parser.add_argument("--vllm-api", default=None)
    parser.add_argument("--config", type=Path, default=PROJECT_ROOT / "code" / "config" / "deploy.env")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    load_env(args.config)
    if args.input_file:
        path = args.input_file.resolve()
        if not is_supported(path):
            raise SystemExit(f"不支持或不存在的输入文件: {path}")
        paths, input_root = [path], path.parent
    else:
        input_root = args.input_dir.resolve()
        paths = collect_documents(input_root, recursive=args.recursive)
    if not paths:
        raise SystemExit("未发现支持的文档")
    run_id = args.run_id or datetime.now().strftime("%Y%m%d_%H%M%S")
    output = RunOutput(PROJECT_ROOT / "output", run_id)
    output.write_manifest({
        "run_id": run_id,
        "input_root": portable_path(input_root, PROJECT_ROOT),
        "files": [portable_path(path, input_root) for path in paths],
    })
    mineru = MinerUClient(args.mineru_api or os.getenv("MINERU_API_URL", "http://127.0.0.1:13456"))
    vllm = VLLMClient(
        args.vllm_api or os.getenv("VLLM_API_URL", "http://127.0.0.1:8001"),
        model_name=os.getenv("VLLM_SERVED_MODEL_NAME", "LexPro_8B"),
        batch_size=args.batch_size or int(os.getenv("INFERENCE_BATCH_SIZE", "128")),
    )
    results, failures = PipelineProcessor(mineru, vllm, output, input_root).process(
        paths, resume=args.resume, force=args.force
    )
    print(f"运行目录: {output.root}")
    print(f"成功: {len(results)}，失败: {len(failures)}")
    return 2 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
