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
# Use the system patch tool so paths are resolved from the restored Android
# project directory. `git apply` would otherwise discover the outer GitHub
# checkout and resolve app/... against the repository root instead.
subprocess.run(
    ["patch", "-p1", "--forward", "--batch", "-i", str(PATCH)],
    cwd=PROJECT,
    check=True,
)
print("Phase 14.5.5 warehouse transfer patch applied successfully")
