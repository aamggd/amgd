#!/usr/bin/env bash
set -euo pipefail
curl -fsSL 'https://firestorage.ai/_next/static/chunks/app/(files)/ja/f/page-4d19ae0a4ce39d79.js' -o /tmp/page.js
python3 - <<'PY'
import re
s=open('/tmp/page.js','r',encoding='utf-8',errors='ignore').read()
for pat in [r'/api/[^"\']+',r'https?://[^"\']+',r'download[^"\']*',r'publicId[^"\']*',r'shareId[^"\']*']:
    print('PATTERN',pat)
    for x in list(dict.fromkeys(re.findall(pat,s)))[:100]: print(x[:300])
PY
