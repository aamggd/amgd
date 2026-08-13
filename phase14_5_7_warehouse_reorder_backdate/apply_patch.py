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

patch_bytes = b"".join(
    base64.b64decode(path.read_text(encoding="ascii").strip(), validate=True)
    for path in chunk_paths
)

with tempfile.NamedTemporaryFile(prefix="phase14_5_7_", suffix=".diff", delete=False) as handle:
    handle.write(patch_bytes)
    patch_path = Path(handle.name)

try:
    result = subprocess.run(
        ["patch", "-p1", "--forward", "--batch", "-i", str(patch_path)],
        cwd=PROJECT,
        check=False,
    )
finally:
    patch_path.unlink(missing_ok=True)

# The repository payload was split across transport chunks. On the released
# 14.5.6 source, GNU patch can reject only the first AdvancedInventoryScreens
# hunk while applying the remaining hunks. Repair that one deterministic edit
# explicitly, then reject any other patch failure.
reject = PROJECT / "app/src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt.rej"
if result.returncode != 0:
    other_rejects = [p for p in PROJECT.rglob("*.rej") if p != reject]
    if other_rejects or not reject.exists():
        raise RuntimeError(f"Unexpected Phase 14.5.7 patch failure; rejects: {other_rejects}")

    screen = PROJECT / "app/src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt"
    text = screen.read_text(encoding="utf-8")
    old = "    val reorder by container.db.advancedInventoryDao().observeReorderAlerts().collectAsState(initial = emptyList())\n"
    new = (
        "    val reorderAsOf = remember { System.currentTimeMillis() }\n"
        "    val reorder by container.db.advancedInventoryDao().observeReorderAlerts(reorderAsOf).collectAsState(initial = emptyList())\n"
    )
    if old in text:
        text = text.replace(old, new, 1)
        screen.write_text(text, encoding="utf-8")
    elif "observeReorderAlerts(reorderAsOf)" not in text:
        raise RuntimeError("Could not repair the known AdvancedInventoryScreens reorder-alert hunk")

    reject.unlink(missing_ok=True)
    Path(str(screen) + ".orig").unlink(missing_ok=True)

remaining = list(PROJECT.rglob("*.rej"))
if remaining:
    raise RuntimeError(f"Unresolved Phase 14.5.7 patch rejects remain: {remaining}")

print("Phase 14.5.7 warehouse reorder/backdated transfer patch applied successfully")
