from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: finalize_recovery.py <project_dir>")

root = Path(sys.argv[1]).resolve()

# Give this continuation build its own permanent package identity so it can be
# installed beside BOTH the original com.fush.erp app and the earlier preview.
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = s.replace('applicationId = "com.fush.erp.preview"', 'applicationId = "com.fush.erp.recovery"')
s = s.replace('versionCode = 64', 'versionCode = 65')
s = s.replace(
    'versionName = "0.15.4.25-combined-recovery-party-quality"',
    'versionName = "0.15.4.26-stable-recovery-party-quality"',
)
p.write_text(s, encoding="utf-8")

# The recovery app is allowed to import verified backups from the original
# FUSH package. Other unrelated package ids remain rejected.
p = root / "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt"
s = p.read_text(encoding="utf-8")
s = s.replace(
    '(BuildConfig.APPLICATION_ID == "com.fush.erp.preview" && packageId == "com.fush.erp")',
    '(BuildConfig.APPLICATION_ID == "com.fush.erp.recovery" && packageId == "com.fush.erp")',
)
p.write_text(s, encoding="utf-8")

# Make the side-by-side continuation unmistakable in Android Settings/launcher.
p = root / "app/src/main/res/values/strings.xml"
s = p.read_text(encoding="utf-8")
s = s.replace('<string name="app_name">Fush ERP</string>', '<string name="app_name">Fush ERP Recovery</string>')
p.write_text(s, encoding="utf-8")

# Guardrails.
checks = {
    root / "app/build.gradle.kts": [
        'applicationId = "com.fush.erp.recovery"',
        'versionCode = 65',
        '0.15.4.26-stable-recovery-party-quality',
    ],
    root / "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt": [
        'BuildConfig.APPLICATION_ID == "com.fush.erp.recovery"',
        'packageId == "com.fush.erp"',
    ],
    root / "app/src/main/res/values/strings.xml": ['Fush ERP Recovery'],
}
for path, tokens in checks.items():
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"finalize verification failed: {token} missing from {path}")

print("Phase 14.5.26 stable recovery identity prepared")
