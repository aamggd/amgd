package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.cloud.CloudUserBinding
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.PermissionEntity
import com.fush.erp.data.entity.RoleEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.PasswordPolicy
import com.fush.erp.domain.SecurityPermissions
import com.fush.erp.domain.SessionPolicy
import com.fush.erp.domain.SessionTimeoutSettings
import com.fush.erp.domain.SupportPolicy
import com.fush.erp.ui.FushSearchableSelectionField
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class PendingCriticalAction(
    val label: String,
    val action: suspend () -> Unit
)

@Composable
fun InitialAdminSetupScreen(
    container: AppContainer,
    onCreated: (UserEntity) -> Unit,
    onJoinExisting: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("مدير النظام") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إعداد مدير النظام", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "هذه أول مرة يتم فيها تشغيل النظام. أنشئ حساب المدير الآن. لا توجد كلمة مرور افتراضية أو ثابتة داخل التطبيق.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("اسم المستخدم") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("الاسم الظاهر") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${PasswordPolicy.MIN_LENGTH} حرفًا على الأقل + حرف كبير وصغير + رقم + رمز خاص.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = !busy && username.isNotBlank() && displayName.isNotBlank() &&
                            password.isNotBlank() && password == confirm,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching {
                                    container.securityService.bootstrapFirstAdmin(
                                        username = username,
                                        displayName = displayName,
                                        password = password.toCharArray()
                                    )
                                }.onSuccess(onCreated)
                                    .onFailure { message = it.message ?: "تعذر إنشاء مدير النظام" }
                                password = ""
                                confirm = ""
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إنشاء مدير النظام") }
                    OutlinedButton(
                        onClick = onJoinExisting,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("الانضمام إلى شركة FUSH موجودة") }
                    Text(
                        "استخدم الانضمام السحابي لهاتف المحاسب أو عامل الإنتاج أو المندوب بعد أن ينشئ المدير حسابه السحابي من شاشة المستخدمين.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun InitialCloudJoinScreen(
    container: AppContainer,
    onCreated: (UserEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الانضمام إلى FUSH ERP", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "هذه الشاشة مخصصة لهاتف موظف سبق أن أنشأ له المدير حسابًا سحابيًا. سيتم تنزيل هويته ودوره وربط هذا الهاتف به.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; message = null },
                        label = { Text("البريد الإلكتروني السحابي") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; message = null },
                        label = { Text("كلمة مرور السحابة") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "في أول انضمام فقط ستُستخدم كلمة المرور نفسها لإنشاء حماية الدخول المحلية على هذا الهاتف. يجب أن تحقق سياسة كلمات مرور FUSH.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val joined = container.cloudSyncRepository.joinExistingCompany(email, password)) {
                                    is CloudOperationResult.Failure -> message = joined.message
                                    is CloudOperationResult.Success -> {
                                        val binding = joined.value.binding
                                        runCatching {
                                            val created = container.securityService.bootstrapCloudMember(
                                                username = binding.localUsername,
                                                displayName = binding.displayName ?: binding.localUsername,
                                                role = binding.cloudRole,
                                                password = password.toCharArray(),
                                            )
                                            container.cloudSyncRepository.completeJoinedIdentity(created, joined.value)
                                            created
                                        }.onSuccess { created ->
                                            password = ""
                                            onCreated(created)
                                        }.onFailure {
                                            message = it.message ?: "تعذر إنشاء هوية FUSH المحلية من حساب السحابة"
                                        }
                                    }
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("التحقق والانضمام") }
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("رجوع") }
                }
            }
        }
    }
}

@Composable
fun UsersPermissionsScreen(container: AppContainer, currentUser: UserEntity, modifier: Modifier = Modifier) {
    val users by container.db.userDao().observeAll().collectAsState(initial = emptyList())
    val roles by container.db.securityDao().observeRoles().collectAsState(initial = emptyList())
    val permissions by container.db.securityDao().observePermissions().collectAsState(initial = emptyList())
    val actorPermissions by container.db.securityDao().observePermissionCodesForRole(currentUser.role).collectAsState(initial = emptyList())
    val actorPermissionSet = remember(actorPermissions) { actorPermissions.toSet() }
    fun can(code: String) = currentUser.role == "ADMIN" || code in actorPermissionSet

    var tab by remember { mutableIntStateOf(0) }
    var addUser by remember { mutableStateOf(false) }
    var addRole by remember { mutableStateOf(false) }
    var roleTarget by remember { mutableStateOf<UserEntity?>(null) }
    var resetTarget by remember { mutableStateOf<UserEntity?>(null) }
    var cloudTarget by remember { mutableStateOf<UserEntity?>(null) }
    var cloudRevision by remember { mutableIntStateOf(0) }
    var selectedRole by remember { mutableStateOf<RoleEntity?>(null) }
    var deleteRoleTarget by remember { mutableStateOf<RoleEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingCriticalAction by remember { mutableStateOf<PendingCriticalAction?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(roles.map { it.code }, selectedRole?.code) {
        selectedRole = when {
            roles.isEmpty() -> null
            selectedRole == null -> roles.firstOrNull { it.code == "ADMIN" } ?: roles.first()
            roles.none { it.code == selectedRole?.code } -> roles.firstOrNull { it.code == "ADMIN" } ?: roles.first()
            else -> roles.first { it.code == selectedRole?.code }
        }
    }

    fun runCritical(label: String, action: suspend () -> Unit) {
        scope.launch {
            if (container.securityService.hasRecentReauthentication(currentUser.id)) action()
            else pendingCriticalAction = PendingCriticalAction(label, action)
        }
    }

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("المستخدمون") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الأدوار والصلاحيات") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("سياسة الأمان") })
        }
        message?.let {
            AssistChip(
                onClick = { message = null },
                label = { Text(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        when (tab) {
            0 -> {
                val visibleUsers = users.filter { it.role != SupportPolicy.SUPPORT_ROLE }
                val cloudBindings = remember(visibleUsers, cloudRevision) {
                    visibleUsers.associate { it.id to container.cloudSyncRepository.bindingFor(it) }
                }
                UsersTab(
                    users = visibleUsers,
                    roles = roles.filter { it.code != SupportPolicy.SUPPORT_ROLE },
                    cloudBindings = cloudBindings,
                    canManage = can(SecurityPermissions.USERS_MANAGE),
                    onAdd = { addUser = true },
                    onRole = { roleTarget = it },
                    onReset = { resetTarget = it },
                    onCloud = { cloudTarget = it },
                    onToggleActive = { target, active ->
                    runCritical(if (active) "تفعيل مستخدم" else "تعطيل مستخدم") {
                        message = runCatching {
                            container.securityService.setUserActive(currentUser.id, target.id, active)
                            if (active) "تم تفعيل ${target.displayName}" else "تم تعطيل ${target.displayName}"
                        }.getOrElse { it.message ?: "تعذر تحديث المستخدم" }
                    }
                    }
                )
            }
            1 -> RolesTab(
                roles = roles.filter { it.code != SupportPolicy.SUPPORT_ROLE },
                permissions = permissions,
                canManage = can(SecurityPermissions.ROLES_MANAGE),
                selectedRole = selectedRole,
                onSelectRole = { selectedRole = it },
                onAddRole = { addRole = true },
                onDeleteRole = { deleteRoleTarget = it },
                onSave = { role, codes ->
                    runCritical("تغيير صلاحيات دور") {
                        message = runCatching {
                            container.securityService.saveRolePermissions(currentUser.id, role.code, codes)
                            "تم حفظ صلاحيات ${role.nameAr}"
                        }.getOrElse { it.message ?: "تعذر حفظ الصلاحيات" }
                    }
                },
                loadRolePermissions = { roleCode -> container.db.securityDao().permissionCodesForRole(roleCode).toSet() }
            )
            else -> SecurityPolicyTab(container, currentUser, can(SecurityPermissions.ROLES_MANAGE))
        }
    }

    if (addUser) {
        AddUserDialog(
            roles = roles.filter { it.isActive && it.code != SupportPolicy.SUPPORT_ROLE },
            onDismiss = { addUser = false },
            onSave = { username, displayName, role, password ->
                runCritical("إنشاء مستخدم") {
                    message = runCatching {
                        container.securityService.createUser(currentUser.id, username, displayName, role, password.toCharArray())
                        addUser = false
                        "تم إنشاء المستخدم $displayName. يجب عليه تغيير كلمة المرور عند أول دخول."
                    }.getOrElse { it.message ?: "تعذر إنشاء المستخدم" }
                }
            }
        )
    }

    cloudTarget?.let { target ->
        CloudProvisionDialog(
            target = target,
            existing = container.cloudSyncRepository.bindingFor(target),
            onDismiss = { cloudTarget = null },
            onSave = { email, password ->
                runCritical("إعداد مستخدم السحابة") {
                    message = when (val result = container.cloudSyncRepository.provisionCloudUser(
                        actor = currentUser,
                        target = target,
                        email = email,
                        temporaryPassword = password,
                    )) {
                        is CloudOperationResult.Success -> {
                            cloudRevision++
                            cloudTarget = null
                            if (result.value.requiresEmailConfirmation) {
                                "تم إنشاء وربط حساب ${target.displayName}. يجب تأكيد البريد الإلكتروني قبل أول دخول سحابي."
                            } else {
                                "تم إنشاء وربط حساب ${target.displayName} بالسحابة."
                            }
                        }
                        is CloudOperationResult.Failure -> result.message
                    }
                }
            }
        )
    }

    roleTarget?.let { target ->
        AssignRoleDialog(
            target = target,
            roles = roles.filter { it.isActive && it.code != SupportPolicy.SUPPORT_ROLE },
            onDismiss = { roleTarget = null },
            onSave = { role ->
                runCritical("تغيير دور مستخدم") {
                    message = runCatching {
                        container.securityService.assignRole(currentUser.id, target.id, role)
                        roleTarget = null
                        "تم تغيير دور ${target.displayName}"
                    }.getOrElse { it.message ?: "تعذر تغيير الدور" }
                }
            }
        )
    }

    resetTarget?.let { target ->
        ResetPasswordDialog(
            target = target,
            onDismiss = { resetTarget = null },
            onSave = { password ->
                runCritical("إعادة ضبط كلمة مرور مستخدم") {
                    message = runCatching {
                        container.securityService.resetPassword(currentUser.id, target.id, password.toCharArray())
                        resetTarget = null
                        "تمت إعادة ضبط كلمة المرور. سيُطلب تغييرها عند الدخول القادم."
                    }.getOrElse { it.message ?: "تعذر إعادة ضبط كلمة المرور" }
                }
            }
        )
    }

    if (addRole) {
        AddRoleDialog(
            onDismiss = { addRole = false },
            onSave = { code, name, description ->
                runCritical("إنشاء أو تعديل دور") {
                    message = runCatching {
                        val role = container.securityService.saveCustomRole(currentUser.id, code, name, description)
                        selectedRole = role
                        addRole = false
                        "تم إنشاء الدور ${role.nameAr}. حدد صلاحياته ثم احفظ."
                    }.getOrElse { it.message ?: "تعذر إنشاء الدور" }
                }
            }
        )
    }

    deleteRoleTarget?.let { role ->
        DeleteRoleDialog(
            role = role,
            onDismiss = { deleteRoleTarget = null },
            onConfirm = {
                runCritical("حذف دور") {
                    message = runCatching {
                        container.securityService.deleteRole(currentUser.id, role.code)
                        deleteRoleTarget = null
                        if (selectedRole?.code == role.code) selectedRole = null
                        "تم حذف الدور ${role.nameAr}"
                    }.getOrElse { it.message ?: "تعذر حذف الدور" }
                }
            }
        )
    }

    pendingCriticalAction?.let { pending ->
        ReauthenticationDialog(
            container = container,
            userId = currentUser.id,
            actionLabel = pending.label,
            onDismiss = { pendingCriticalAction = null },
            onVerified = {
                val action = pending.action
                pendingCriticalAction = null
                scope.launch { action() }
            }
        )
    }
}

@Composable
private fun UsersTab(
    users: List<UserEntity>,
    roles: List<RoleEntity>,
    cloudBindings: Map<Long, CloudUserBinding?>,
    canManage: Boolean,
    onAdd: () -> Unit,
    onRole: (UserEntity) -> Unit,
    onReset: (UserEntity) -> Unit,
    onCloud: (UserEntity) -> Unit,
    onToggleActive: (UserEntity, Boolean) -> Unit
) {
    val roleNames = remember(roles) { roles.associate { it.code to it.nameAr } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("إدارة المستخدمين", style = MaterialTheme.typography.headlineSmall)
                    Text("الحسابات منفصلة، وكل مستخدم مرتبط بدور وصلاحيات محددة.", style = MaterialTheme.typography.bodySmall)
                }
                if (canManage) Button(onClick = onAdd) { Text("مستخدم جديد") }
            }
        }
        items(users, key = { it.id }) { user ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("${user.username} • ${roleNames[user.role] ?: user.role}")
                        }
                        AssistChip(onClick = {}, label = { Text(if (user.isActive) "نشط" else "معطل") })
                    }
                    Text(
                        "آخر دخول: ${formatDate(user.lastLoginAt)} • تغيير كلمة المرور: ${formatDate(user.passwordChangedAt)}" +
                            if (user.mustChangePassword) " • مطلوب تغيير كلمة المرور" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    cloudBindings[user.id]?.let { cloud ->
                        Text(
                            "السحابة: ${cloud.email ?: cloud.cloudUserId} • ${cloud.cloudRole}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } ?: Text(
                        "السحابة: غير مربوط",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (user.lockedUntil != null && user.lockedUntil > com.fush.erp.domain.TrustedTimeService.now()) {
                        Text("الحساب مقفل حتى ${formatDate(user.lockedUntil)}", color = MaterialTheme.colorScheme.error)
                    }
                    if (canManage) {
                        FlowRowCompat {
                            TextButton(onClick = { onRole(user) }) { Text("الدور") }
                            TextButton(onClick = { onReset(user) }) { Text("إعادة كلمة المرور") }
                            if (user.role != SupportPolicy.SUPPORT_ROLE) {
                                TextButton(onClick = { onCloud(user) }) { Text("السحابة") }
                            }
                            TextButton(onClick = { onToggleActive(user, !user.isActive) }) {
                                Text(if (user.isActive) "تعطيل" else "تفعيل")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RolesTab(
    roles: List<RoleEntity>,
    permissions: List<PermissionEntity>,
    canManage: Boolean,
    selectedRole: RoleEntity?,
    onSelectRole: (RoleEntity) -> Unit,
    onAddRole: () -> Unit,
    onDeleteRole: (RoleEntity) -> Unit,
    onSave: (RoleEntity, Set<String>) -> Unit,
    loadRolePermissions: suspend (String) -> Set<String>
) {
    var selectedCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(selectedRole?.code) {
        val role = selectedRole ?: return@LaunchedEffect
        loading = true
        selectedCodes = loadRolePermissions(role.code)
        loading = false
    }
    val grouped = remember(permissions) { permissions.groupBy { it.moduleKey } }
    val effectiveSelectedCount = if (selectedRole?.code == "ADMIN") permissions.size else selectedCodes.size

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الأدوار والصلاحيات", style = MaterialTheme.typography.headlineSmall)
                    Text("اختر أي دور لعرض جميع صلاحياته بالتفصيل أو تعديلها.", style = MaterialTheme.typography.bodySmall)
                }
                if (canManage) OutlinedButton(onClick = onAddRole) { Text("دور جديد") }
            }
        }

        item {
            Text("الأدوار", style = MaterialTheme.typography.titleMedium)
            FlowRowCompat {
                roles.forEach { role ->
                    FilterChip(
                        selected = selectedRole?.code == role.code,
                        onClick = { onSelectRole(role) },
                        label = { Text(role.nameAr) }
                    )
                }
            }
        }

        selectedRole?.let { role ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("صلاحيات: ${role.nameAr}", style = MaterialTheme.typography.titleLarge)
                        Text("$effectiveSelectedCount من ${permissions.size} صلاحية ممنوحة", style = MaterialTheme.typography.bodyMedium)
                        if (role.code == "ADMIN") {
                            Text("مدير النظام يحتفظ دائمًا بجميع الصلاحيات ولا يمكن حذف هذا الدور.", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("المربعات المحددة هي الصلاحيات الفعلية لهذا الدور.", style = MaterialTheme.typography.bodySmall)
                            if (canManage && !role.isSystem) {
                                TextButton(onClick = { onDeleteRole(role) }) { Text("حذف هذا الدور") }
                            }
                        }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            grouped.forEach { (module, rows) ->
                item {
                    val grantedInModule = if (role.code == "ADMIN") rows.size else rows.count { it.code in selectedCodes }
                    Text("${moduleLabel(module)} ($grantedInModule/${rows.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(rows, key = { "permission-${role.code}-${it.code}" }) { permission ->
                    val granted = role.code == "ADMIN" || permission.code in selectedCodes
                    ListItem(
                        headlineContent = { Text(permission.nameAr) },
                        supportingContent = {
                            if (permission.description.isNotBlank()) Text(permission.description, style = MaterialTheme.typography.bodySmall)
                            else Text(permission.code, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Checkbox(
                                checked = granted,
                                enabled = canManage && role.code != "ADMIN",
                                onCheckedChange = { checked ->
                                    selectedCodes = if (checked) selectedCodes + permission.code else selectedCodes - permission.code
                                }
                            )
                        },
                        trailingContent = {
                            Text(if (granted) "مسموح" else "غير مسموح", style = MaterialTheme.typography.labelMedium)
                        }
                    )
                    HorizontalDivider()
                }
            }
            item {
                if (canManage && role.code != "ADMIN") {
                    Button(
                        onClick = { onSave(role, selectedCodes) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حفظ صلاحيات الدور") }
                }
            }
        }
    }
}

@Composable
private fun SecurityPolicyTab(container: AppContainer, currentUser: UserEntity, canManage: Boolean) {
    val initial = remember { container.sessionSettings.current() }
    val automaticLogoutEnabled = true
    var idleMinutes by remember { mutableStateOf(initial.idleTimeoutMinutes.toString()) }
    var maxSessionMinutes by remember { mutableStateOf(initial.maxSessionMinutes.toString()) }
    var sessionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("سياسة الأمان", style = MaterialTheme.typography.headlineSmall) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("الإغلاق التلقائي للجلسة", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "مفعّل تلقائيًا — مدة الجلسة يحددها مدير النظام من داخل التطبيق",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = true,
                            enabled = false,
                            onCheckedChange = null
                        )
                    }
                    Text("اختيار سريع لمدة الجلسة", style = MaterialTheme.typography.labelLarge)
                    FlowRowCompat {
                        SessionPolicy.QUICK_SESSION_MINUTES.forEach { minutes ->
                            FilterChip(
                                selected = idleMinutes.toLongOrNull() == minutes && maxSessionMinutes.toLongOrNull() == minutes,
                                onClick = {
                                    idleMinutes = minutes.toString()
                                    maxSessionMinutes = minutes.toString()
                                },
                                enabled = canManage,
                                label = { Text(if (minutes < 60) "$minutes دقيقة" else if (minutes % 60L == 0L) "${minutes / 60} ساعة" else "$minutes دقيقة") },
                                modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = idleMinutes,
                        onValueChange = { idleMinutes = it.filter(Char::isDigit).take(5) },
                        label = { Text("الإغلاق بعد الخمول — بالدقائق") },
                        supportingText = { Text("يمكن ضبطها من 1 إلى ${SessionPolicy.MAX_TIMEOUT_MINUTES} دقيقة") },
                        enabled = canManage,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxSessionMinutes,
                        onValueChange = { maxSessionMinutes = it.filter(Char::isDigit).take(5) },
                        label = { Text("الحد الأقصى لمدة الجلسة — بالدقائق") },
                        supportingText = { Text("هذه هي أقصى مدة للجلسة حتى مع استمرار الاستخدام") },
                        enabled = canManage,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (canManage) {
                        Button(
                            onClick = {
                                val idle = idleMinutes.toLongOrNull()
                                val maximum = maxSessionMinutes.toLongOrNull()
                                if (idle == null || maximum == null ||
                                        idle !in SessionPolicy.MIN_TIMEOUT_MINUTES..SessionPolicy.MAX_TIMEOUT_MINUTES ||
                                        maximum !in SessionPolicy.MIN_TIMEOUT_MINUTES..SessionPolicy.MAX_TIMEOUT_MINUTES) {
                                    sessionMessage = "أدخل مدة صحيحة بين 1 و${SessionPolicy.MAX_TIMEOUT_MINUTES} دقيقة"
                                } else {
                                    val old = container.sessionSettings.current()
                                    val updated = SessionTimeoutSettings(
                                        automaticLogoutEnabled = automaticLogoutEnabled,
                                        idleTimeoutMinutes = idle ?: SessionPolicy.DEFAULT_IDLE_MINUTES,
                                        maxSessionMinutes = maximum ?: SessionPolicy.DEFAULT_ABSOLUTE_MINUTES
                                    )
                                    container.sessionSettings.save(updated)
                                    scope.launch {
                                        runCatching {
                                            container.securityService.recordSessionPolicyChange(
                                                currentUser.id,
                                                oldValue = old.toString(),
                                                newValue = updated.toString()
                                            )
                                        }
                                    }
                                    sessionMessage = "تم حفظ مدة الجلسة الجديدة وستطبق تلقائيًا"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حفظ إعدادات الجلسة") }
                    }
                    sessionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { PolicyCard("كلمات المرور", "${PasswordPolicy.MIN_LENGTH} حرفًا على الأقل، حرف كبير وصغير ورقم ورمز خاص، ومنع إعادة استخدام آخر ${PasswordPolicy.HISTORY_COUNT} كلمات مرور، وتغيير إلزامي بعد ${PasswordPolicy.MAX_AGE_DAYS} يومًا.") }
        item { PolicyCard("الحسابات الجديدة", "كلمة المرور الأولية مؤقتة ويجب تغييرها عند أول دخول.") }
        item { PolicyCard("محاولات الدخول", "بعد 5 محاولات فاشلة: قفل 15 دقيقة. عند تكرار القفل: 60 دقيقة.") }
        item { PolicyCard("الجلسات", "مدة الجلسة قابلة للتحديد من داخل التطبيق بواسطة مدير النظام بدل الحدود الثابتة السابقة. الإعداد الافتراضي 60 دقيقة، ويمكن اختيار مدة سريعة أو إدخال مدة مخصصة. تعطيل المستخدم أو تغيير دوره/كلمة مروره أو تسجيل دخول جديد ينهي الجلسة القديمة فورًا.") }
        item { PolicyCard("إعادة التحقق للعمليات الحساسة", "إدارة المستخدمين والأدوار واستعادة النسخ الاحتياطية تتطلب إعادة إدخال كلمة المرور الحالية. صلاحية إعادة التحقق ${com.fush.erp.domain.ReauthenticationPolicy.WINDOW_MINUTES} دقائق فقط ثم يجب التحقق من جديد.") }
        item { PolicyCard("تغيير الدور أو كلمة المرور", "يتم إبطال الجلسة الحالية فور تغيير الدور أو إعادة ضبط كلمة المرور، كما أن تسجيل دخول جديد يبطل الجلسة السابقة.") }
        item { PolicyCard("الحماية من فقد الإدارة", "لا يمكن تعطيل آخر مدير نظام نشط أو إزالة دور ADMIN منه.") }
        item { PolicyCard("سجل التدقيق", "تُسجل عمليات الدخول وإدارة المستخدمين والأدوار وتغيير سياسة الجلسة، كما تمنع قاعدة البيانات تعديل أحداث التدقيق أو حذفها بعد تسجيلها.") }
    }
}

@Composable
private fun PolicyCard(title: String, text: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddUserDialog(roles: List<RoleEntity>, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember(roles) { mutableStateOf(roles.firstOrNull()?.code ?: "") }
    val selectedRoleEntity = roles.firstOrNull { it.code == selectedRole }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مستخدم جديد") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(username, { username = it }, label = { Text("اسم المستخدم بالإنجليزية") }, singleLine = true)
                OutlinedTextField(display, { display = it }, label = { Text("الاسم الظاهر") }, singleLine = true)
                FushSearchableSelectionField(
                    label = "الدور",
                    selectedText = selectedRoleEntity?.let { "${it.code} — ${it.nameAr}" }.orEmpty(),
                    options = roles,
                    optionText = { "${it.code} — ${it.nameAr}" },
                    searchTerms = { listOf(it.code, it.nameAr, it.nameEn) },
                    onCleared = { selectedRole = "" },
                    onSelected = { selectedRole = it.code },
                    placeholder = "اكتب كود أو اسم الدور",
                )
                OutlinedTextField(password, { password = it }, label = { Text("كلمة مرور مؤقتة") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("${PasswordPolicy.MIN_LENGTH} حرفًا على الأقل + كبير/صغير + رقم + رمز. سيُطلب تغييرها عند أول دخول.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = username.isNotBlank() && display.isNotBlank() && selectedRole.isNotBlank() && password.isNotBlank(),
                onClick = { onSave(username, display, selectedRole, password) }
            ) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CloudProvisionDialog(
    target: UserEntity,
    existing: CloudUserBinding?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var email by remember(target.id) { mutableStateOf(existing?.email.orEmpty()) }
    var password by remember(target.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حساب السحابة — ${target.displayName}") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text(
                    "سيتم ربط مستخدم FUSH المحلي (${target.username}) بحساب Supabase مستقل ودور ${target.role}. لا تستخدم بريد المدير لموظف آخر.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني للمستخدم") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة مرور سحابية مؤقتة") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text(
                    "يحتاج مدير النظام إلى جلسة سحابية OWNER فعالة. إذا كان الحساب موجودًا مسبقًا فسيتم ربطه دون تغيير كلمة مروره الحالية.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = email.isNotBlank() && password.isNotBlank(),
                onClick = { onSave(email, password) },
            ) { Text(if (existing == null) "إنشاء وربط" else "تأكيد الربط") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun AssignRoleDialog(target: UserEntity, roles: List<RoleEntity>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember { mutableStateOf(target.role) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير دور ${target.displayName}") },
        text = {
            Column {
                roles.forEach { role ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == role.code, onClick = { selected = role.code })
                        Text(role.nameAr)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ResetPasswordDialog(target: UserEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة كلمة مرور ${target.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور المؤقتة الجديدة") }, visualTransformation = PasswordVisualTransformation())
                Text("سيتم إبطال صلاحية الجلسة القديمة ويجب على المستخدم تغيير كلمة المرور عند الدخول.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = password.isNotBlank(), onClick = { onSave(password) }) { Text("إعادة الضبط") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DeleteRoleDialog(role: RoleEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف الدور ${role.nameAr}") },
        text = {
            Text(
                "سيتم حذف الدور وصلاحياته نهائيًا. إذا كان هناك أي مستخدم مرتبط بهذا الدور فلن يسمح النظام بالحذف حتى يتم تغيير دوره أولاً."
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("حذف الدور") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddRoleDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دور جديد") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("رمز الدور - مثال STORE_MANAGER") })
                OutlinedTextField(name, { name = it }, label = { Text("اسم الدور") })
                OutlinedTextField(description, { description = it }, label = { Text("الوصف") }, minLines = 2)
            }
        },
        confirmButton = { Button(enabled = code.isNotBlank() && name.isNotBlank(), onClick = { onSave(code, name, description) }) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ChangePasswordScreen(
    container: AppContainer,
    user: UserEntity,
    forced: Boolean,
    onChanged: (UserEntity) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (forced) "تغيير كلمة المرور مطلوب" else "تغيير كلمة المرور", style = MaterialTheme.typography.headlineSmall)
                    if (forced) Text("لأمان الحساب يجب تغيير كلمة المرور المؤقتة قبل استخدام النظام.")
                    OutlinedTextField(current, { current = it }, label = { Text("كلمة المرور الحالية") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("كلمة المرور الجديدة") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("تأكيد كلمة المرور الجديدة") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Text("${PasswordPolicy.MIN_LENGTH} حرفًا على الأقل، مع حرف كبير وصغير ورقم ورمز، ومنع إعادة استخدام آخر ${PasswordPolicy.HISTORY_COUNT} كلمات مرور.", style = MaterialTheme.typography.bodySmall)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = !busy && current.isNotBlank() && newPassword.isNotBlank() && newPassword == confirm,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching { container.securityService.changePassword(user.id, current.toCharArray(), newPassword.toCharArray()) }
                                    .onSuccess(onChanged)
                                    .onFailure { message = it.message ?: "تعذر تغيير كلمة المرور" }
                                current = ""; newPassword = ""; confirm = ""; busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حفظ كلمة المرور") }
                    TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

private fun moduleLabel(module: String): String = when (module) {
    "DASHBOARD" -> "لوحة الإدارة"
    "SALES" -> "المبيعات والعملاء"
    "PURCHASES" -> "المشتريات والموردون"
    "INVENTORY" -> "المخزون"
    "MASTER_DATA" -> "البيانات الأساسية"
    "PRODUCTION" -> "الإنتاج والجودة"
    "PLANNING" -> "التخطيط"
    "ACCOUNTING" -> "الحسابات والخزينة"
    "HR" -> "الموظفون والمناديب"
    "MAINTENANCE" -> "الصيانة والسلامة"
    "GOVERNANCE" -> "الحوكمة والمخاطر"
    "SUPPORT" -> "الدعم والصيانة"
    "REPORTS" -> "التقارير"
    "SYSTEM" -> "النظام والنسخ الاحتياطي"
    "SECURITY" -> "المستخدمون والأمان"
    else -> module
}

private fun formatDate(value: Long?): String = value?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
} ?: "—"
