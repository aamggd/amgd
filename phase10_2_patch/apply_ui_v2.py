from __future__ import annotations

from pathlib import Path
import re

ROOT = Path('FushERP_Mobile_Phase5')
SRC = ROOT / 'app/src/main/java/com/fush/erp'


def load(p: Path) -> str:
    return p.read_text(encoding='utf-8')


def save(p: Path, s: str) -> None:
    p.write_text(s, encoding='utf-8')


def match_close(s: str, pos: int, left: str, right: str) -> int:
    depth = 0
    string = False
    esc = False
    for i in range(pos, len(s)):
        c = s[i]
        if string:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                string = False
            continue
        if c == '"':
            string = True
        elif c == left:
            depth += 1
        elif c == right:
            depth -= 1
            if depth == 0:
                return i
    raise RuntimeError(f'Unbalanced {left}{right}')


files = list(SRC.rglob('*.kt'))
nav = next((p for p in files if 'NavigationBar(' in load(p) and 'NavHost' in load(p)), None)
if nav is None:
    nav = next((p for p in files if 'NavigationBar(' in load(p) and '.navigate(' in load(p)), None)
if nav is None:
    raise RuntimeError('Navigation source not found')

s = load(nav)

# Find the real NavigationBar call, not its import.
bar_pos = s.find('NavigationBar(')
bar_open_paren = s.find('(', bar_pos)
bar_close_paren = match_close(s, bar_open_paren, '(', ')')
q = bar_close_paren + 1
while q < len(s) and s[q].isspace():
    q += 1
if q >= len(s) or s[q] != '{':
    raise RuntimeError('NavigationBar trailing lambda not found')
bar_open = q
bar_close = match_close(s, bar_open, '{', '}')
bar = s[bar_open:bar_close + 1]

loop = re.search(r'([A-Za-z_][A-Za-z0-9_]*)\.forEach\s*\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*->', bar)
if not loop:
    raise RuntimeError('Navigation item loop not found')
items_name, item_name = loop.group(1), loop.group(2)

nav_call = re.search(r'([A-Za-z_][A-Za-z0-9_]*)\.navigate\(\s*' + re.escape(item_name) + r'\.([A-Za-z_][A-Za-z0-9_]*)', bar)
if not nav_call:
    raise RuntimeError('Navigation call not found')
controller, route_field = nav_call.group(1), nav_call.group(2)

label_match = re.search(r'(?:Text\(\s*|text\s*=\s*)' + re.escape(item_name) + r'\.([A-Za-z_][A-Za-z0-9_]*)', bar)
label_field = label_match.group(1) if label_match else 'label'

# Capture selected expression from the real NavigationBarItem and reuse it in the drawer.
sel = re.search(r'selected\s*=\s*(.*?),(?:\s*\n|\s*)onClick\s*=', bar, flags=re.S)
selected_expr = sel.group(1).strip() if sel else 'false'
selected_expr_drawer = re.sub(r'\b' + re.escape(item_name) + r'\b', '_drawerItem', selected_expr)

# Ensure required imports are available.
required = [
    'import androidx.compose.material3.DrawerValue',
    'import androidx.compose.material3.HorizontalDivider',
    'import androidx.compose.material3.ModalDrawerSheet',
    'import androidx.compose.material3.ModalNavigationDrawer',
    'import androidx.compose.material3.NavigationDrawerItem',
    'import androidx.compose.material3.rememberDrawerState',
    'import androidx.compose.runtime.rememberCoroutineScope',
    'import androidx.compose.ui.unit.dp',
    'import kotlinx.coroutines.launch',
]
for imp in required:
    if imp not in s:
        all_imports = list(re.finditer(r'^import .*$', s, flags=re.M))
        if not all_imports:
            raise RuntimeError('No imports found')
        at = all_imports[-1].end()
        s = s[:at] + '\n' + imp + s[at:]

# Re-find NavigationBar after import edits.
bar_pos = s.find('NavigationBar(')
bar_open_paren = s.find('(', bar_pos)
bar_close_paren = match_close(s, bar_open_paren, '(', ')')
q = bar_close_paren + 1
while q < len(s) and s[q].isspace():
    q += 1
bar_open = q
bar_close = match_close(s, bar_open, '{', '}')
bar = s[bar_open:bar_close + 1]

# Four daily destinations + one menu launcher. All other destinations move to drawer.
loop_pattern = re.compile(re.escape(items_name) + r'\.forEach\s*\{\s*' + re.escape(item_name) + r'\s*->')
bar, count = loop_pattern.subn(
    f'{items_name}.filter {{ it.{label_field} in setOf("الرئيسية", "المبيعات", "الإنتاج", "المخزون") }}.forEach {{ {item_name} ->',
    bar,
    count=1,
)
if count != 1:
    raise RuntimeError('Could not reduce bottom navigation')

bar = bar[:-1] + '''
        NavigationBarItem(
            selected = false,
            onClick = { _fushDrawerScope.launch { _fushDrawerState.open() } },
            icon = { Text("☰") },
            label = { Text("القائمة", maxLines = 1) }
        )
''' + bar[-1:]
s = s[:bar_open] + bar + s[bar_close + 1:]

# Wrap the app Scaffold with a side drawer.
sc = re.search(r'\bScaffold\s*\(', s)
if not sc:
    raise RuntimeError('Scaffold not found')
sc_start = sc.start()
p0 = s.find('(', sc.start())
p1 = match_close(s, p0, '(', ')')
end = p1 + 1
r = end
while r < len(s) and s[r].isspace():
    r += 1
if r < len(s) and s[r] == '{':
    end = match_close(s, r, '{', '}') + 1

# Direct material/unit entries intentionally open the master-data page, where its own material/unit tabs live.
data_expr = f'{items_name}.first {{ it.{label_field} == "البيانات" }}.{route_field}'

drawer = f'''
    val _fushDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val _fushDrawerScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = _fushDrawerState,
        drawerContent = {{
            ModalDrawerSheet {{
                Text(
                    "Fush ERP",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(20.dp)
                )
                HorizontalDivider()
                Text(
                    "الأقسام",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                {items_name}.forEach {{ _drawerItem ->
                    NavigationDrawerItem(
                        label = {{ Text(_drawerItem.{label_field}, maxLines = 1) }},
                        selected = {selected_expr_drawer},
                        onClick = {{
                            _fushDrawerScope.launch {{ _fushDrawerState.close() }}
                            {controller}.navigate(_drawerItem.{route_field}) {{
                                launchSingleTop = true
                                restoreState = true
                            }}
                        }},
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }}
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "البيانات الأساسية",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                NavigationDrawerItem(
                    label = {{ Text("المواد والأصناف") }},
                    selected = false,
                    onClick = {{
                        _fushDrawerScope.launch {{ _fushDrawerState.close() }}
                        {controller}.navigate({data_expr}) {{ launchSingleTop = true }}
                    }},
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                NavigationDrawerItem(
                    label = {{ Text("الوحدات") }},
                    selected = false,
                    onClick = {{
                        _fushDrawerScope.launch {{ _fushDrawerState.close() }}
                        {controller}.navigate({data_expr}) {{ launchSingleTop = true }}
                    }},
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }}
        }}
    ) {{
'''

s = s[:sc_start] + drawer + s[sc_start:end] + '\n    } // Phase 10.2 drawer\n' + s[end:]
save(nav, s)

# Make the existing inventory shortcut shorter and clearer on a phone.
for p in files:
    if p == nav:
        continue
    t = load(p)
    if 'إدارة المواد والأصناف والوحدات' in t:
        save(p, t.replace('إدارة المواد والأصناف والوحدات', 'المواد والأصناف والوحدات'))
        break

# UI-only release: DB schema remains at v11.
gradle = ROOT / 'app/build.gradle.kts'
g = load(gradle)
g, a = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 12', g, count=1)
g, b = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.10.2-phase10-ui"', g, count=1)
if a != 1 or b != 1:
    raise RuntimeError('Version update failed')
save(gradle, g)

print('PHASE10_2_UI_PATCH_OK')
print(f'nav={nav}')
print(f'items={items_name}, item={item_name}, label={label_field}, route={route_field}, controller={controller}')
