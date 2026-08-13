#!/usr/bin/env python3
from pathlib import Path
import base64, gzip, subprocess, tempfile

root = Path("FushERP_Mobile_Phase5")
if not root.is_dir():
    raise SystemExit("FushERP_Mobile_Phase5 source directory not found")

gradle_path = root / "app/build.gradle.kts"
db_path = root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
gradle = gradle_path.read_text(encoding="utf-8")
db = db_path.read_text(encoding="utf-8")
if "versionCode = 46" not in gradle or "0.15.4.7-phase14.5-warehouse-reorder" not in gradle:
    raise SystemExit("Phase 14.5.7 source baseline mismatch")
if "version = 20" not in db:
    raise SystemExit("Room schema 20 baseline not found")

payload = "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"

diff_bytes = gzip.decompress(base64.b64decode(payload))
with tempfile.NamedTemporaryFile(prefix="phase1458-", suffix=".diff", delete=False) as tmp:
    tmp.write(diff_bytes)
    diff_path = tmp.name

subprocess.run(["patch", "-p1", "--forward", "--batch", "-i", diff_path], cwd=root, check=True)

checks = {
    "app/build.gradle.kts": ["versionCode = 47", "0.15.4.8-phase14.5-historical-stock-guard"],
    "app/src/main/java/com/fush/erp/data/FushDatabase.kt": ["version = 20"],
    "app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt": ["lotMovementTimeline", "ORDER BY movementDate ASC, id ASC"],
    "app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt": ["validateHistoricalTransferAvailability", "validateHistoricalQuantity"],
    "app/src/main/java/com/fush/erp/domain/WarehouseTransferMath.kt": ["minimumAvailableFrom", "WarehouseTransferBalancePoint"],
    "app/src/test/java/com/fush/erp/domain/WarehouseTransferMathTest.kt": ["minimumAvailableFrom_detects_later_historical_dip", "validateHistoricalQuantity_rejects_backdated_transfer_that_breaks_later_balance"],
    "PHASE14_5_8_SCOPE.md": ["Historical Stock Guard"],
}
for rel, needles in checks.items():
    text = (root / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"Patch verification failed: {rel} missing {needle}")
print("Phase 14.5.8 historical stock guard patch applied successfully")
