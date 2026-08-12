from pathlib import Path
import base64
import gzip
import subprocess

ROOT = Path('FushERP_Mobile_Phase5')
PATCH_FILE = Path(__file__).parent / 'phase15_2.diff.gz.b64'
patch = gzip.decompress(base64.b64decode(PATCH_FILE.read_text().strip())).decode('utf-8')
subprocess.run(['git', 'apply', '--whitespace=nowarn', '-'], input=patch, text=True, check=True)
print('Phase 15.2 export/print/share patch applied')
