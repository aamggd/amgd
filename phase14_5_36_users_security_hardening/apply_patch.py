#!/usr/bin/env python3
from pathlib import Path
import hashlib
import subprocess

HERE = Path(__file__).resolve().parent
PATCH = HERE / "phase14_5_36.patch"
EXPECTED = "a17965137613db3c7e7a48ddcfae4c083e35e45088c9d367c2e421863a9bdef8"

raw = PATCH.read_bytes()
sha = hashlib.sha256(raw).hexdigest()
if sha != EXPECTED:
    raise SystemExit(f"Patch checksum mismatch: {sha}")
subprocess.run(["git", "apply", "--check", str(PATCH)], check=True)
subprocess.run(["git", "apply", str(PATCH)], check=True)
print("Applied FUSH users/security hardening 14.5.36")
