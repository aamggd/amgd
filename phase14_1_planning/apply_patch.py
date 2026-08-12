from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase14_1_planning/phase14_1.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "169bf58116f0ee3556b0119c3347fbd8342053a0a2512dfbba2d7d9920cf3b0d"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 14.1 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "320819854a3c65e5e9142d42011025f92066845d124163a40194e044446162de"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 14.1 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 29' in text
assert 'versionName = "0.13.9-phase13-production-export"' in text

proc = subprocess.run(
    ["patch", "-p1", "--forward", "--batch"],
    cwd=root,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode("utf-8", errors="replace"))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding="utf-8")
assert 'versionCode = 30' in text
assert 'versionName = "0.14.1-phase14-seasonality-demand"' in text

checks = {
    "app/src/main/java/com/fush/erp/data/entity/PlanningEntities.kt": ["DemandSeasonalityEntity", "DemandForecastSnapshot"],
    "app/src/main/java/com/fush/erp/data/dao/PlanningDao.kt": ["monthlyDemandHistory", "observeSeasonality"],
    "app/src/main/java/com/fush/erp/domain/PlanningMath.kt": ["fun baseline", "fun forecast"],
    "app/src/main/java/com/fush/erp/domain/PlanningService.kt": ["forecastNextMonth", "saveSeasonality"],
    "app/src/main/java/com/fush/erp/ui/screens/PlanningScreen.kt": ["التخطيط والموسمية", "توقع الشهر القادم"],
    "app/src/test/java/com/fush/erp/domain/PlanningMathTest.kt": ["forecast_applies_configured_seasonality_factor"],
    "PHASE14_1_SCOPE.md": ["Phase 14.1", "12-month average net demand"],
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding="utf-8")
    for needle in needles:
        assert needle in content, f"{relative}: {needle}"

migrations = (root / "app/src/main/java/com/fush/erp/data/Migrations.kt").read_text(encoding="utf-8")
database = (root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt").read_text(encoding="utf-8")
container = (root / "app/src/main/java/com/fush/erp/data/AppContainer.kt").read_text(encoding="utf-8")
assert "MIGRATION_12_13" in migrations
assert "version = 13" in database
assert "planningDao(): PlanningDao" in database
assert "MIGRATION_12_13" in container

print("Phase 14.1 patch checks passed")
