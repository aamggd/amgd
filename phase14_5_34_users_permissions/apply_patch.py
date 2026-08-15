#!/usr/bin/env python3
"""Apply FUSH ERP Phase 14.5.34 Users & Permissions patch to a clean 14.5.33 source tree."""
from __future__ import annotations
import base64
import gzip
import hashlib
from pathlib import Path
import subprocess
import tempfile

EXPECTED_SHA256 = "94bc1ae1ef5fe91d0537b94bcaa8cca5b38b94e69a1cd719419e608469f5ce93"
HERE = Path(__file__).resolve().parent
PARTS = sorted(HERE.glob("patch_*.b64"))

if not PARTS:
    raise SystemExit("Patch chunks were not found next to apply_patch.py")

encoded = "".join(p.read_text(encoding="utf-8").strip() for p in PARTS)
patch = gzip.decompress(base64.b64decode(encoded))
actual = hashlib.sha256(patch).hexdigest()
if actual != EXPECTED_SHA256:
    raise SystemExit(f"Patch integrity check failed: {actual}")

with tempfile.NamedTemporaryFile(suffix=".patch", delete=False) as tmp:
    tmp.write(patch)
    patch_path = Path(tmp.name)

try:
    subprocess.run(["git", "apply", "--check", str(patch_path)], check=True)
    subprocess.run(["git", "apply", str(patch_path)], check=True)
finally:
    patch_path.unlink(missing_ok=True)

print("Applied FUSH ERP Phase 14.5.34 Users & Permissions successfully.")
