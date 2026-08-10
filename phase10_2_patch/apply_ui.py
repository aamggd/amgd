from __future__ import annotations

from pathlib import Path
import re

ROOT = Path('FushERP_Mobile_Phase5')
SRC = ROOT / 'app/src/main/java/com/fush/erp'


def read(path: Path) -> str:
    return path.read_text(encoding='utf-8')


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding='utf-8')


def matching(text: str, start: int, open_ch: str, close_ch: str) -> int:
    depth = 0
    in_string = False
    escaped = False
    for i in range(start, len(text)):
        c = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == '"':
                in_string = False
            continue
        if c == '"':
            in_string = True
            continue
        if c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return i
    raise RuntimeError(f'Unbalanced {open_ch}{close_ch} from {start}')


# Locate the actual app navigation source after all previous phase patches.
kt_files = list(SRC.rglob('*.kt'))
nav_path = None
for p in kt_files:
    t = read(p)
    if 'NavigationBar(' in t and 'NavHost' in t:
        nav_path = p
        break
if nav_path is None:
    for p in kt_files:
        t = read(p)
        if 'NavigationBar' in t and 'navigate(' in t:
            nav_path = p
            break
if nav_path is None:
    raise RuntimeError('Could not locate navigation source')

text = read(nav_path)

# Infer the existing navigation item names/properties instead of hard-coding old implementation details.
bar_pos = text.find('NavigationBar')
bar_brace = text.find('{', bar_pos)
bar_end = matching(text, bar_brace, '{', '}')
bar = text[bar_brace:bar_end + 1]

m_collection = re.search(r'([A-Za-z_][A-Za-z0-9_]*)\.forEach\s*\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*->', bar)
if not m_collection:
    raise RuntimeError('Could not infer bottom navigation collection')
collection, item_var = m_collection.group(1), m_collection.group(2)

m_nav = re.search(r'([A-Za-z_][A-Za-z0-9_]*)\.navigate\(\s*' + re.escape(item_var) + r'\.([A-Za-z_][A-Za-z0-9_]*)', bar)
if not m_nav:
    raise RuntimeError('Could not infer NavController/route property')
nav_controller, route_prop = m_nav.group(1), m_nav.group(2)

m_label = re.search(r'Text\(\s*' + re.escape(item_var) + r'\.([A-Za-z_][A-Za-z0-9_]*)', bar)
label_prop = m_label.group(1) if m_label else 'label'

m_current = re.search(r'selected\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*==\s*' + re.escape(item_var) + r'\.' + re.escape(route_prop), bar)
current_route = m_current.group(1) if m_current else 'currentRoute'

# Add explicit imports. Duplicate imports are avoided.
imports = [
    'import androidx.compose.material3.DrawerValue',
    'import androidx.compose.material3.HorizontalDivider',
    'import androidx.compose.material3.ModalDrawerSheet',
    'import androidx.compose.material3.ModalNavigationDrawer',
    'import androidx.compose.material3.NavigationDrawerItem',
    'import androidx.compose.material3.rememberDrawerState',
    'import androidx.compose.runtime.rememberCoroutineScope',
    'import kotlinx.coroutines.launch',
]
last_import = max((m.end() for m in re.finditer(r'^import .*$', text, flags=re.M)), default=0)
for imp in imports:
    if imp not in text:
        text = text[:last_import] + '\n' + imp + text[last_import:]
        last_import += len(imp) + 1

# Refresh navigation block positions after import insertion.
bar_pos = text.find('NavigationBar')
bar_brace = text.find('{', bar_pos)
bar_end = matching(text, bar_brace, '{', '}')
bar = text[bar_brace:bar_end + 1]

# Keep only the four high-frequency modules in the permanent phone bottom bar.
old_loop = f'{collection}.forEach {{ {item_var} ->'
if old_loop not in bar:
    # tolerate formatting/new lines
    pattern = re.compile(re.escape(collection) + r'\.forEach\s*\{\s*' + re.escape(item_var) + r'\s*->')
    bar, n = pattern.subn(
        f'{collection}.filter {{ it.{label_prop} in setOf("الرئيسية", "المبيعات", "الإنتاج", "المخزون") }}.forEach {{ {item_var} ->',
        bar,
        count=1,
    )
    if n != 1:
        raise RuntimeError('Could not filter bottom navigation items')
else:
    bar = bar.replace(
        old_loop,
        f'{collection}.filter {{ it.{label_prop} in setOf("الرئيسية", "المبيعات", "الإنتاج", "المخزون") }}.forEach {{ {item_var} ->',
        1,
    )

# Add one compact menu button to the bottom navigation.
menu_item = '''
        NavigationBarItem(
            selected = false,
            onClick = { _fushDrawerScope.launch { _fushDrawerState.open() } },
            icon = { Text("☰") },
            label = { Text("القائمة", maxLines = 1) }
        )
'''
bar = bar[:-1] + menu_item + bar[-1:]
text = text[:bar_brace] + bar + text[bar_end + 1:]

# Find Scaffold again and wrap the whole phone layout with a Material 3 side drawer.
scaffold_match = re.search(r'\bScaffold\s*\(', text)
if not scaffold_match:
    raise RuntimeError('Could not locate Scaffold')
scaffold_start = scaffold_match.start()
paren_start = text.find('(', scaffold_match.start())
paren_end = matching(text, paren_start, '(', ')')
scan = paren_end + 1
while scan < len(text) and text[scan].isspace():
    scan += 1
if scan < len(text) and text[scan] == '{':
    scaffold_end = matching(text, scan, '{', '}') + 1
else:
    scaffold_end = paren_end + 1

# Determine the route used by the existing data/master-data module.
data_route_expr = None
# Prefer an item whose visible label is البيانات.
list_region = text[max(0, text.find(collection) - 6000):scaffold_start]
entry_patterns = [
    re.compile(r'[^\n]*(?:"البيانات"|label\s*=\s*"البيانات")[^\n]*'),
]
for pat in entry_patterns:
    mm = pat.search(list_region)
    if mm:
        line = mm.group(0)
        # If route is a quoted constructor argument, capture the first non-Arabic quoted token.
        quoted = re.findall(r'"([^"]+)"', line)
        candidates = [q for q in quoted if q != 'البيانات']
        if candidates:
            data_route_expr = '"' + candidates[0] + '"'
            break
# Fallback: navigate to the item already labelled البيانات at runtime; no hardcoded route required.

before = '''
    val _fushDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val _fushDrawerScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = _fushDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Fush ERP", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
                HorizontalDivider()
                Text("الأقسام", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                ''' + collection + '''.forEach { _drawerItem ->
                    NavigationDrawerItem(
                        label = { Text(_drawerItem.''' + label_prop + ''') },
                        selected = ''' + current_route + ''' == _drawerItem.''' + route_prop + ''',
                        onClick = {
                            _fushDrawerScope.launch { _fushDrawerState.close() }
                            ''' + nav_controller + '''.navigate(_drawerItem.''' + route_prop + ''') {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("البيانات الأساسية", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("المواد والأصناف") },
                    selected = false,
                    onClick = {
                        _fushDrawerScope.launch { _fushDrawerState.close() }
                        ''' + nav_controller + '''.navigate(''' + ((data_route_expr or (collection + '.first { it.' + label_prop + ' == "البيانات" }.' + route_prop))) + ''') { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                NavigationDrawerItem(
                    label = { Text("الوحدات") },
                    selected = false,
                    onClick = {
                        _fushDrawerScope.launch { _fushDrawerState.close() }
                        ''' + nav_controller + '''.navigate(''' + ((data_route_expr or (collection + '.first { it.' + label_prop + ' == "البيانات" }.' + route_prop))) + ''') { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    ) {
'''

after = '\n    } // Phase 10.2 side drawer\n'
text = text[:scaffold_start] + before + text[scaffold_start:scaffold_end] + after + text[scaffold_end:]

write(nav_path, text)

# Add a prominent discoverability note/shortcut label to the advanced inventory screen if present.
for p in kt_files:
    if p == nav_path:
        continue
    t = read(p)
    if 'إدارة المواد والأصناف والوحدات' in t:
        # Make wording shorter and unmistakable on small screens.
        t = t.replace('إدارة المواد والأصناف والوحدات', 'المواد والأصناف والوحدات')
        write(p, t)
        break

# Version-only update; DB stays at v11 because this is UI/navigation only.
gradle = ROOT / 'app/build.gradle.kts'
g = read(gradle)
g, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 12', g, count=1)
g, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.10.2-phase10-ui"', g, count=1)
if not (n1 and n2):
    raise RuntimeError('Could not update Android version')
write(gradle, g)

print('PHASE10_2_UI_PATCH_OK')
print('NAV_FILE=', nav_path)
print('NAV_COLLECTION=', collection, 'ITEM=', item_var, 'ROUTE=', route_prop, 'LABEL=', label_prop)
