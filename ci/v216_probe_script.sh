#!/usr/bin/env bash
set -euo pipefail
BASE='https://api.firestorage.ai/dev/file'
SHARE='8yyd0ER8uxWe'
FILE='01a088279df8719f87554e2d5cf7f504'
COOKIE=/tmp/firestorage.cookies
rm -f "$COOKIE"
printf '%s\n' '--- create cushion session ---'
curl -sS -D /tmp/cushion.headers -c "$COOKIE" -b "$COOKIE" -H 'Content-Type: application/json' -X POST "$BASE/shares/$SHARE/cushion-sessions" --data '{"password":null}' -o /tmp/cushion.json
cat /tmp/cushion.headers
cat /tmp/cushion.json; echo
printf '%s\n' '--- request download url ---'
curl -sS -D /tmp/download.headers -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/shares/$SHARE/files/$FILE/download" -o /tmp/download.json
cat /tmp/download.headers
cat /tmp/download.json; echo
python3 - <<'PY'
import json
try:
 d=json.load(open('/tmp/download.json'))
 print('DOWNLOAD_URL=',d.get('downloadUrl'))
except Exception as e:
 print('DOWNLOAD_JSON_ERROR',e)
PY
