from __future__ import annotations

import asyncio
import json
import os
import shutil
import tempfile
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

import uvicorn
from fastapi import FastAPI, File, HTTPException, UploadFile


PROJECT_ROOT = Path(os.environ["LEXPRO_PROJECT_ROOT"]).resolve()
MODEL_PATH = Path(os.environ["MINERU_MODEL_PATH"]).resolve()
SERVICE_ROOT = PROJECT_ROOT / "output" / "mineru_service"
TMP_ROOT = SERVICE_ROOT / "tmp"
TASK_ROOT = SERVICE_ROOT / "tasks"
CONFIG_PATH = SERVICE_ROOT / "home" / "mineru.json"
HOST = os.getenv("MINERU_HOST", "127.0.0.1")
PORT = int(os.getenv("MINERU_PORT", "13456"))
parse_lock = asyncio.Lock()


def prepare_runtime() -> None:
    for directory in (TMP_ROOT, TASK_ROOT, CONFIG_PATH.parent):
        directory.mkdir(parents=True, exist_ok=True)
    config = {
        "models-dir": {"vlm": str(MODEL_PATH)},
        "latex-delimiter-config": {
            "display": {"left": "$$", "right": "$$"},
            "inline": {"left": "$", "right": "$"},
        },
    }
    CONFIG_PATH.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")


@asynccontextmanager
async def lifespan(app: FastAPI):
    prepare_runtime()
    yield


app = FastAPI(title="LexPro MinerU Service", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok", "model_path": str(MODEL_PATH), "model_exists": MODEL_PATH.exists()}


@app.post("/parse")
async def parse_document(file: UploadFile = File(...)):
    task_id = uuid.uuid4().hex
    task_dir = TASK_ROOT / task_id
    work_dir = TMP_ROOT / task_id
    task_dir.mkdir(parents=True, exist_ok=True)
    work_dir.mkdir(parents=True, exist_ok=True)
    filename = Path(file.filename or "document").name
    source = task_dir / filename
    try:
        source.write_bytes(await file.read())
        command = [
            "mineru", "--path", str(source), "--output", str(work_dir),
            "--backend", "vlm-auto-engine", "--format", "json", "--lang", "ch",
        ]
        env = os.environ.copy()
        env["MINERU_MODEL_SOURCE"] = "local"
        env["HF_HUB_OFFLINE"] = "1"
        env["HOME"] = str(CONFIG_PATH.parent)
        env["MINERU_TOOLS_CONFIG_JSON"] = str(CONFIG_PATH)
        async with parse_lock:
            # vLLM uses Unix-domain sockets whose path is limited to 107 bytes on Linux.
            # The project root can be deeply nested, so only this auto-deleted IPC socket
            # uses the operating-system temporary directory. All task files and logs stay
            # under output/mineru_service/.
            with tempfile.TemporaryDirectory(prefix="lexpro_mu_") as socket_dir:
                env["TMPDIR"] = socket_dir
                process = await asyncio.create_subprocess_exec(
                    *command, env=env, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
                )
                stdout, stderr = await process.communicate()
        (task_dir / "mineru.stdout.log").write_bytes(stdout)
        (task_dir / "mineru.stderr.log").write_bytes(stderr)
        if process.returncode != 0:
            raise RuntimeError(f"MinerU 返回码 {process.returncode}: {stderr.decode(errors='replace')[-2000:]}")
        candidates = list(work_dir.rglob("*_content_list.json")) or list(work_dir.rglob("*.json"))
        if not candidates:
            raise RuntimeError("MinerU 未生成 JSON 结果")
        result_path = candidates[0]
        final_path = task_dir / result_path.name
        shutil.copy2(result_path, final_path)
        data = json.loads(final_path.read_text(encoding="utf-8"))
        return {"status": "success", "task_id": task_id, "data": data}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
