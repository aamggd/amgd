import re
import subprocess
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    raise SystemExit(2)

nodes = [
    n for n in root.iter("node")
    if n.attrib.get("text") == target or n.attrib.get("content-desc") == target
]
if not nodes:
    raise SystemExit(3)

match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", nodes[0].attrib.get("bounds", ""))
if not match:
    raise SystemExit(4)

x1, y1, x2, y2 = map(int, match.groups())
subprocess.check_call([
    "adb", "shell", "input", "tap",
    str((x1 + x2) // 2), str((y1 + y2) // 2)
])
