from __future__ import annotations

import base64
import gzip
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
PAYLOAD = Path(__file__).with_name("phase14_5_6.diff.gz.b64")
PATCH = Path(__file__).with_name("phase14_5_6.diff")

PATCH.write_bytes(gzip.decompress(base64.b64decode(PAYLOAD.read_text(encoding="utf-8"))))
# The restored Android source is inside the outer GitHub repository.  Applying
# from PROJECT would make git discover the outer .git directory and target the
# wrong root, so explicitly prefix every patch path with the restored project.
subprocess.run(
    ["git", "apply", "--whitespace=nowarn", "--directory=FushERP_Mobile_Phase5", str(PATCH)],
    cwd=ROOT,
    check=True,
)
print("Phase 14.5.6 inventory warehouse separation patch applied successfully")
