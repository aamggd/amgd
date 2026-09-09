#!/usr/bin/env bash
set -euo pipefail
SHARE='https://firestorage.ai/ja/f/8yyd0ER8uxWe'
curl -fsSL "$SHARE" -o /tmp/share.html
python3 - <<'PY'
import re,subprocess,os,html
base='https://firestorage.ai'
s=html.unescape(open('/tmp/share.html','r',encoding='utf-8',errors='ignore').read())
srcs=[]
for x in re.findall(r'<script[^>]+src="([^"]+)"',s):
    if x not in srcs: srcs.append(x)
print('SCRIPTS',len(srcs))
alltxt=[]
for i,src in enumerate(srcs):
    url=src if src.startswith('http') else base+src
    p=f'/tmp/js{i}.js'
    subprocess.run(['curl','-fsSL',url,'-o',p],check=False)
    if os.path.exists(p):
        txt=open(p,'r',encoding='utf-8',errors='ignore').read()
        alltxt.append((url,txt))
for key in ['requiresCushion','fileId','download','/dev/file/','shares/']:
    print('\n===',key,'===')
    shown=0
    for url,txt in alltxt:
        pos=0
        while shown<80:
            j=txt.find(key,pos)
            if j<0: break
            print('URL',url)
            print(txt[max(0,j-350):j+700].replace('\n',' ')[:1200])
            print()
            shown+=1; pos=j+len(key)
        if shown>=80: break
PY
