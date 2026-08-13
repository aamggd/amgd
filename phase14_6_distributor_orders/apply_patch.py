from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
PATCH = Path(__file__).with_name("phase14_6.diff")

subprocess.run(
    ["patch", "-p1", "--forward", "--batch", "-i", str(PATCH)],
    cwd=PROJECT,
    check=True,
)
print("Phase 14.6 distributor orders patch applied successfully")
