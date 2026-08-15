#!/usr/bin/env python3
from pathlib import Path
import base64
import gzip
import hashlib

HERE = Path(__file__).resolve().parent
EXPECTED_PATCH = "d7155b82b0ea7fe70df100f50ee22f964b7531e4577f0058bde1b97932d83bf5"
EXPECTED_COMPRESSED = "c73adcd8940ee8792c8ccf136058c740de3a4be237f6fa0a73b42625a1d8afcb"
parts = sorted(HERE.glob("payload_*.b64"))
if not parts:
    raise SystemExit("No payload parts found")
raw_b64 = "".join(p.read_text().strip() for p in parts)
compressed = base64.b64decode(raw_b64)
if hashlib.sha256(compressed).hexdigest() != EXPECTED_COMPRESSED:
    raise SystemExit("Compressed payload checksum mismatch")
patch = gzip.decompress(compressed)
if hashlib.sha256(patch).hexdigest() != EXPECTED_PATCH:
    raise SystemExit("Patch checksum mismatch")
out = HERE / "security_rebase.patch"
out.write_bytes(patch)
print(out)
