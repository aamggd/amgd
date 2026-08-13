from __future__ import annotations

import base64
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "FushERP_Mobile_Phase5"
HERE = Path(__file__).resolve().parent
CHUNKS = HERE / "patch_chunks"

chunk_paths = sorted(CHUNKS.glob("chunk_*.b64"))
if not chunk_paths:
    raise RuntimeError("Phase 14.5.7 patch chunks are missing")

# Each repository chunk is independently base64-encoded. Decode each one,
# then concatenate the raw patch bytes in filename order.
patch_bytes = b"".join(
    base64.b64decode(path.read_text(encoding="ascii").strip(), validate=True)
    for path in chunk_paths
)

with tempfile.NamedTemporaryFile(prefix="phase14_5_7_", suffix=".diff", delete=False) as handle:
    handle.write(patch_bytes)
    patch_path = Path(handle.name)

try:
    subprocess.run(
        ["patch", "-p1", "--forward", "--batch", "-i", str(patch_path)],
        cwd=PROJECT,
        check=True,
    )
finally:
    patch_path.unlink(missing_ok=True)

print("Phase 14.5.7 warehouse reorder/backdated transfer patch applied successfully")
