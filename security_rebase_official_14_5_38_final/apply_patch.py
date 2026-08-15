from pathlib import Path
import base64, gzip, hashlib

ROOT = Path(__file__).resolve().parent
PATCH_SHA256 = "629117dc269ee836c741df1c43fa61275b980eb99ac8caf8f0d426ab3feef0b1"
GZIP_SHA256 = "26aa15a46705f0dcd702ec7d854d8ad3ab778ccb89fe184d39bf977186cd2717"

parts = sorted(ROOT.glob("payload_*.b64"))
if not parts:
    raise SystemExit("No payload parts found")
encoded = b"".join(p.read_bytes() for p in parts)
raw_gz = base64.b64decode(encoded, validate=True)
if hashlib.sha256(raw_gz).hexdigest() != GZIP_SHA256:
    raise SystemExit("Compressed bundle SHA-256 mismatch")
patch = gzip.decompress(raw_gz)
if hashlib.sha256(patch).hexdigest() != PATCH_SHA256:
    raise SystemExit("Patch SHA-256 mismatch")
out = ROOT / "security_rebase_final.patch"
out.write_bytes(patch)
print(out)
