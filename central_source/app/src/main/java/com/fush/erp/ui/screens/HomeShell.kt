package com.fush.erp.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.fush.erp.R
import com.fush.erp.ui.*
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.SecurityPermissions
import com.fush.erp.domain.SessionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private data class ModuleCard(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val target: String,
    @StringRes val badgeRes: Int? = null,
)

private data class DrawerItem(@StringRes val labelRes: Int, val target: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    container: AppContainer,
    user: UserEntity,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("الرئيسية") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showSnackbar = remember(scope, snackbarHostState) {
        { text: String ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text)
            }
            Unit
        }
    }
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val permissionSet = remember(rolePermissions) { rolePermissions.toSet() }
    val lastActivityAt = remember(user.id) { AtomicLong(System.currentTimeMillis()) }
    fun can(permission: String): Boolean = user.role == "ADMIN" || permission in permissionSet
    val pagePermissions = remember {
        mapOf(
            "المبيعات" to SecurityPermissions.SALES_VIEW,
            "العملاء" to SecurityPermissions.CUSTOMERS_VIEW,
            "التحصيلات" to SecurityPermissions.SALES_VIEW,
            "الإنتاج" to SecurityPermissions.PRODUCTION_VIEW,
            "تقارير الإنتاج" to SecurityPermissions.REPORTS_VIEW,
            "التخطيط" to SecurityPermissions.PLANNING_VIEW,
            "النسخ الاحتياطي" to SecurityPermissions.BACKUP_CREATE,
            "المخزون" to SecurityPermissions.INVENTORY_VIEW,
            "المشتريات" to SecurityPermissions.PURCHASES_VIEW,
            "الموردون" to SecurityPermissions.SUPPLIERS_VIEW,
            "الحسابات" to SecurityPermissions.ACCOUNTING_VIEW,
            "العملات" to SecurityPermissions.GEOGRAPHY_VIEW,
            "الموظفون" to SecurityPermissions.EMPLOYEES_VIEW,
            "مناديب المبيعات" to SecurityPermissions.SALES_REPS_VIEW,
            "الصيانة" to SecurityPermissions.MAINTENANCE_VIEW,
            "الحوكمة" to SecurityPermissions.GOVERNANCE_VIEW,
            "التقارير" to SecurityPermissions.REPORTS_VIEW,
            "المخاطر" to SecurityPermissions.RISK_VIEW,
            "المواد والأصناف" to SecurityPermissions.MASTER_DATA_VIEW,
            "الوحدات" to SecurityPermissions.MASTER_DATA_VIEW,
            "المستخدمون" to SecurityPermissions.USERS_VIEW,
        )
    }
    fun canOpen(target: String): Boolean = target == "الرئيسية" || (pagePermissions[target]?.let { can(it) } ?: false)
    fun navigate(target: String) { if (canOpen(target)) page = target }
    LaunchedEffect(page, permissionSet, user.role) {
        if (!canOpen(page)) page = "الرئيسية"
    }
    LaunchedEffect(user.id, user.sessionVersion) {
        val sessionStartedAt = user.lastLoginAt ?: System.currentTimeMillis()
        while (true) {
            delay(15_000L)
            val fresh = container.db.userDao().byId(user.id)
            val now = System.currentTimeMillis()
            val timedOut = SessionPolicy.shouldExpire(
                settings = container.sessionSettings.current(),
                sessionStartedAt = sessionStartedAt,
                lastActivityAt = lastActivityAt.get(),
                now = now
            )
            if (fresh == null || !fresh.isActive || fresh.sessionVersion != user.sessionVersion || timedOut) {
                onLogout()
                break
            }
        }
    }

    val modules = remember {
        listOf(
            ModuleCard(R.string.module_accounting_title, R.string.module_accounting_subtitle, "الحسابات", R.string.badge_finance),
            ModuleCard(R.string.module_items_title, R.string.module_items_subtitle, "المواد والأصناف", R.string.badge_data),
            ModuleCard(R.string.module_inventory_title, R.string.module_inventory_subtitle, "المخزون", R.string.badge_operations),
            ModuleCard(R.string.module_purchases_title, R.string.module_purchases_subtitle, "المشتريات", R.string.badge_supply),
            ModuleCard(R.string.module_production_title, R.string.module_production_subtitle, "الإنتاج", R.string.badge_production),
            ModuleCard(R.string.module_sales_title, R.string.module_sales_subtitle, "المبيعات", R.string.badge_sales),
            ModuleCard(R.string.module_geo_title, R.string.module_geo_subtitle, "العملات", R.string.badge_pricing),
            ModuleCard(R.string.module_maintenance_title, R.string.module_maintenance_subtitle, "الصيانة", R.string.badge_assets),
            ModuleCard(R.string.module_governance_title, R.string.module_governance_subtitle, "الحوكمة", R.string.badge_governance),
            ModuleCard(R.string.module_employees_title, R.string.module_employees_subtitle, "الموظفون", R.string.badge_hr),
            ModuleCard(R.string.module_reps_title, R.string.module_reps_subtitle, "مناديب المبيعات", R.string.badge_sales),
            ModuleCard(R.string.module_reports_title, R.string.module_reports_subtitle, "التقارير", R.string.badge_analytics),
            ModuleCard(R.string.module_risks_title, R.string.module_risks_subtitle, "المخاطر", R.string.badge_control),
            ModuleCard(R.string.module_dashboard_title, R.string.module_dashboard_subtitle, "الرئيسية", R.string.badge_management),
            ModuleCard(R.string.module_planning_title, R.string.module_planning_subtitle, "التخطيط", R.string.badge_planning),
            ModuleCard(R.string.module_backup_title, R.string.module_backup_subtitle, "النسخ الاحتياطي", R.string.badge_system),
        )
    }


    val visibleModules = remember(modules, permissionSet, user.role) { modules.filter { canOpen(it.target) } }

    val drawerItems = remember {
        listOf(
            DrawerItem(R.string.nav_home, "الرئيسية"),
            DrawerItem(R.string.nav_sales, "المبيعات"),
            DrawerItem(R.string.nav_customers, "العملاء"),
            DrawerItem(R.string.nav_production_quality, "الإنتاج"),
            DrawerItem(R.string.nav_planning_seasonality, "التخطيط"),
            DrawerItem(R.string.nav_backup_restore, "النسخ الاحتياطي"),
            DrawerItem(R.string.nav_inventory_warehouses, "المخزون"),
            DrawerItem(R.string.nav_purchases, "المشتريات"),
            DrawerItem(R.string.nav_suppliers, "الموردون"),
            DrawerItem(R.string.nav_accounting_treasury, "الحسابات"),
            DrawerItem(R.string.nav_currencies_governorates, "العملات"),
            DrawerItem(R.string.nav_employees, "الموظفون"),
            DrawerItem(R.string.nav_sales_reps, "مناديب المبيعات"),
            DrawerItem(R.string.nav_maintenance_safety, "الصيانة"),
            DrawerItem(R.string.nav_governance_audit, "الحوكمة"),
            DrawerItem(R.string.nav_executive_dashboard, "الرئيسية"),
            DrawerItem(R.string.nav_comprehensive_reports, "التقارير"),
            DrawerItem(R.string.nav_risks_controls, "المخاطر"),
        )
    }

    val openMenuDescription = stringResource(R.string.drawer_open_main_menu)
    val themeToggleDescription = stringResource(
        if (darkTheme) R.string.theme_switch_to_light else R.string.theme_switch_to_dark
    )

    ModalNavigationDrawer(
        modifier = Modifier.pointerInput(user.id) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent()
                    lastActivityAt.set(System.currentTimeMillis())
                }
            }
        },
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FushBrand(compact = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FushUserAvatar(user.displayName)
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    stringResource(R.string.drawer_active_session),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.preferences_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    Text(
                        stringResource(R.string.preferences_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = languageTag == "ar",
                            onClick = { onLanguageChange("ar") },
                            label = { Text(stringResource(R.string.language_arabic)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = languageTag == "en",
                            onClick = { onLanguageChange("en") },
                            label = { Text(stringResource(R.string.language_english)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.preferences_theme),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !darkTheme,
                            onClick = { if (darkTheme) onToggleTheme() },
                            label = { Text(stringResource(R.string.theme_light)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = darkTheme,
                            onClick = { if (!darkTheme) onToggleTheme() },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        stringResource(R.string.drawer_system_sections),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    drawerItems.filter { canOpen(it.target) }.forEach { item ->
                        val target = item.target
                        NavigationDrawerItem(
                            label = { Text(stringResource(item.labelRes), maxLines = 1) },
                            icon = { NavIcon(destinationIcon(target), selected = page == target) },
                            selected = page == target,
                            onClick = {
                                navigate(target)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.drawer_master_data),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    if (canOpen("المواد والأصناف")) NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_items)) },
                        icon = { NavIcon(Icons.Filled.List, selected = page == "المواد والأصناف") },
                        selected = page == "المواد والأصناف",
                        onClick = {
                            navigate("المواد والأصناف")
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                    if (canOpen("الوحدات")) NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_units)) },
                        icon = { NavIcon(Icons.Filled.Settings, selected = page == "الوحدات") },
                        selected = page == "الوحدات",
                        onClick = {
                            navigate("الوحدات")
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(destinationTitleRes(page)), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                stringResource(R.string.brand_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.semantics { contentDescription = openMenuDescription },
                        ) {
                            NavIcon(Icons.Filled.Menu, selected = true)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.semantics { contentDescription = themeToggleDescription },
                        ) {
                            Text(
                                stringResource(if (darkTheme) R.string.theme_light else R.string.theme_dark),
                                maxLines = 1,
                            )
                        }
                        FushUserAvatar(user.displayName, Modifier.padding(horizontal = 4.dp))
                        TextButton(onClick = onLogout) { Text(stringResource(R.string.common_logout)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            bottomBar = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 840.dp) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            tonalElevation = 2.dp,
                        ) {
                            listOf(
                                Triple("الرئيسية", Icons.Filled.Home, R.string.nav_home),
                                Triple("المبيعات", Icons.Filled.ShoppingCart, R.string.nav_sales),
                                Triple("الإنتاج", Icons.Filled.Build, R.string.nav_production),
                                Triple("المخزون", Icons.Filled.List, R.string.nav_inventory),
                            ).filter { canOpen(it.first) }.forEach { (target, icon, labelRes) ->
                                NavigationBarItem(
                                    selected = page == target,
                                    onClick = { navigate(target) },
                                    icon = { NavIcon(icon, selected = page == target) },
                                    label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = { scope.launch { drawerState.open() } },
                                icon = { NavIcon(Icons.Filled.Menu) },
                                label = { Text(stringResource(R.string.common_menu), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            CompositionLocalProvider(LocalFushSnackbar provides showSnackbar) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
                if (maxWidth >= 840.dp) {
                    Row(Modifier.fillMaxSize()) {
                        FushPrimaryNavigationRail(
                            page = page,
                            canOpen = { canOpen(it) },
                            onNavigate = { navigate(it) },
                            onOpenMenu = { scope.launch { drawerState.open() } }
                        )
                        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                        HomePageContent(
                            container = container,
                            user = user,
                            page = page,
                            modules = visibleModules,
                            onNavigate = { navigate(it) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else {
                    HomePageContent(
                        container = container,
                        user = user,
                        page = page,
                        modules = visibleModules,
                        onNavigate = { navigate(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            }
        }
    }
}


@Composable
private fun FushPrimaryNavigationRail(
    page: String,
    canOpen: (String) -> Boolean,
    onNavigate: (String) -> Unit,
    onOpenMenu: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("الرئيسية", Icons.Filled.Home, R.string.nav_home),
            Triple("المبيعات", Icons.Filled.ShoppingCart, R.string.nav_sales),
            Triple("الإنتاج", Icons.Filled.Build, R.string.nav_production),
            Triple("المخزون", Icons.Filled.List, R.string.nav_inventory),
            Triple("التقارير", Icons.Filled.List, R.string.nav_reports)
        ).filter { canOpen(it.first) }.forEach { (target, icon, labelRes) ->
            NavigationRailItem(
                selected = page == target,
                onClick = { onNavigate(target) },
                icon = { NavIcon(icon, selected = page == target) },
                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = false,
            onClick = onOpenMenu,
            icon = { NavIcon(Icons.Filled.Menu) },
            label = { Text(stringResource(R.string.common_menu), style = MaterialTheme.typography.labelSmall) },
            alwaysShowLabel = true
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomePageContent(
    container: AppContainer,
    user: UserEntity,
    page: String,
    modules: List<ModuleCard>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (page) {
        "المبيعات" -> SalesScreen(container, user, modifier)
        "العملاء" -> CustomersScreen(container, user, modifier)
        "التحصيلات" -> CollectionsDetailScreen(container, modifier)
        "الإنتاج" -> ProductionScreen(container, user, modifier, onNavigate = onNavigate)
        "الصيانة" -> MaintenanceScreen(container, user, modifier)
        "الموظفون" -> EmployeesScreen(container, user, modifier)
        "مناديب المبيعات" -> SalesRepresentativesScreen(container, user, modifier)
        "المشتريات" -> PurchasesScreen(container, user, modifier)
        "الموردون" -> SuppliersScreen(container, user, modifier)
        "المخزون" -> AdvancedInventoryScreen(container, user, modifier, onOpenMasterData = { onNavigate("المواد والأصناف") })
        "الحوكمة" -> GovernanceScreen(container, user, modifier)
        "العملات" -> CurrencyGeographyScreen(container, user, modifier)
        "الحسابات" -> AccountingScreen(container, user, modifier)
        "التقارير" -> ReportsScreen(container, user, modifier)
        "تقارير الإنتاج" -> ReportsScreen(container, user, modifier, initialTab = "الإنتاج")
        "المخاطر" -> RiskControlScreen(container, user, modifier)
        "التخطيط" -> PlanningScreen(container, user, modifier)
        "النسخ الاحتياطي" -> BackupRestoreScreen(container, user, modifier)
        "المواد والأصناف" -> MasterDataScreen(container, user, modifier, initialSection = "المواد والأصناف")
        "الوحدات" -> MasterDataScreen(container, user, modifier, initialSection = "الوحدات")
        else -> DashboardScreen(container, modules, modifier, onNavigate = onNavigate)
    }
}

@Composable
private fun DashboardScreen(
    container: AppContainer,
    modules: List<ModuleCard>,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val now = remember { System.currentTimeMillis() }
    val periodFrom = remember(now) { now - 30L * 86_400_000L }

    val itemCount by container.db.itemDao().observeCount().collectAsState(initial = 0)
    val supplierCount by container.db.supplierDao().observeCount().collectAsState(initial = 0)
    val purchaseCount by container.db.purchaseDao().observeInvoiceCount().collectAsState(initial = 0)
    val productionCount by container.db.productionDao().observeOrderCount().collectAsState(initial = 0)
    val qcHoldCount by container.db.productionDao().observeQcHoldCount().collectAsState(initial = 0)
    val customerCount by container.db.customerDao().observeCount().collectAsState(initial = 0)
    val salesCount by container.db.salesDao().observeInvoiceCount().collectAsState(initial = 0)
    val receiptCount by container.db.salesDao().observeReceiptCount().collectAsState(initial = 0)
    val overdueCount by container.db.salesDao().observeOverdueInvoiceCount(now).collectAsState(initial = 0)
    val cashSalesPct by container.db.salesDao().observeCashSalesPct().collectAsState(initial = 0.0)

    val openMaintenanceCount by container.db.maintenanceDao().observeOpenWorkOrderCount().collectAsState(initial = 0)
    val overdueMaintenanceCount by container.db.maintenanceDao().observeOverduePlanCount(now).collectAsState(initial = 0)
    val overdueWorkOrders by container.db.maintenanceDao().observeOverdueWorkOrderCount(now).collectAsState(initial = 0)
    val overdueEquipmentChecks by container.db.maintenanceDao().observeOverdueEquipmentCheckCount(now).collectAsState(initial = 0)
    val openSafetyCount by container.db.maintenanceDao().observeOpenSafetyIncidentCount().collectAsState(initial = 0)

    val employeeCount by container.db.employeeDao().observeActiveEmployeeCount().collectAsState(initial = 0)
    val salesRepCount by container.db.salesRepresentativeDao().observeActiveCount().collectAsState(initial = 0)
    val expiredTrainingCount by container.db.employeeDao().observeExpiredTrainingCount(now).collectAsState(initial = 0)
    val expiredAuthorizationCount by container.db.employeeDao().observeExpiredAuthorizationCount(now).collectAsState(initial = 0)

    val lowStockRows by container.db.advancedInventoryDao().observeReorderAlerts(System.currentTimeMillis()).collectAsState(initial = emptyList())
    val expiryRows by container.db.advancedInventoryDao().observeExpiryAlerts(now + 30L * 86_400_000L).collectAsState(initial = emptyList())
    val controlledLots by container.db.advancedInventoryDao().observeControlledLotCount().collectAsState(initial = 0)

    val openRiskCount by container.db.riskControlDao().observeOpenRiskCount().collectAsState(initial = 0)
    val highRiskCount by container.db.riskControlDao().observeHighRiskCount().collectAsState(initial = 0)
    val openExceptionCount by container.db.riskControlDao().observeOpenExceptionCount().collectAsState(initial = 0)
    val overdueExceptionCount by container.db.riskControlDao().observeOverdueExceptionCount(now).collectAsState(initial = 0)
    val pendingApprovalCount by container.db.governanceDao().observePendingApprovalCount().collectAsState(initial = 0)
    val openChangeCount by container.db.governanceDao().observeOpenChangeCount().collectAsState(initial = 0)

    var executive by remember { mutableStateOf<ExecutiveReportRow?>(null) }
    LaunchedEffect(Unit) {
        executive = runCatching { container.db.reportDao().executive(periodFrom, now) }.getOrNull()
    }

    data class AlertRow(
        @StringRes val titleRes: Int,
        @StringRes val detailRes: Int,
        val detailArgs: List<Any>,
        val target: String,
        val critical: Boolean,
    )
    val alerts = buildList {
        if (overdueCount > 0) add(AlertRow(R.string.alert_overdue_receivables, R.string.alert_overdue_receivables_detail, listOf(overdueCount), "المبيعات", true))
        if (qcHoldCount > 0) add(AlertRow(R.string.alert_qc_hold, R.string.alert_qc_hold_detail, listOf(qcHoldCount), "الإنتاج", true))
        if (highRiskCount > 0) add(AlertRow(R.string.alert_high_risk, R.string.alert_high_risk_detail, listOf(highRiskCount), "المخاطر", true))
        if (openSafetyCount > 0) add(AlertRow(R.string.alert_open_safety, R.string.alert_open_safety_detail, listOf(openSafetyCount), "الصيانة", true))
        if (overdueExceptionCount > 0) add(AlertRow(R.string.alert_overdue_control_exceptions, R.string.alert_overdue_control_exceptions_detail, listOf(overdueExceptionCount), "المخاطر", true))
        if (lowStockRows.isNotEmpty()) add(AlertRow(R.string.alert_reorder, R.string.alert_reorder_detail, listOf(lowStockRows.size), "المخزون", false))
        if (expiryRows.isNotEmpty()) add(AlertRow(R.string.alert_near_expiry, R.string.alert_near_expiry_detail, listOf(expiryRows.size), "المخزون", false))
        if (controlledLots > 0) add(AlertRow(R.string.alert_controlled_stock, R.string.alert_controlled_stock_detail, listOf(controlledLots), "المخزون", false))
        if (overdueMaintenanceCount > 0 || overdueWorkOrders > 0) add(AlertRow(R.string.alert_overdue_maintenance, R.string.alert_overdue_maintenance_detail, listOf(overdueMaintenanceCount, overdueWorkOrders), "الصيانة", false))
        if (overdueEquipmentChecks > 0) add(AlertRow(R.string.alert_equipment_checks, R.string.alert_equipment_checks_detail, listOf(overdueEquipmentChecks), "الصيانة", false))
        if (expiredTrainingCount > 0 || expiredAuthorizationCount > 0) add(AlertRow(R.string.alert_employee_qualification, R.string.alert_employee_qualification_detail, listOf(expiredTrainingCount, expiredAuthorizationCount), "الموظفون", false))
        if (pendingApprovalCount > 0) add(AlertRow(R.string.alert_pending_approvals, R.string.alert_pending_approvals_detail, listOf(pendingApprovalCount), "الحوكمة", false))
        if (openChangeCount > 0) add(AlertRow(R.string.alert_open_changes, R.string.alert_open_changes_detail, listOf(openChangeCount), "الحوكمة", false))
        if (openExceptionCount > 0 && overdueExceptionCount == 0) add(AlertRow(R.string.alert_open_control_exceptions, R.string.alert_open_control_exceptions_detail, listOf(openExceptionCount), "المخاطر", false))
    }
    val criticalCount = alerts.count { it.critical }
    val readiness = when {
        criticalCount > 0 -> stringResource(R.string.common_needs_action)
        alerts.isNotEmpty() -> stringResource(R.string.common_follow_up)
        else -> stringResource(R.string.common_stable)
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                title = stringResource(R.string.dashboard_title),
                subtitle = stringResource(R.string.dashboard_subtitle),
            )
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.dashboard_executive_status), style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric(stringResource(R.string.dashboard_readiness), readiness, Modifier.weight(1f))
                        Metric(stringResource(R.string.dashboard_alerts), alerts.size.toString(), Modifier.weight(1f))
                        Metric(stringResource(R.string.dashboard_critical), criticalCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            FushSectionHeader(stringResource(R.string.dashboard_kpis), stringResource(R.string.dashboard_kpis_subtitle))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutiveMetric(stringResource(R.string.metric_net_sales), executive?.let { it.grossSalesBase - it.salesReturnsBase }, stringResource(R.string.currency_yer), Modifier.weight(1f)) { onNavigate("المبيعات") }
                ExecutiveMetric(stringResource(R.string.metric_net_collections), executive?.collectionsBase, stringResource(R.string.currency_yer), Modifier.weight(1f)) { onNavigate("التحصيلات") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutiveMetric(stringResource(R.string.metric_receivables), executive?.receivablesBase, stringResource(R.string.currency_yer), Modifier.weight(1f)) { onNavigate("المبيعات") }
                ExecutiveMetric(stringResource(R.string.metric_inventory), executive?.inventoryValueBase, stringResource(R.string.currency_yer), Modifier.weight(1f)) { onNavigate("المخزون") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_cash_ratio), "%.0f".format(cashSalesPct), Modifier.weight(1f)) { onNavigate("المبيعات") }
                Metric(stringResource(R.string.metric_scrap), executive?.scrapQtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) { onNavigate("الإنتاج") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_accepted_60), executive?.accepted60QtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) { onNavigate("الإنتاج") }
                Metric(stringResource(R.string.metric_accepted_200), executive?.accepted200QtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) { onNavigate("الإنتاج") }
            }
        }

        item {
            FushSectionHeader(stringResource(R.string.dashboard_decisions_alerts), stringResource(R.string.dashboard_decisions_alerts_subtitle))
            if (alerts.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    FushInlineState(stringResource(R.string.dashboard_no_pending_alerts), modifier = Modifier.padding(8.dp), tone = FushStatusTone.Success)
                }
            }
        }
        items(alerts) { alert ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (alert.critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(alert.titleRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        FushStatusPill(
                            if (alert.critical) stringResource(R.string.common_critical) else stringResource(R.string.common_follow_up),
                            if (alert.critical) FushStatusTone.Danger else FushStatusTone.Warning,
                        )
                    }
                    Text(stringResource(alert.detailRes, *alert.detailArgs.toTypedArray()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onNavigate(alert.target) }) { Text(stringResource(R.string.common_open_section)) }
                }
            }
        }

        item {
            FushSectionHeader(stringResource(R.string.dashboard_system_pulse), stringResource(R.string.dashboard_system_pulse_subtitle))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_customers), customerCount.toString(), Modifier.weight(1f)) { onNavigate("العملاء") }
                Metric(stringResource(R.string.metric_sales_invoices), salesCount.toString(), Modifier.weight(1f)) { onNavigate("المبيعات") }
                Metric(stringResource(R.string.metric_collections), receiptCount.toString(), Modifier.weight(1f)) { onNavigate("المبيعات") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_suppliers), supplierCount.toString(), Modifier.weight(1f)) { onNavigate("الموردون") }
                Metric(stringResource(R.string.metric_purchases), purchaseCount.toString(), Modifier.weight(1f)) { onNavigate("المشتريات") }
                Metric(stringResource(R.string.metric_production_orders), productionCount.toString(), Modifier.weight(1f)) { onNavigate("الإنتاج") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_items), itemCount.toString(), Modifier.weight(1f)) { onNavigate("المواد والأصناف") }
                Metric(stringResource(R.string.metric_employees), employeeCount.toString(), Modifier.weight(1f)) { onNavigate("الموظفون") }
                Metric(stringResource(R.string.metric_sales_reps), salesRepCount.toString(), Modifier.weight(1f)) { onNavigate("مناديب المبيعات") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_open_maintenance), openMaintenanceCount.toString(), Modifier.weight(1f)) { onNavigate("الصيانة") }
                Metric(stringResource(R.string.metric_open_risks), openRiskCount.toString(), Modifier.weight(1f)) { onNavigate("المخاطر") }
                Metric(stringResource(R.string.metric_approvals), pendingApprovalCount.toString(), Modifier.weight(1f)) { onNavigate("الحوكمة") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.metric_expired_training), expiredTrainingCount.toString(), Modifier.weight(1f)) { onNavigate("الموظفون") }
                Metric(stringResource(R.string.metric_expired_authorizations), expiredAuthorizationCount.toString(), Modifier.weight(1f)) { onNavigate("الموظفون") }
                Metric(stringResource(R.string.metric_overdue_exceptions), overdueExceptionCount.toString(), Modifier.weight(1f)) { onNavigate("المخاطر") }
            }
        }

        item { FushSectionHeader(stringResource(R.string.dashboard_system_modules), stringResource(R.string.dashboard_system_modules_subtitle)) }
        items(modules) { module ->
            FushModuleCard(
                title = stringResource(module.titleRes),
                subtitle = stringResource(module.subtitleRes),
                badge = module.badgeRes?.let { stringResource(it) },
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate(module.target) },
            )
        }
    }
}

@Composable
private fun ExecutiveMetric(
    label: String,
    value: Double?,
    suffix: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    FushMetricCard(
        label = label,
        value = value?.let { java.lang.String.format(java.util.Locale.US, "%,.0f", it) } ?: "—",
        helper = suffix,
        modifier = modifier,
        tone = FushStatusTone.Info,
        onClick = onClick,
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    FushMetricCard(
        label = label,
        value = value,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    selected: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .clearAndSetSemantics {},
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@StringRes
private fun destinationTitleRes(target: String): Int = when (target) {
    "الرئيسية" -> R.string.nav_home
    "المبيعات" -> R.string.nav_sales
    "العملاء" -> R.string.nav_customers
    "التحصيلات" -> R.string.nav_collections
    "الإنتاج" -> R.string.nav_production_quality
    "تقارير الإنتاج" -> R.string.nav_production_reports
    "التخطيط" -> R.string.nav_planning_seasonality
    "النسخ الاحتياطي" -> R.string.nav_backup_restore
    "المخزون" -> R.string.nav_inventory_warehouses
    "المشتريات" -> R.string.nav_purchases
    "الموردون" -> R.string.nav_suppliers
    "الحسابات" -> R.string.nav_accounting_treasury
    "العملات" -> R.string.nav_currencies_governorates
    "الموظفون" -> R.string.nav_employees
    "مناديب المبيعات" -> R.string.nav_sales_reps
    "الصيانة" -> R.string.nav_maintenance_safety
    "الحوكمة" -> R.string.nav_governance_audit
    "التقارير" -> R.string.nav_comprehensive_reports
    "المخاطر" -> R.string.nav_risks_controls
    "المواد والأصناف" -> R.string.nav_items
    "الوحدات" -> R.string.nav_units
    else -> R.string.brand_name
}

private fun destinationIcon(target: String): ImageVector = when (target) {
    "الرئيسية" -> Icons.Filled.Home
    "المبيعات", "المشتريات" -> Icons.Filled.ShoppingCart
    "العملاء", "الموردون", "الموظفون" -> Icons.Filled.Person
    "مناديب المبيعات" -> Icons.Filled.AccountCircle
    "الإنتاج", "الصيانة" -> Icons.Filled.Build
    "التخطيط" -> Icons.Filled.DateRange
    "النسخ الاحتياطي" -> Icons.Filled.Share
    "المخزون", "التقارير", "المواد والأصناف" -> Icons.Filled.List
    "الحسابات" -> Icons.Filled.AccountBox
    "العملات" -> Icons.Filled.LocationOn
    "الحوكمة" -> Icons.Filled.CheckCircle
    "المخاطر" -> Icons.Filled.Warning
    "الوحدات" -> Icons.Filled.Settings
    else -> Icons.Filled.List
}


@Composable
private fun AccountsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("دليل الحسابات الأساسي", style = MaterialTheme.typography.headlineSmall) }
        item { Text("فواتير الشراء تنشئ القيود آلياً: مخزون / صندوق أو موردون.", style = MaterialTheme.typography.bodyMedium) }
        items(accounts) { a ->
            ListItem(
                headlineContent = { Text("${a.code} — ${a.nameAr}") },
                supportingContent = { Text("${a.nameEn} • ${a.type}") }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun InventoryScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val balances by container.db.stockDao().observeBalances().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())
    var showOpening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("أرصدة المخزون", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showOpening = true }) { Text("إدخال رصيد افتتاحي") }
            Spacer(Modifier.height(6.dp))
            Text("الرصيد محسوب من حركات المخزون الفعلية؛ الشراء يزيد والمرتجع ينقص.")
            FushOperationMessage(message, onConsumed = { message = null })
        }
        items(balances) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(row.nameAr) },
                    supportingContent = { Text(row.code) },
                    trailingContent = { Text("${formatQty(row.quantityBase)} ${row.baseUnitName}") }
                )
            }
        }
    }

    if (showOpening) {
        OpeningStockDialog(itemsList, warehouses, onDismiss = { showOpening = false }) { item, warehouse, qty, cost, note ->
            scope.launch {
                try {
                    val entryId = container.inventoryService.postOpeningStock(warehouse.id, item.id, qty, cost, user.id, note)
                    message = "تم ترحيل الرصيد الافتتاحي والقيد رقم $entryId"
                    showOpening = false
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إدخال الرصيد الافتتاحي"
                }
            }
        }
    }
}

@Composable
private fun OpeningStockDialog(
    items: List<ItemEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSave: (ItemEntity, WarehouseEntity, Double, Double, String) -> Unit
) {
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(items) { if (item == null) item = items.firstOrNull() }
    LaunchedEffect(warehouses) { if (warehouse == null) warehouse = warehouses.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رصيد مخزون افتتاحي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل الكمية بالوحدة الأساسية للصنف. ينشئ النظام قيد مخزون مقابل حساب الرصيد الافتتاحي.")
                SelectionField("الصنف", item?.nameAr ?: "اختر", items, { it.nameAr }) { item = it }
                SelectionField("المخزن", warehouse?.nameAr ?: "اختر", warehouses, { it.nameAr }) { warehouse = it }
                OutlinedTextField(qtyText, { qtyText = it }, label = { Text("الكمية بالوحدة الأساسية") }, singleLine = true)
                OutlinedTextField(costText, { costText = it }, label = { Text("تكلفة الوحدة بالريال الجديد") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
            }
        },
        confirmButton = {
            Button(
                enabled = item != null && warehouse != null && qtyText.toDoubleOrNull() != null && costText.toDoubleOrNull() != null,
                onClick = { onSave(item!!, warehouse!!, qtyText.toDouble(), costText.toDouble(), note) }
            ) { Text("ترحيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun MasterDataScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, initialSection: String = "المواد والأصناف") {
    val scope = rememberCoroutineScope()
    val units by container.db.unitDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val conversions by container.db.itemUnitConversionDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val activeUnits = units.filter { it.isActive }
    val activeItems = itemsList.filter { it.isActive }
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var showItem by remember { mutableStateOf(false) }
    var showUnit by remember { mutableStateOf(false) }
    var showWarehouse by remember { mutableStateOf(false) }
    var showConversion by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ItemEntity?>(null) }
    var editUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var editWarehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var editConversion by remember { mutableStateOf<ItemUnitConversionEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    fun runAction(block: suspend () -> String) {
        scope.launch {
            try { message = block() } catch (e: Exception) { message = e.message ?: "تعذر تنفيذ العملية" }
        }
    }

    val query = search.trim().lowercase()
    val filteredItems = remember(itemsList, query) {
        if (query.isBlank()) itemsList else itemsList.filter { item ->
            listOf(item.nameAr, item.nameEn, item.code, itemCategoryAr(item.category)).any { it.lowercase().contains(query) }
        }
    }
    val filteredUnits = remember(units, query) {
        if (query.isBlank()) units else units.filter { unit -> listOf(unit.nameAr, unit.nameEn, unit.code).any { it.lowercase().contains(query) } }
    }
    val filteredWarehouses = remember(warehouses, query) {
        if (query.isBlank()) warehouses else warehouses.filter { warehouse -> listOf(warehouse.nameAr, warehouse.nameEn, warehouse.code, warehouse.location).any { it.lowercase().contains(query) } }
    }
    val filteredConversions = remember(conversions, itemsList, units, query) {
        if (query.isBlank()) conversions else conversions.filter { conversion ->
            val item = itemsList.firstOrNull { it.id == conversion.itemId }
            val unit = units.firstOrNull { it.id == conversion.unitId }
            listOf(item?.nameAr.orEmpty(), item?.code.orEmpty(), unit?.nameAr.orEmpty(), unit?.code.orEmpty(), conversion.barcode.orEmpty())
                .any { it.lowercase().contains(query) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FushSectionHeader(
            title = "الأصناف والوحدات والمخازن",
            subtitle = "إدارة البيانات الأساسية وحالات التفعيل وتحويلات الوحدات",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FushMetricCard("الأصناف النشطة", activeItems.size.toString(), Modifier.weight(1f), "من ${itemsList.size} صنف", FushStatusTone.Info)
            FushMetricCard("الوحدات النشطة", activeUnits.size.toString(), Modifier.weight(1f), "من ${units.size} وحدة", FushStatusTone.Neutral)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FushMetricCard("المخازن النشطة", warehouses.count { it.isActive }.toString(), Modifier.weight(1f), "من ${warehouses.size} مخزن", FushStatusTone.Success)
            FushMetricCard("تحويلات الوحدات", conversions.count { it.isActive }.toString(), Modifier.weight(1f), "تحويل نشط", FushStatusTone.Info)
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                "كل إنشاء أو تعديل أو تفعيل/إيقاف مسجل في التدقيق. لا يمكن إيقاف مخزن أو صنف له رصيد قائم.",
                Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = section == "المواد والأصناف", onClick = { section = "المواد والأصناف"; search = "" }, label = { Text("المواد") }, modifier = Modifier.weight(1f))
            FilterChip(selected = section == "الوحدات", onClick = { section = "الوحدات"; search = "" }, label = { Text(stringResource(R.string.nav_units)) }, modifier = Modifier.weight(1f))
            FilterChip(selected = section == "المخازن", onClick = { section = "المخازن"; search = "" }, label = { Text("المخازن") }, modifier = Modifier.weight(1f))
            FilterChip(selected = section == "التحويلات", onClick = { section = "التحويلات"; search = "" }, label = { Text("التحويلات") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        when (section) {
            "المواد والأصناف" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { showItem = true }, modifier = Modifier.weight(1f), enabled = activeUnits.isNotEmpty()) { Text("إضافة صنف") }
                OutlinedButton(onClick = { showConversion = true }, modifier = Modifier.weight(1f), enabled = activeItems.isNotEmpty() && activeUnits.isNotEmpty()) { Text("إضافة تحويل") }
            }
            "الوحدات" -> Button(onClick = { showUnit = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة وحدة جديدة") }
            "المخازن" -> Button(onClick = { showWarehouse = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة مخزن جديد") }
            "التحويلات" -> Button(onClick = { showConversion = true }, modifier = Modifier.fillMaxWidth(), enabled = activeItems.isNotEmpty() && activeUnits.isNotEmpty()) { Text("إضافة تحويل وحدة") }
        }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("بحث") },
            placeholder = { Text("الاسم، الكود، الموقع أو الباركود") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        FushOperationMessage(message, onConsumed = { message = null })
        Spacer(Modifier.height(7.dp))


            }
        }
            when (section) {
                "الوحدات" -> {
                    item { FushSectionHeader("الوحدات", "${filteredUnits.size} من ${units.size} وحدة") }
                    if (filteredUnits.isEmpty()) item { FushInlineState("لا توجد وحدات مطابقة للبحث.") }
                    items(filteredUnits) { unit ->
                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(unit.nameAr, style = MaterialTheme.typography.titleMedium)
                                        Text("${unit.code}${if (unit.nameEn.isNotBlank()) " • ${unit.nameEn}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FushStatusPill(if (unit.isActive) "نشطة" else "موقوفة", if (unit.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editUnit = unit }) { Text("تعديل") }
                                    TextButton(onClick = { runAction { container.masterDataService.setUnitActive(unit.id, !unit.isActive, user.id); if (unit.isActive) "تم إيقاف الوحدة" else "تم تفعيل الوحدة" } }) { Text(if (unit.isActive) "إيقاف" else "تفعيل") }
                                }
                            }
                        }
                    }
                }
                "المخازن" -> {
                    item { FushSectionHeader("المخازن", "${filteredWarehouses.size} من ${warehouses.size} مخزن") }
                    if (filteredWarehouses.isEmpty()) item { FushInlineState("لا توجد مخازن مطابقة للبحث.") }
                    items(filteredWarehouses) { warehouse ->
                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(warehouse.nameAr, style = MaterialTheme.typography.titleMedium)
                                        Text("${warehouse.code}${if (warehouse.location.isNotBlank()) " • ${warehouse.location}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FushStatusPill(if (warehouse.isActive) "نشط" else "موقوف", if (warehouse.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editWarehouse = warehouse }) { Text("تعديل") }
                                    TextButton(onClick = { runAction { container.masterDataService.setWarehouseActive(warehouse.id, !warehouse.isActive, user.id); if (warehouse.isActive) "تم إيقاف المخزن" else "تم تفعيل المخزن" } }) { Text(if (warehouse.isActive) "إيقاف" else "تفعيل") }
                                }
                            }
                        }
                    }
                }
                "التحويلات" -> {
                    item { FushSectionHeader("تحويلات الوحدات", "${filteredConversions.size} من ${conversions.size} تحويل") }
                    if (filteredConversions.isEmpty()) item { FushInlineState("لا توجد تحويلات وحدات مطابقة للبحث.") }
                    items(filteredConversions) { conversion ->
                        val item = itemsList.firstOrNull { it.id == conversion.itemId }
                        val unit = units.firstOrNull { it.id == conversion.unitId }
                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${item?.nameAr ?: "صنف #${conversion.itemId}"} — ${unit?.nameAr ?: "وحدة #${conversion.unitId}"}", style = MaterialTheme.typography.titleMedium)
                                        Text("× ${formatQty(conversion.factorToBase)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FushStatusPill(if (conversion.isActive) "نشط" else "موقوف", if (conversion.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (conversion.allowPurchase) FushStatusPill("شراء", FushStatusTone.Info)
                                    if (conversion.allowSale) FushStatusPill("بيع", FushStatusTone.Info)
                                    conversion.barcode?.takeIf { it.isNotBlank() }?.let { FushStatusPill("باركود $it", FushStatusTone.Neutral) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editConversion = conversion }) { Text("تعديل") }
                                    TextButton(onClick = { runAction { container.masterDataService.setConversionActive(conversion.id, !conversion.isActive, user.id); if (conversion.isActive) "تم إيقاف التحويل" else "تم تفعيل التحويل" } }) { Text(if (conversion.isActive) "إيقاف" else "تفعيل") }
                                }
                            }
                        }
                    }
                }
                else -> {
                    item { FushSectionHeader("المواد والأصناف", "${filteredItems.size} من ${itemsList.size} صنف") }
                    if (filteredItems.isEmpty()) item { FushInlineState("لا توجد أصناف مطابقة للبحث.") }
                    items(filteredItems) { item ->
                        val baseUnit = units.firstOrNull { it.id == item.baseUnitId }
                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.nameAr, style = MaterialTheme.typography.titleMedium)
                                        Text("${item.code} • ${itemCategoryAr(item.category)} • ${baseUnit?.nameAr ?: "وحدة"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FushStatusPill(if (item.isActive) "نشط" else "موقوف", if (item.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FushStatusPill("إعادة طلب ${formatQty(item.reorderLevel)}", if (item.reorderLevel > 0) FushStatusTone.Info else FushStatusTone.Neutral)
                                    item.shelfLifeDays?.let { FushStatusPill("صلاحية $it يوم", FushStatusTone.Warning) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editItem = item }) { Text("تعديل") }
                                    TextButton(onClick = { runAction { container.masterDataService.setItemActive(item.id, !item.isActive, user.id); if (item.isActive) "تم إيقاف الصنف" else "تم تفعيل الصنف" } }) { Text(if (item.isActive) "إيقاف" else "تفعيل") }
                                }
                            }
                        }
                    }
                }
            }
    }

    if (showItem) {
        AddItemDialog(units = activeUnits, onDismiss = { showItem = false }) { nameAr, nameEn, category, baseUnit, reorder, shelfLife, lotTracked, expiryTracked ->
            runAction {
                val item = container.masterDataService.createItem(nameAr, nameEn, category, baseUnit.id, reorder, shelfLife, lotTracked, expiryTracked, user.id)
                showItem = false
                "تمت إضافة ${item.nameAr} بالكود ${item.code}"
            }
        }
    }

    if (showUnit) {
        AddUnitDialog(onDismiss = { showUnit = false }) { nameAr, nameEn ->
            runAction {
                val unit = container.masterDataService.createUnit(nameAr, nameEn, user.id)
                showUnit = false
                "تمت إضافة الوحدة بالكود ${unit.code}"
            }
        }
    }

    if (showWarehouse) {
        AddWarehouseDialog(onDismiss = { showWarehouse = false }) { nameAr, nameEn, location ->
            runAction {
                val warehouse = container.masterDataService.createWarehouse(nameAr, nameEn, location, user.id)
                showWarehouse = false
                "تمت إضافة المخزن بالكود ${warehouse.code}"
            }
        }
    }

    if (showConversion) {
        AddConversionDialog(activeItems, activeUnits, onDismiss = { showConversion = false }) { item, unit, factor, purchase, sale, barcode ->
            runAction {
                container.masterDataService.saveConversion(null, item.id, unit.id, factor, purchase, sale, barcode, true, user.id)
                showConversion = false
                "تم حفظ تحويل الوحدة"
            }
        }
    }

    editItem?.let { current ->
        EditItemDialog(current, units, onDismiss = { editItem = null }) { nameAr, nameEn, reorder, shelfLife, lotTracked, expiryTracked ->
            runAction {
                val updated = container.masterDataService.updateItem(current.id, nameAr, nameEn, reorder, shelfLife, lotTracked, expiryTracked, user.id)
                editItem = null
                "تم تعديل ${updated.nameAr}"
            }
        }
    }

    editUnit?.let { current ->
        EditUnitDialog(current, onDismiss = { editUnit = null }) { nameAr, nameEn ->
            runAction {
                container.masterDataService.updateUnit(current.id, nameAr, nameEn, user.id)
                editUnit = null
                "تم تعديل الوحدة"
            }
        }
    }

    editWarehouse?.let { current ->
        EditWarehouseDialog(current, onDismiss = { editWarehouse = null }) { nameAr, nameEn, location ->
            runAction {
                container.masterDataService.updateWarehouse(current.id, nameAr, nameEn, location, user.id)
                editWarehouse = null
                "تم تعديل المخزن"
            }
        }
    }

    editConversion?.let { current ->
        val item = itemsList.firstOrNull { it.id == current.itemId }
        val unit = units.firstOrNull { it.id == current.unitId }
        if (item != null && unit != null) {
            EditConversionDialog(current, item, unit, onDismiss = { editConversion = null }) { factor, purchase, sale, barcode ->
                runAction {
                    container.masterDataService.saveConversion(current.id, current.itemId, current.unitId, factor, purchase, sale, barcode, current.isActive, user.id)
                    editConversion = null
                    "تم تعديل تحويل الوحدة"
                }
            }
        }
    }
}

@Composable
private fun EditItemDialog(
    item: ItemEntity,
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Int?, Boolean, Boolean) -> Unit
) {
    var nameAr by remember(item.id) { mutableStateOf(item.nameAr) }
    var nameEn by remember(item.id) { mutableStateOf(item.nameEn) }
    var reorderText by remember(item.id) { mutableStateOf(item.reorderLevel.toString()) }
    var shelfText by remember(item.id) { mutableStateOf(item.shelfLifeDays?.toString() ?: "") }
    var lotTracked by remember(item.id) { mutableStateOf(item.lotTracked) }
    var expiryTracked by remember(item.id) { mutableStateOf(item.expiryTracked) }
    val baseUnit = units.firstOrNull { it.id == item.baseUnitId }
    val finishedGood = item.category == "FINISHED_GOOD"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل مادة/صنف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الكود: ${item.code} — لا يتغير بعد إنشاء الصنف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("الفئة: ${itemCategoryAr(item.category)} • الوحدة الأساسية: ${baseUnit?.nameAr ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("الفئة والوحدة الأساسية محميتان حتى لا تتأثر الحركات السابقة وتحويلات الوحدات.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("الاسم العربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم الإنجليزي") }, singleLine = true)
                OutlinedTextField(reorderText, { reorderText = it }, label = { Text("حد إعادة الطلب") }, singleLine = true)
                OutlinedTextField(shelfText, { shelfText = it.filter(Char::isDigit) }, label = { Text("مدة الصلاحية بالأيام - اختياري") }, singleLine = true)
                Row {
                    Checkbox(checked = lotTracked, onCheckedChange = { if (!finishedGood) lotTracked = it }, enabled = !finishedGood)
                    Text(if (finishedGood) "تتبع التشغيلة — إلزامي للمنتج النهائي" else "تتبع التشغيلة")
                }
                Row {
                    Checkbox(checked = expiryTracked, onCheckedChange = { if (!finishedGood) expiryTracked = it }, enabled = !finishedGood)
                    Text(if (finishedGood) "تتبع الصلاحية — إلزامي للمنتج النهائي" else "تتبع الصلاحية")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = nameAr.isNotBlank() && reorderText.toDoubleOrNull() != null && (shelfText.isBlank() || shelfText.toIntOrNull() != null),
                onClick = { onSave(nameAr, nameEn, reorderText.toDouble(), shelfText.toIntOrNull(), lotTracked, expiryTracked) }
            ) { Text("حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditUnitDialog(
    unit: UnitEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var nameAr by remember(unit.id) { mutableStateOf(unit.nameAr) }
    var nameEn by remember(unit.id) { mutableStateOf(unit.nameEn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الوحدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الكود: ${unit.code} — يبقى ثابتاً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم الوحدة بالعربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("اسم الوحدة بالإنجليزي") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = nameAr.isNotBlank(), onClick = { onSave(nameAr, nameEn) }) { Text("حفظ التعديل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditWarehouseDialog(
    warehouse: WarehouseEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var nameAr by remember(warehouse.id) { mutableStateOf(warehouse.nameAr) }
    var nameEn by remember(warehouse.id) { mutableStateOf(warehouse.nameEn) }
    var location by remember(warehouse.id) { mutableStateOf(warehouse.location) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المخزن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الكود: ${warehouse.code} — يبقى ثابتاً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم المخزن بالعربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("اسم المخزن بالإنجليزي") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("الموقع") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = nameAr.isNotBlank(), onClick = { onSave(nameAr, nameEn, location) }) { Text("حفظ التعديل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddWarehouseDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مخزن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كود المخزن: تلقائي عند الحفظ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم المخزن بالعربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("اسم المخزن بالإنجليزي - اختياري") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("الموقع - اختياري") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = nameAr.isNotBlank(), onClick = { onSave(nameAr, nameEn, location) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddItemDialog(
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, UnitEntity, Double, Int?, Boolean, Boolean) -> Unit
) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("RAW_MATERIAL") }
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var reorderText by remember { mutableStateOf("0") }
    var shelfText by remember { mutableStateOf("") }
    var lotTracked by remember { mutableStateOf(false) }
    var expiryTracked by remember { mutableStateOf(false) }
    LaunchedEffect(units) { if (unit == null) unit = units.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مادة/صنف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الكود: تلقائي عند الحفظ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("الاسم العربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم الإنجليزي - اختياري") }, singleLine = true)
                StringSelectionField("الفئة", itemCategoryAr(category), listOf("RAW_MATERIAL", "PACKAGING", "FINISHED_GOOD")) { category = it }
                SelectionField("الوحدة الأساسية", unit?.nameAr ?: "اختر", units, { it.nameAr }) { unit = it }
                OutlinedTextField(reorderText, { reorderText = it }, label = { Text("حد إعادة الطلب") }, singleLine = true)
                OutlinedTextField(shelfText, { shelfText = it.filter(Char::isDigit) }, label = { Text("مدة الصلاحية بالأيام - اختياري") }, singleLine = true)
                Row { Checkbox(lotTracked, { lotTracked = it }); Text("تتبع التشغيلة") }
                Row { Checkbox(expiryTracked, { expiryTracked = it }); Text("تتبع الصلاحية") }
            }
        },
        confirmButton = {
            Button(
                enabled = nameAr.isNotBlank() && unit != null && reorderText.toDoubleOrNull() != null,
                onClick = { onSave(nameAr, nameEn, category, unit!!, reorderText.toDouble(), shelfText.toIntOrNull(), lotTracked, expiryTracked) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddUnitDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة وحدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كود الوحدة: تلقائي عند الحفظ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم الوحدة بالعربي") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("اسم الوحدة بالإنجليزي - اختياري") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = nameAr.isNotBlank(), onClick = { onSave(nameAr, nameEn) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun itemCategoryAr(category: String): String = when (category) {
    "RAW_MATERIAL" -> "مواد خام"
    "PACKAGING" -> "مواد تغليف"
    "FINISHED_GOOD" -> "منتج نهائي"
    else -> category
}

@Composable
private fun AddConversionDialog(
    items: List<ItemEntity>,
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onSave: (ItemEntity, UnitEntity, Double, Boolean, Boolean, String) -> Unit
) {
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var factorText by remember { mutableStateOf("1") }
    var purchase by remember { mutableStateOf(true) }
    var sale by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    LaunchedEffect(items) { if (item == null) item = items.firstOrNull() }
    LaunchedEffect(units) { if (unit == null) unit = units.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحويل وحدة للصنف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الصنف", item?.nameAr ?: "اختر", items, { it.nameAr }) { item = it }
                SelectionField("الوحدة", unit?.nameAr ?: "اختر", units, { it.nameAr }) { unit = it }
                OutlinedTextField(factorText, { factorText = it }, label = { Text("كم وحدة أساسية؟") }, singleLine = true)
                OutlinedTextField(barcode, { barcode = it }, label = { Text("الباركود - اختياري") }, singleLine = true)
                Row { Checkbox(purchase, { purchase = it }); Text("مسموحة للشراء") }
                Row { Checkbox(sale, { sale = it }); Text("مسموحة للبيع") }
            }
        },
        confirmButton = {
            Button(enabled = item != null && unit != null && factorText.toDoubleOrNull() != null, onClick = {
                onSave(item!!, unit!!, factorText.toDouble(), purchase, sale, barcode)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditConversionDialog(
    conversion: ItemUnitConversionEntity,
    item: ItemEntity,
    unit: UnitEntity,
    onDismiss: () -> Unit,
    onSave: (Double, Boolean, Boolean, String) -> Unit
) {
    var factorText by remember(conversion.id) { mutableStateOf(conversion.factorToBase.toString()) }
    var purchase by remember(conversion.id) { mutableStateOf(conversion.allowPurchase) }
    var sale by remember(conversion.id) { mutableStateOf(conversion.allowSale) }
    var barcode by remember(conversion.id) { mutableStateOf(conversion.barcode ?: "") }
    val isBase = item.baseUnitId == unit.id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل تحويل الوحدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.nameAr} — ${unit.nameAr}", style = MaterialTheme.typography.titleMedium)
                if (isBase) Text("هذه الوحدة الأساسية؛ معاملها ثابت عند 1 ولا يمكن إيقافها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(factorText, { factorText = it }, enabled = !isBase, label = { Text("كم وحدة أساسية؟") }, singleLine = true)
                OutlinedTextField(barcode, { barcode = it }, label = { Text("الباركود - اختياري") }, singleLine = true)
                Row { Checkbox(purchase, { purchase = it }); Text("مسموحة للشراء") }
                Row { Checkbox(sale, { sale = it }); Text("مسموحة للبيع") }
            }
        },
        confirmButton = {
            Button(enabled = factorText.toDoubleOrNull() != null, onClick = { onSave(factorText.toDouble(), purchase, sale, barcode) }) { Text("حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun <T> SelectionField(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selectedText")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun StringSelectionField(label: String, selectedText: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selectedText") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

private fun formatQty(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.3f".format(value)
