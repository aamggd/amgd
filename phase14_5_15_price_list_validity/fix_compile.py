from pathlib import Path

path = Path("FushERP_Mobile_Phase5/app/src/main/java/com/fush/erp/ui/screens/GeographyScreens.kt")
text = path.read_text(encoding="utf-8")
needle = "import androidx.compose.ui.Alignment"
if needle not in text:
    lines = text.splitlines()
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith("import "):
            insert_at = i + 1
    lines.insert(insert_at, needle)
    text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    path.write_text(text, encoding="utf-8")
print("Phase 14.5.15 compile fix applied")
