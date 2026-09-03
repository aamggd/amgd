#!/usr/bin/env bash
set -euo pipefail

BASE_ZIP="${1:-FushERP-Mobile-v178-ProductionBackfillAccountant-FINAL-Source.zip}"
EXPECTED_BASE_SHA="438a8cae1b71f47e7d16916542d239d355cde101a2669ae99b4f1bce6e1d4f19"
EXPECTED_PATCH_B64_SHA="e661b43eae8b998644739e1b44bb8e056dbb3e510c1bf38659b95531652ade26"
EXPECTED_SCHEMA_B64_SHA="dea7fa1c2ead5d70ed93242a5d9bbe761eaa74b3d2f9a583b52a31fd56af7a40"

command -v zstd >/dev/null
command -v git >/dev/null
command -v unzip >/dev/null

printf '%s  %s\n' "$EXPECTED_BASE_SHA" "$BASE_ZIP" | sha256sum -c -
printf '%s  %s\n' "$EXPECTED_PATCH_B64_SHA" v179-code-only.patch.zst.b64 | sha256sum -c -
printf '%s  %s\n' "$EXPECTED_SCHEMA_B64_SHA" v179-room-schemas-47-48.tar.zst.b64 | sha256sum -c -

rm -rf restored-v179
mkdir restored-v179
unzip -q "$BASE_ZIP" -d restored-v179
ROOT="$(find restored-v179 -type f -name settings.gradle.kts -printf '%h\n' | head -1)"
if [[ -z "$ROOT" ]]; then
  echo "Could not locate Android source root" >&2
  exit 1
fi

base64 -d v179-code-only.patch.zst.b64 | zstd -d -q -o v179-code-only.patch
git -C "$ROOT" init -q
git -C "$ROOT" add -A
git -C "$ROOT" -c user.name='FUSH Restore' -c user.email='restore@local.invalid' commit -qm 'v178 baseline'
git -C "$ROOT" apply --check "$(pwd)/v179-code-only.patch"
git -C "$ROOT" apply "$(pwd)/v179-code-only.patch"

mkdir -p "$ROOT/app/schemas/com.fush.erp.data.FushDatabase"
base64 -d v179-room-schemas-47-48.tar.zst.b64 | zstd -d -q | tar -xf - -C "$ROOT/app/schemas/com.fush.erp.data.FushDatabase"
rm -rf "$ROOT/.git"

grep -q 'versionCode = 179' "$ROOT/app/build.gradle.kts"
grep -q '0.15.4.130-sales-additional-charges-shipment-costs' "$ROOT/app/build.gradle.kts"
grep -q 'FUSH_DB_SCHEMA_VERSION = 48' "$ROOT/app/src/main/java/com/fush/erp/data/FushDatabase.kt"

echo "v179 source delta restored successfully at: $ROOT"
