from pathlib import Path
import re

ROOT = Path('FushERP_Mobile_Phase5')
home = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
s = home.read_text(encoding='utf-8')

start = s.find('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun HomeShell(')
if start < 0:
    raise RuntimeError('HomeShell start not found')
end_marker = '\n@Composable\nprivate fun DashboardScreen('
end = s.find(end_marker, start)
if end < 0:
    raise RuntimeError('Dashboard marker not found')

new_home = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(container: AppContainer, user: UserEntity, onLogout: () -> Unit) {
    var page by remember { mutableStateOf("الرئيسية") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val modules = remember {
        listOf(
            ModuleCard("المحاسبة والخزينة", "اليومية، الأستاذ، الخزينة والبنوك، ميزان المراجعة والقوائم المالية", "المرحلة 8 جاهزة"),
            ModuleCard("الأصناف والوحدات", "مواد خام، تغليف، منتج نهائي وتحويل الوحدات", "موسع في المرحلة 2"),
            ModuleCard("المخزون والمستودعات", "جرد، فروق، تشغيلات، صلاحية، حجر، إعادة طلب وحركة مخزون", "المرحلة 10 جاهزة"),
            ModuleCard("الموردون والمشتريات", "موردون، فواتير، مرتجعات وقيود تلقائية", "المرحلة 2"),
            ModuleCard("الإنتاج والجودة", "BOM، أوامر الإنتاج، الدفعات، الفحص وCAPA", "المرحلة 3 جاهزة"),
            ModuleCard("المبيعات والعملاء", "نقدي/آجل، تحصيل، عمولات، مرتجعات وتتبع دفعات", "المرحلة 4 جاهزة"),
            ModuleCard("العملات والمحافظات", "صرف تاريخي، تسعير المحافظات، النقل والرسوم وربحية جغرافية", "المرحلة 9 جاهزة"),
            ModuleCard("الأصول والصيانة والسلامة", "الأصول، الأعطال، الوقائي، المعايرة والحوادث", "المرحلة 5 جاهزة"),
            ModuleCard("الحوكمة والتدقيق", "SOP، النماذج، إدارة التغيير، الموافقات وسجل التدقيق", "المرحلة 6 جاهزة"),
            ModuleCard("الموظفون والتدريب", "الكفاءة، التدريب، تصاريح المعدات وربط المشغل بأمر الإنتاج", "المرحلة 7 جاهزة"),
            ModuleCard("لوحة الإدارة", "KPI، التنبيهات والقرار اليومي", "قيد التوسع")
        )
    }

    val drawerItems = remember {
        listOf(
            "الرئيسية" to "الرئيسية",
            "المبيعات" to "المبيعات",
            "الإنتاج والجودة" to "الإنتاج",
            "المخزون والمستودعات" to "المخزون",
            "المشتريات والموردون" to "المشتريات",
            "الحسابات والخزينة" to "الحسابات",
            "العملات والمحافظات" to "العملات",
            "الموظفون والتدريب" to "الموظفون",
            "الصيانة والسلامة" to "الصيانة",
            "الحوكمة والتدقيق" to "الحوكمة"
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Fush ERP",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                )
                HorizontalDivider()
                Text(
                    "أقسام النظام",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                drawerItems.forEach { (label, target) ->
                    NavigationDrawerItem(
                        label = { Text(label, maxLines = 1) },
                        selected = page == target,
                        onClick = {
                            page = target
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "البيانات الأساسية",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                NavigationDrawerItem(
                    label = { Text("المواد والأصناف") },
                    selected = page == "المواد والأصناف",
                    onClick = {
                        page = "المواد والأصناف"
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                NavigationDrawerItem(
                    label = { Text("الوحدات") },
                    selected = page == "الوحدات",
                    onClick = {
                        page = "الوحدات"
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fush ERP — $page") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        Text(user.displayName, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onLogout) { Text("خروج") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("الرئيسية", "المبيعات", "الإنتاج", "المخزون").forEach { label ->
                        NavigationBarItem(
                            selected = page == label,
                            onClick = { page = label },
                            icon = { Text(if (page == label) "●" else "○") },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { scope.launch { drawerState.open() } },
                        icon = { Text("☰") },
                        label = { Text("القائمة", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    )
                }
            }
        ) { pad ->
            when (page) {
                "المبيعات" -> SalesScreen(container, user, Modifier.padding(pad))
                "الإنتاج" -> ProductionScreen(container, user, Modifier.padding(pad))
                "الصيانة" -> MaintenanceScreen(container, user, Modifier.padding(pad))
                "الموظفون" -> EmployeesScreen(container, user, Modifier.padding(pad))
                "المشتريات" -> PurchasesScreen(container, user, Modifier.padding(pad))
                "المخزون" -> AdvancedInventoryScreen(container, user, Modifier.padding(pad), onOpenMasterData = { page = "المواد والأصناف" })
                "الحوكمة" -> GovernanceScreen(container, user, Modifier.padding(pad))
                "العملات" -> CurrencyGeographyScreen(container, user, Modifier.padding(pad))
                "الحسابات" -> AccountingScreen(container, user, Modifier.padding(pad))
                "المواد والأصناف" -> MasterDataScreen(container, Modifier.padding(pad), initialSection = "المواد والأصناف")
                "الوحدات" -> MasterDataScreen(container, Modifier.padding(pad), initialSection = "الوحدات")
                else -> DashboardScreen(container, modules, Modifier.padding(pad))
            }
        }
    }
}
'''

s = s[:start] + new_home + s[end:]

old_sig = 'private fun MasterDataScreen(container: AppContainer, modifier: Modifier = Modifier) {'
new_sig = 'private fun MasterDataScreen(container: AppContainer, modifier: Modifier = Modifier, initialSection: String = "المواد والأصناف") {'
if old_sig not in s:
    raise RuntimeError('MasterDataScreen signature not found')
s = s.replace(old_sig, new_sig, 1)

old_state = 'var section by remember { mutableStateOf("المواد والأصناف") }'
new_state = 'var section by remember(initialSection) { mutableStateOf(initialSection) }'
if old_state not in s:
    raise RuntimeError('MasterData section state not found')
s = s.replace(old_state, new_state, 1)
s = s.replace('Metric("الإصدار", "0.10.1", Modifier.weight(1f))', 'Metric("الإصدار", "0.10.2", Modifier.weight(1f))', 1)
s = s.replace('إدارة المواد والأصناف والوحدات', 'المواد والأصناف والوحدات')
home.write_text(s, encoding='utf-8')

gradle = ROOT / 'app/build.gradle.kts'
g = gradle.read_text(encoding='utf-8')
g, a = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 12', g, count=1)
g, b = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.10.2-phase10-ui"', g, count=1)
if a != 1 or b != 1:
    raise RuntimeError('Version update failed')
gradle.write_text(g, encoding='utf-8')

print('PHASE10_2_UI_PATCH_OK')
