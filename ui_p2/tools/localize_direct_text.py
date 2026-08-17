#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import html
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path('central_source')
UI_ROOT = ROOT / 'app/src/main/java/com/fush/erp/ui'
RES = ROOT / 'app/src/main/res'
OUT_EN = RES / 'values/p2_direct_text.xml'
OUT_AR = RES / 'values-ar/p2_direct_text.xml'
REPORT = Path('ui_p2/generated/direct_text_report.json')
CACHE = Path('ui_p2/generated/translation_cache.json')
ARABIC_RE = re.compile(r'[\u0600-\u06FF]')
IDENT_RE = re.compile(r'[A-Za-z_][A-Za-z0-9_]*')
MIN_REQUEST_INTERVAL = max(0.0, float(os.environ.get('FUSH_TRANSLATION_MIN_INTERVAL', '1.0')))
_LAST_REQUEST_AT = 0.0
_NEW_TRANSLATIONS = 0

GLOSSARY = {
    'المحاسبة والخزينة': 'Accounting & Treasury',
    'المحاسبة': 'Accounting',
    'الخزينة': 'Treasury',
    'المخزون': 'Inventory',
    'المخازن': 'Warehouses',
    'المستودعات': 'Warehouses',
    'المبيعات': 'Sales',
    'المشتريات': 'Purchases',
    'العملاء': 'Customers',
    'الموردون': 'Suppliers',
    'الإنتاج': 'Production',
    'الجودة': 'Quality',
    'الموظفون': 'Employees',
    'مناديب المبيعات': 'Sales Representatives',
    'الصيانة': 'Maintenance',
    'السلامة': 'Safety',
    'الحوكمة': 'Governance',
    'التدقيق': 'Audit',
    'المخاطر': 'Risks',
    'الرقابة الداخلية': 'Internal Control',
    'مرتجع مبيعات': 'Sales Return',
    'مرتجع مشتريات': 'Purchase Return',
    'سبب الإلغاء': 'Reason for cancellation',
    'حفظ': 'Save',
    'إلغاء': 'Cancel',
    'إغلاق': 'Close',
    'حذف': 'Delete',
    'تعديل': 'Edit',
    'إضافة': 'Add',
    'بحث': 'Search',
    'الكل': 'All',
    'نشط': 'Active',
    'غير نشط': 'Inactive',
    'نعم': 'Yes',
    'لا': 'No',
}


def load_cache():
    if CACHE.exists():
        return json.loads(CACHE.read_text(encoding='utf-8'))
    return {}


def save_cache(cache):
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=2, sort_keys=True), encoding='utf-8')


def wait_for_request_slot():
    global _LAST_REQUEST_AT
    if MIN_REQUEST_INTERVAL <= 0:
        return
    now = time.monotonic()
    remaining = MIN_REQUEST_INTERVAL - (now - _LAST_REQUEST_AT)
    if remaining > 0:
        time.sleep(remaining)
    _LAST_REQUEST_AT = time.monotonic()


def store_translation(cache: dict, key: str, value: str) -> str:
    global _NEW_TRANSLATIONS
    cache[key] = value
    _NEW_TRANSLATIONS += 1
    if _NEW_TRANSLATIONS % 5 == 0:
        save_cache(cache)
    return value


def translate(text: str, source: str, target: str, cache: dict) -> str:
    text = text.strip()
    if not text:
        return text
    key = f'{source}>{target}:{text}'
    if key in cache:
        return cache[key]
    if source == 'ar' and target == 'en' and text in GLOSSARY:
        return store_translation(cache, key, GLOSSARY[text])

    params = urllib.parse.urlencode({'client':'gtx','sl':source,'tl':target,'dt':'t','q':text})
    url = 'https://translate.googleapis.com/translate_a/single?' + params
    last = None
    for attempt in range(10):
        try:
            wait_for_request_slot()
            req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0 FUSH-UI-P2'})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.load(resp)
            result = ''.join(part[0] for part in data[0] if part and part[0]).strip()
            if result:
                return store_translation(cache, key, result)
            last = RuntimeError('empty translation response')
        except urllib.error.HTTPError as exc:
            last = exc
            save_cache(cache)
            retry_after = exc.headers.get('Retry-After') if exc.headers else None
            if exc.code in (403, 429, 500, 502, 503, 504):
                try:
                    wait = float(retry_after) if retry_after else min(120.0, 12.0 * (attempt + 1))
                except ValueError:
                    wait = min(120.0, 12.0 * (attempt + 1))
                print(f'translation provider HTTP {exc.code}; waiting {wait:.1f}s before retry {attempt + 2}/10', flush=True)
                time.sleep(wait)
                continue
            raise
        except Exception as exc:
            last = exc
            save_cache(cache)
            wait = min(45.0, 2.0 * (attempt + 1))
            print(f'translation provider error {type(exc).__name__}; waiting {wait:.1f}s before retry {attempt + 2}/10', flush=True)
            time.sleep(wait)
    save_cache(cache)
    raise RuntimeError(f'translation failed for {text!r}: {last}')


def parse_string(src: str, quote: int):
    assert src[quote] == '"'
    i = quote + 1
    literal = []
    segments = []  # ('lit', text) / ('expr', source)
    while i < len(src):
        c = src[i]
        if c == '\\':
            if i + 1 >= len(src):
                return None
            esc = src[i:i+2]
            mapping = {'\\n':'\n','\\t':'\t','\\r':'\r','\\"':'"',"\\'":"'",'\\\\':'\\'}
            literal.append(mapping.get(esc, esc[1]))
            i += 2
            continue
        if c == '"':
            if literal:
                segments.append(('lit', ''.join(literal)))
            return i + 1, segments
        if c == '$':
            if literal:
                segments.append(('lit', ''.join(literal)))
                literal = []
            if i + 1 < len(src) and src[i+1] == '{':
                j = i + 2
                depth = 1
                in_str = False
                esc = False
                while j < len(src) and depth:
                    ch = src[j]
                    if in_str:
                        if esc:
                            esc = False
                        elif ch == '\\':
                            esc = True
                        elif ch == '"':
                            in_str = False
                    else:
                        if ch == '"':
                            in_str = True
                        elif ch == '{':
                            depth += 1
                        elif ch == '}':
                            depth -= 1
                    j += 1
                if depth:
                    return None
                segments.append(('expr', src[i+2:j-1].strip()))
                i = j
                continue
            m = IDENT_RE.match(src, i+1)
            if m:
                segments.append(('expr', m.group(0)))
                i = m.end()
                continue
            literal.append('$')
            i += 1
            continue
        literal.append(c)
        i += 1
    return None


def find_direct_text_strings(src: str):
    found = []
    pos = 0
    while True:
        m = re.search(r'\bText\s*\(', src[pos:])
        if not m:
            break
        call_start = pos + m.start()
        i = pos + m.end()
        while i < len(src) and src[i].isspace(): i += 1
        if src.startswith('text', i):
            j = i + 4
            while j < len(src) and src[j].isspace(): j += 1
            if j < len(src) and src[j] == '=':
                i = j + 1
                while i < len(src) and src[i].isspace(): i += 1
        if i < len(src) and src[i] == '"' and not src.startswith('"""', i):
            parsed = parse_string(src, i)
            if parsed:
                end, segments = parsed
                found.append((i, end, segments, call_start))
                pos = end
                continue
        pos = i + 1
    return found


def make_template(segments):
    ar_parts = []
    args = []
    for kind, val in segments:
        if kind == 'lit':
            ar_parts.append(val)
        else:
            args.append(val)
            ar_parts.append(f'%{len(args)}$s')
    return ''.join(ar_parts), args


def mask_placeholders(template: str):
    tokens = []
    def sub(m):
        token = f'ZXQP{len(tokens)+1}QXZ'
        tokens.append((token, m.group(0)))
        return token
    masked = re.sub(r'%\d+\$s', sub, template)
    return masked, tokens


def translate_template(ar: str, cache: dict):
    masked, tokens = mask_placeholders(ar)
    masked_for_translation = masked.replace('\n', ' ZXQNLQXZ ')
    en = translate(masked_for_translation, 'ar', 'en', cache) if ARABIC_RE.search(masked_for_translation) else masked_for_translation
    en = en.replace('ZXQNLQXZ', '\n').replace('ZxqnLqxz', '\n')
    compact = re.sub(r'\s+', ' ', en)
    for token, placeholder in tokens:
        variants = {token, token.lower(), token.capitalize(), token.replace('Q','q').replace('X','x').replace('Z','z')}
        replaced = False
        for v in variants:
            if v in compact:
                compact = compact.replace(v, placeholder)
                replaced = True
                break
        if not replaced:
            parts = re.split(r'(%\d+\$s)', ar)
            out=[]
            for p in parts:
                if re.fullmatch(r'%\d+\$s', p or ''):
                    out.append(p)
                elif p:
                    out.append(translate(p.replace('\n',' '), 'ar', 'en', cache) if ARABIC_RE.search(p) else p)
            return ''.join(out)
    return compact.strip()


def xml_escape(value: str) -> str:
    value = value.replace('%', '%%')
    value = re.sub(r'%%(\d+\$s)', r'%\1', value)
    value = value.replace('\\', '\\\\')
    value = value.replace("'", "\\'")
    value = value.replace('\n', '\\n')
    return html.escape(value, quote=True)


def read_existing_ar_map():
    result = {}
    for p in (RES/'values-ar').glob('*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for node in root.findall('string'):
            name = node.attrib.get('name')
            if name and node.text and not list(node):
                result[node.text] = name
    return result


def ensure_imports(src: str):
    if 'stringResource(' in src and 'import androidx.compose.ui.res.stringResource' not in src:
        lines = src.splitlines()
        insert = 1
        while insert < len(lines) and (lines[insert].startswith('import ') or not lines[insert].strip()):
            insert += 1
        lines.insert(insert, 'import androidx.compose.ui.res.stringResource')
        src = '\n'.join(lines) + ('\n' if src.endswith('\n') else '')
    if 'R.string.' in src and 'import com.fush.erp.R' not in src:
        lines = src.splitlines()
        insert = 1
        while insert < len(lines) and (lines[insert].startswith('import ') or not lines[insert].strip()):
            insert += 1
        lines.insert(insert, 'import com.fush.erp.R')
        src = '\n'.join(lines) + ('\n' if src.endswith('\n') else '')
    return src


def key_for(path: Path, ar_template: str):
    stem = re.sub(r'[^a-z0-9]+', '_', path.stem.lower()).strip('_')[:28]
    digest = hashlib.sha1(ar_template.encode('utf-8')).hexdigest()[:10]
    return f'p2_{stem}_{digest}'


cache = load_cache()
existing = read_existing_ar_map()
resources = {}  # key -> (en, ar)
changed_files = []
replacements = 0

try:
    for path in sorted(UI_ROOT.rglob('*.kt')):
        if path.name.endswith('.orig'):
            continue
        src = path.read_text(encoding='utf-8')
        matches = find_direct_text_strings(src)
        if not matches:
            continue
        edits = []
        for start, end, segments, call_start in matches:
            ar_template, args = make_template(segments)
            if not ar_template.strip():
                continue
            visible_letters = re.search(r'[A-Za-z\u0600-\u06FF]', ar_template)
            if not visible_letters:
                continue
            existing_key = existing.get(ar_template) if not args else None
            key = existing_key or key_for(path, ar_template)
            if not existing_key and key not in resources:
                if ARABIC_RE.search(ar_template):
                    en = translate_template(ar_template, cache)
                    ar = ar_template
                else:
                    en = ar_template
                    masked, toks = mask_placeholders(ar_template)
                    ar = translate(masked, 'en', 'ar', cache)
                    for tok, ph in toks:
                        ar = ar.replace(tok, ph).replace(tok.lower(), ph)
                resources[key] = (en, ar)
            call = f'stringResource(R.string.{key}'
            if args:
                call += ', ' + ', '.join(args)
            call += ')'
            edits.append((start, end, call))
        if not edits:
            continue
        for start, end, repl in reversed(edits):
            src = src[:start] + repl + src[end:]
            replacements += 1
        src = ensure_imports(src)
        path.write_text(src, encoding='utf-8')
        changed_files.append(str(path.relative_to(ROOT)))
finally:
    save_cache(cache)

OUT_EN.parent.mkdir(parents=True, exist_ok=True)
OUT_AR.parent.mkdir(parents=True, exist_ok=True)
def write_xml(path, language_index):
    lines=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for key,(en,ar) in sorted(resources.items()):
        val=(en,ar)[language_index]
        lines.append(f'    <string name="{key}">{xml_escape(val)}</string>')
    lines.append('</resources>')
    path.write_text('\n'.join(lines)+'\n', encoding='utf-8')
write_xml(OUT_EN,0)
write_xml(OUT_AR,1)

remaining = 0
remaining_files = {}
for path in sorted(UI_ROOT.rglob('*.kt')):
    if path.name.endswith('.orig'): continue
    n=len(find_direct_text_strings(path.read_text(encoding='utf-8')))
    if n:
        remaining += n
        remaining_files[str(path.relative_to(ROOT))]=n

REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps({
    'stage':'P2-direct-text',
    'replacements': replacements,
    'new_resource_pairs': len(resources),
    'translation_cache_entries': len(cache),
    'new_translations_this_run': _NEW_TRANSLATIONS,
    'translation_min_interval_seconds': MIN_REQUEST_INTERVAL,
    'changed_files': changed_files,
    'remaining_direct_text_literals': remaining,
    'remaining_files': remaining_files,
    'note':'Remaining direct literals are reviewed after build; P2 is not final at this stage.'
},ensure_ascii=False,indent=2),encoding='utf-8')
print(REPORT.read_text(encoding='utf-8'))
