from pathlib import Path
import base64
import gzip
import subprocess


def main():
    root = Path(__file__).resolve().parent
    encoded = "".join((root / f"patch_part{i}.txt").read_text(encoding="utf-8").strip() for i in range(1, 5))
    data = gzip.decompress(base64.b64decode(encoded))
    patch_file = root / "phase14_5_15_price_list_validity.patch"
    patch_file.write_bytes(data)
    subprocess.run(["patch", "-p1", "-i", str(patch_file)], check=True)
    print("Phase 14.5.15 price-list validity patch applied")


if __name__ == "__main__":
    main()
