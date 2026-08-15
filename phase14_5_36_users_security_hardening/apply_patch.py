#!/usr/bin/env python3
from pathlib import Path
import hashlib
import subprocess

HERE = Path(__file__).resolve().parent
PATCH = HERE / "phase14_5_36.patch"
EXPECTED = "90b459347c75403eaa1d24ef0dd6b086ebbd0bbc31ef4625b86999e2c0f12c46"

raw = PATCH.read_bytes()
sha = hashlib.sha256(raw).hexdigest()
if sha != EXPECTED:
    raise SystemExit(f"Patch checksum mismatch: {sha}")
subprocess.run(["git", "apply", "--check", str(PATCH)], check=True)
subprocess.run(["git", "apply", str(PATCH)], check=True)
print("Applied FUSH users/security hardening 14.5.36")
