from __future__ import annotations

import base64
import gzip
import subprocess
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
project = repo / "FushERP_Mobile_Phase5"
parts_dir = Path(__file__).resolve().parent
patch_file = repo / "phase14_5_7_business_settings.diff"

if not project.is_dir():
    raise SystemExit("FushERP_Mobile_Phase5 source folder was not restored")

payload = "".join((parts_dir / f"v2_part_{i:02d}").read_text(encoding="utf-8").strip() for i in range(8))
raw = base64.b64decode(payload, validate=True)
patch_file.write_bytes(gzip.decompress(raw))

subprocess.run(["git", "apply", "--check", str(patch_file)], cwd=project, check=True)
subprocess.run(["git", "apply", str(patch_file)], cwd=project, check=True)
print("Phase 14.5.7 business-settings patch applied successfully")
