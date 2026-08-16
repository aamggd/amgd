#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

ROOT = Path("central_source")
UI_ROOTS = [
    ROOT / "app/src/main/java/com/fush/erp/ui",
]

ARABIC_RE = re.compile(r"[\u0600-\u06FF]")
STRING_RE = re.compile(r'(?P<prefix>\b(?:Text\s*\(\s*(?:text\s*=\s*)?|label\s*=\s*\{\s*Text\s*\(\s*|title\s*=\s*\{\s*Text\s*\(\s*|placeholder\s*=\s*\{\s*Text\s*\(\s*))(?P<quote>\")(?P<text>(?:\\.|[^"\\])*)(?P=quote)', re.S)
QUOTED_RE = re.compile(r'"((?:\\.|[^"\\])*)"')

# Known non-UI literals / machine identifiers that may contain Arabic in legacy code.
# P2 reports these separately and does not auto-classify them as display text.
CONTEXT_EXEMPT_PATTERNS = [
    re.compile(r'page\s*[=!]=?'),
    re.compile(r'target\s*='),
    re.compile(r'route\s*='),
    re.compile(r'when\s*\('),
    re.compile(r'==|!='),
]

rows = []
summary = {}

for root in UI_ROOTS:
    for path in sorted(root.rglob("*.kt")):
        if path.name.endswith(".orig"):
            continue
        text = path.read_text(encoding="utf-8")
        direct = []
        for m in STRING_RE.finditer(text):
            literal = bytes(m.group("text"), "utf-8").decode("unicode_escape") if "\\u" in m.group("text") else m.group("text")
            if not literal.strip():
                continue
            line = text.count("\n", 0, m.start()) + 1
            context = text[max(0, m.start()-120):min(len(text), m.end()+160)].replace("\n", " ")
            direct.append((line, literal, context))
            rows.append({
                "kind": "direct_text",
                "file": str(path.relative_to(ROOT)),
                "line": line,
                "literal": literal,
                "has_arabic": bool(ARABIC_RE.search(literal)),
                "context": context,
            })

        quoted_ar = []
        for m in QUOTED_RE.finditer(text):
            literal = m.group(1)
            if not ARABIC_RE.search(literal):
                continue
            line = text.count("\n", 0, m.start()) + 1
            context = text[max(0, m.start()-100):min(len(text), m.end()+140)].replace("\n", " ")
            exempt_hint = any(p.search(context) for p in CONTEXT_EXEMPT_PATTERNS)
            quoted_ar.append((line, literal, context, exempt_hint))
            rows.append({
                "kind": "arabic_literal",
                "file": str(path.relative_to(ROOT)),
                "line": line,
                "literal": literal,
                "has_arabic": True,
                "exempt_hint": exempt_hint,
                "context": context,
            })

        summary[str(path.relative_to(ROOT))] = {
            "direct_text": len(direct),
            "arabic_literals": len(quoted_ar),
            "arabic_literal_exempt_hints": sum(1 for *_, e in quoted_ar if e),
        }

out = Path("ui_p2/extract")
out.mkdir(parents=True, exist_ok=True)
with (out / "candidates.csv").open("w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=["kind","file","line","literal","has_arabic","exempt_hint","context"])
    w.writeheader()
    for r in rows:
        r.setdefault("exempt_hint", "")
        w.writerow(r)

unique_ar = sorted({r["literal"] for r in rows if r["kind"] == "arabic_literal"})
unique_direct = sorted({r["literal"] for r in rows if r["kind"] == "direct_text"})
(out / "unique_arabic_literals.txt").write_text("\n".join(unique_ar) + "\n", encoding="utf-8")
(out / "unique_direct_text.txt").write_text("\n".join(unique_direct) + "\n", encoding="utf-8")
(out / "summary.json").write_text(json.dumps({
    "files": summary,
    "totals": {
        "direct_text_occurrences": sum(v["direct_text"] for v in summary.values()),
        "arabic_literal_occurrences": sum(v["arabic_literals"] for v in summary.values()),
        "unique_direct_text": len(unique_direct),
        "unique_arabic_literals": len(unique_ar),
    }
}, ensure_ascii=False, indent=2), encoding="utf-8")
print((out / "summary.json").read_text(encoding="utf-8"))
