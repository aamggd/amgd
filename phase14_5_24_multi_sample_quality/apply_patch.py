from pathlib import Path
import base64
import gzip
import subprocess

root = Path.cwd()
payload = (root / "../phase14_5_24_multi_sample_quality/patch_b64_gz.txt").resolve().read_text().strip()
patch_bytes = gzip.decompress(base64.b64decode(payload))
patch_path = root / "phase14_5_24.patch"
patch_path.write_bytes(patch_bytes)
subprocess.run(["patch", "-p1", "-i", str(patch_path)], check=True)
print("Phase 14.5.24 patch applied")
