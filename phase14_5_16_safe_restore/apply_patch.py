from pathlib import Path
import base64, gzip, subprocess

ROOT = Path("FushERP_Mobile_Phase5")
PARTS = Path(__file__).parent
encoded = "".join((PARTS / f"patch_{i}.txt").read_text().strip() for i in range(1, 2 + 1))
patch = gzip.decompress(base64.b64decode(encoded))
proc = subprocess.run(["patch", "-p1"], cwd=ROOT, input=patch, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(proc.stdout.decode(errors="replace"))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)
print("Phase 14.5.16 safe backup/restore patch applied")
