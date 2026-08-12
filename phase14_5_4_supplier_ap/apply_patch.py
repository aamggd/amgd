from __future__ import annotations

import base64
import gzip
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
PAYLOAD = Path(__file__).with_name("phase14_5_4.diff.gz.b64")

raw = base64.b64decode(PAYLOAD.read_text(encoding="utf-8"))
patch = gzip.decompress(raw)
patch_path = Path(__file__).with_name("phase14_5_4.diff")
patch_path.write_bytes(patch)

subprocess.run(
    ["git", "apply", "--whitespace=nowarn", str(patch_path)],
    cwd=PROJECT,
    check=True,
)
print("Phase 14.5.4 supplier AP patch applied successfully")
