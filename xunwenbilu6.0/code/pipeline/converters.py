from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path


class LibreOfficeConverter:
    def __init__(self, output_dir: Path, executable: str | None = None) -> None:
        self.output_dir = Path(output_dir)
        self.executable = executable or shutil.which("libreoffice") or shutil.which("soffice") or "libreoffice"

    def convert(self, source: Path, document_id: str) -> Path:
        target_dir = self.output_dir / document_id
        target_dir.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="lo_profile_", dir=target_dir) as profile_dir:
            profile_uri = Path(profile_dir).resolve().as_uri()
            completed = subprocess.run(
                [
                    self.executable,
                    f"-env:UserInstallation={profile_uri}",
                    "--headless",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    str(target_dir),
                    str(source),
                ],
                capture_output=True,
                text=True,
                timeout=600,
                check=False,
            )
        expected = target_dir / f"{source.stem}.pdf"
        if completed.returncode != 0 or not expected.exists():
            raise RuntimeError(f"Word 转 PDF 失败: {completed.stderr or completed.stdout}")
        return expected
