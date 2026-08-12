from __future__ import annotations

import base64
import gzip
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
PAYLOAD = Path(__file__).with_name("phase14_5_3.diff.gz.b64")

raw = base64.b64decode(PAYLOAD.read_text(encoding="utf-8"))
patch = gzip.decompress(raw)
patch_path = Path(__file__).with_name("phase14_5_3.diff")
patch_path.write_bytes(patch)

# The checked-out repository itself is a Git repository. Apply the patch under
# the restored Android project directory explicitly so app/... paths resolve
# inside FushERP_Mobile_Phase5 rather than at repository root.
subprocess.run(
    [
        "git",
        "apply",
        "--whitespace=nowarn",
        "--directory=FushERP_Mobile_Phase5",
        str(patch_path),
    ],
    cwd=ROOT,
    check=True,
)
print("Phase 14.5.3 historical report patch applied successfully")
