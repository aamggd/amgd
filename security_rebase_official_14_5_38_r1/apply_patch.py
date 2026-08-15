#!/usr/bin/env python3
from pathlib import Path
import base64, gzip, hashlib, subprocess
HERE=Path(__file__).resolve().parent
EXPECTED_PATCH='d7155b82b0ea7fe70df100f50ee22f964b7531e4577f0058bde1b97932d83bf5'
raw_b64=''.join(p.read_text().strip() for p in sorted(HERE.glob('part_*.b64')))
patch=gzip.decompress(base64.b64decode(raw_b64))
actual=hashlib.sha256(patch).hexdigest()
if actual != EXPECTED_PATCH:
    raise SystemExit(f'Patch checksum mismatch: {actual}')
patch_file=HERE/'security_rebase.patch'
patch_file.write_bytes(patch)
subprocess.run(['git','apply','--check',str(patch_file)],check=True)
subprocess.run(['git','apply',str(patch_file)],check=True)
print('Applied FUSH security rebase on official Phase 14.5.38 Professional UI')
