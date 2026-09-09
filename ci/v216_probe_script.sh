#!/usr/bin/env bash
set -euo pipefail
SHARE='https://firestorage.ai/ja/f/8yyd0ER8uxWe'
if command -v google-chrome >/dev/null 2>&1; then CHROME=google-chrome; elif command -v chromium >/dev/null 2>&1; then CHROME=chromium; else echo 'NO_CHROME'; exit 2; fi
"$CHROME" --headless --no-sandbox --disable-gpu --virtual-time-budget=8000 --dump-dom "$SHARE" > /tmp/dom.html 2>/tmp/chrome.err || true
printf '%s\n' '--- chrome stderr ---'; tail -50 /tmp/chrome.err || true
printf '%s\n' '--- dom markers ---'
grep -Ein 'v216-ci-patch|download|href=|01a088279df8719f87554e2d5cf7f504|5ngbI0C40rCIiYXq' /tmp/dom.html | head -120 || true
printf '%s\n' '--- candidate urls ---'
python3 - <<'PY'
import re, html
s=html.unescape(open('/tmp/dom.html','r',encoding='utf-8',errors='ignore').read())
vals=[]
for x in re.findall(r'https?://[^"\'<> ]+', s):
    if x not in vals: vals.append(x)
for x in vals[:200]: print(x)
PY
