#!/usr/bin/env bash
set -euo pipefail
API='https://api.firestorage.ai/dev/file/shares/8yyd0ER8uxWe/files?maxResults=1000'
curl -fsSL "$API" -o /tmp/files.json
python3 - <<'PY'
import json
obj=json.load(open('/tmp/files.json'))
print(json.dumps(obj, ensure_ascii=False, indent=2))
PY
