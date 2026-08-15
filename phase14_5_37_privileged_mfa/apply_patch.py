#!/usr/bin/env python3
from pathlib import Path
import base64
import gzip
import hashlib
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
EXPECTED_PATCH_SHA256 = "7154ecbac11935d34e1ec23f478948ebb10253aa56d47b0c8626dd0a684282d0"
EXPECTED_BUNDLE_SHA256 = "5b59c820199e273144067d199e1e23be27f134115bfc7778ff7f0d95fb8f3720"

bundle = "".join(p.read_text().strip() for p in sorted(HERE.glob("part_*.b64")))
if hashlib.sha256(bundle.encode()).hexdigest() != EXPECTED_BUNDLE_SHA256:
    raise SystemExit("Compressed MFA patch bundle checksum mismatch")
patch = gzip.decompress(base64.b64decode(bundle))
if hashlib.sha256(patch).hexdigest() != EXPECTED_PATCH_SHA256:
    raise SystemExit("MFA patch checksum mismatch")
with tempfile.NamedTemporaryFile(prefix="fush-14.5.37-", suffix=".patch", delete=False) as handle:
    handle.write(patch)
    patch_path = Path(handle.name)
subprocess.run(["git", "apply", "--check", str(patch_path)], check=True)
subprocess.run(["git", "apply", str(patch_path)], check=True)
patch_path.unlink(missing_ok=True)
print("Applied FUSH privileged MFA 14.5.37")
