from __future__ import annotations

import base64
import gzip
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
PAYLOAD = Path(__file__).with_name("phase14_5_5.diff.gz.b64")
PATCH = Path(__file__).with_name("phase14_5_5.diff")

PATCH.write_bytes(gzip.decompress(base64.b64decode(PAYLOAD.read_text(encoding="utf-8"))))
subprocess.run(
    ["git", "apply", "--whitespace=nowarn", str(PATCH)],
    cwd=PROJECT,
    check=True,
)
print("Phase 14.5.5 warehouse transfer patch applied successfully")
