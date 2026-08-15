#!/usr/bin/env python3
from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

HERE = Path(__file__).resolve().parent
PARTS = sorted(HERE.glob("part_*.b64"))
EXPECTED_B64 = "b60ce70778f98c03b5b998e8421262abe14d49b0ed8c2ba56e329b6c0169286a"
EXPECTED_PATCH = "39a66f38d18604c4feccfbbdefd691f93a85545d232fa4c90c5c55079e7d27b8"

if not PARTS:
    raise SystemExit("No patch parts found")
encoded = "".join(p.read_text(encoding="utf-8").strip() for p in PARTS).encode()
if hashlib.sha256(encoded).hexdigest() != EXPECTED_B64:
    raise SystemExit("Base64 bundle checksum mismatch")
patch = gzip.decompress(base64.b64decode(encoded))
if hashlib.sha256(patch).hexdigest() != EXPECTED_PATCH:
    raise SystemExit("Patch checksum mismatch")
patch_file = HERE / "phase14_5_38.patch"
patch_file.write_bytes(patch)
subprocess.run(["git", "apply", "--check", str(patch_file)], check=True)
subprocess.run(["git", "apply", str(patch_file)], check=True)
print("Applied FUSH critical re-authentication 14.5.38")
