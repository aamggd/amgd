from pathlib import Path
import shutil

ROOT = Path('FushERP_Mobile_Phase5')
PATCH = Path(__file__).parent / 'payload'

files = {
    'app/build.gradle.kts': 'build.gradle.kts',
    'app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt': 'ReportExportSupport.kt',
    'app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt': 'ReportsScreen.kt',
}
for target, source in files.items():
    dst = ROOT / target
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(PATCH / source, dst)
print('Phase 15.2 export/print/share patch applied')
