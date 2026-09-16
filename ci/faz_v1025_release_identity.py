#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
p = root / 'app/build.gradle.kts'
text = p.read_text(encoding='utf-8')
old_code = '        versionCode = 25\n'
old_name = '        versionName = "1.0.24-faz-startup-migration-hotfix"\n'
if text.count(old_code) != 1:
    raise SystemExit(f'versionCode anchor count={text.count(old_code)}')
if text.count(old_name) != 1:
    raise SystemExit(f'versionName anchor count={text.count(old_name)}')
text = text.replace(old_code, '        versionCode = 26\n', 1)
text = text.replace(old_name, '        versionName = "1.0.25-faz-unified-item-master"\n', 1)
p.write_text(text, encoding='utf-8')
print('FAZ_V1025_RELEASE_IDENTITY=PASS')
