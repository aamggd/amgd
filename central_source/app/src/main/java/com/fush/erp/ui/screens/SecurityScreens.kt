package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.PermissionEntity
import com.fush.erp.data.entity.RoleEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.PasswordPolicy
import com.fush.erp.domain.MfaEnrollmentResult
import com.fush.erp.domain.MfaSetupData
import com.fush.erp.domain.SecurityPermissions
import com.fush.erp.domain.SessionPolicy
import com.fush.erp.domain.SessionTimeoutSettings
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
                }
            }
        }
    }
}

@Composable
fun MfaSetupScreen(
    container: AppContainer,
    userId: Long,
    onCompleted: (UserEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPassword by remember { mutableStateOf("") }
    var setup by remember { mutableStateOf<MfaSetupData?>(null) }
    var verificationCode by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf<MfaEnrollmentResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().widthIn(max = 620.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إعداد التحقق الثنائي MFA", style = MaterialTheme.typography.headlineSmall)
                    when {
                        completed != null -> {
                            Text("تم تفعيل MFA. احفظ رموز الاسترداد التالية في مكان آمن. كل رمز يعمل مرة واحدة فقط.")
                            SelectionContainer {
                                Text(completed!!.recoveryCodes.joinToString("\n"), style = MaterialTheme.typography.bodyLarge)
                            }
                            Text(
                                "لن يعرض النظام هذه الرموز بصيغتها الأصلية مرة أخرى.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { onCompleted(completed!!.user) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("حفظت الرموز — متابعة") }
                        }
                        setup == null -> {
                            Text("الحساب ذو صلاحيات حساسة، لذلك يجب ربطه بتطبيق مصادقة قبل استخدام النظام.")
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = { currentPassword = it },
                                label = { Text("كلمة المرور الحالية") },
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                enabled = !busy && currentPassword.isNotBlank(),
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        runCatching { container.securityService.beginMfaSetup(userId, currentPassword.toCharArray()) }
                                            .onSuccess { setup = it }
                                            .onFailure { message = it.message ?: "تعذر بدء إعداد MFA" }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إنشاء مفتاح MFA") }
                            TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء والعودة") }
                        }
                        else -> {
                            Text("أضف حسابًا جديدًا في تطبيق المصادقة باستخدام المفتاح التالي:")
                            SelectionContainer { Text(setup!!.secret, style = MaterialTheme.typography.titleMedium) }
                            Text("رابط الإعداد اليدوي:", style = MaterialTheme.typography.labelMedium)
                            SelectionContainer { Text(setup!!.provisioningUri, style = MaterialTheme.typography.bodySmall) }
                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                                label = { Text("رمز التحقق المكوّن من 6 أرقام") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                enabled = !busy && verificationCode.length == 6,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        runCatching {
                                            container.securityService.confirmMfaSetup(
                                                userId = userId,
                                                currentPassword = currentPassword.toCharArray(),
                                                secret = setup!!.secret,
                                                code = verificationCode
                                            )
                                        }.onSuccess {
                                            completed = it
                                            currentPassword = ""
                                            verificationCode = ""
                                        }.onFailure { message = it.message ?: "تعذر تفعيل MFA" }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("تحقق وفعّل MFA") }
                            TextButton(
                                onClick = { setup = null; verificationCode = ""; message = null },
                                enabled = !busy
                            ) { Text("إنشاء مفتاح جديد") }
                        }
                    }
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
    var mfaResetTarget by remember { mutableStateOf<UserEntity?>(null) }
    var selectedRole by remember { mutableStateOf<RoleEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingCriticalAction by remember { mutableStateOf<PendingCriticalAction?>(null) }
    val scope = rememberCoroutineScope()

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
            0 -> UsersTab(
                users = users,
                roles = roles,
                canManage = can(SecurityPermissions.USERS_MANAGE),
                currentUserId = currentUser.id,
                onAdd = { addUser = true },
                onRole = { roleTarget = it },
                onReset = { resetTarget = it },
                onMfaReset = { mfaResetTarget = it },
                onToggleActive = { target, active ->
                    runCritical(if (active) "تفعيل مستخدم" else "تعطيل مستخدم") {
                        message = runCatching {
                            container.securityService.setUserActive(currentUser.id, target.id, active)
                            if (active) "تم تفعيل ${target.displayName}" else "تم تعطيل ${target.displayName}"
                        }.getOrElse { it.message ?: "تعذر تحديث المستخدم" }
                    }
                }
            )
            1 -> RolesTab(
                roles = roles,
                permissions = permissions,
                canManage = can(SecurityPermissions.ROLES_MANAGE),
                selectedRole = selectedRole,
                onSelectRole = { selectedRole = it },
                onAddRole = { addRole = true },
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
            roles = roles.filter { it.isActive },
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

    roleTarget?.let { target ->
        AssignRoleDialog(
            target = target,
            roles = roles.filter { it.isActive },
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

    mfaResetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { mfaResetTarget = null },
            title = { Text("إعادة ضبط MFA") },
            text = { Text("سيتم إلغاء مفاتيح MFA ورموز الاسترداد للمستخدم ${target.displayName} وإنهاء جلسته. سيُطلب منه إعداد MFA من جديد إذا كان دوره حساسًا.") },
            confirmButton = {
                TextButton(onClick = {
                    runCritical("إعادة ضبط MFA لمستخدم") {
                        message = runCatching {
                            container.securityService.resetMfa(currentUser.id, target.id)
                            mfaResetTarget = null
                            "تمت إعادة ضبط MFA للمستخدم ${target.displayName}"
                        }.getOrElse { it.message ?: "تعذر إعادة ضبط MFA" }
                    }
                }) { Text("إعادة الضبط") }
            },
            dismissButton = { TextButton(onClick = { mfaResetTarget = null }) { Text("إلغاء") } }
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

    pendingCriticalAction?.let { pending ->
        ReauthenticationDialog(
            container = container,
            userId = currentUser.id,
            requireMfa = true,
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
    canManage: Boolean,
    currentUserId: Long,
    onAdd: () -> Unit,
    onRole: (UserEntity) -> Unit,
    onReset: (UserEntity) -> Unit,
    onMfaReset: (UserEntity) -> Unit,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {}, label = { Text(if (user.mfaEnabled) "MFA مفعّل" else "MFA غير مفعّل") })
                        if (user.mfaConfirmedAt != null) Text("منذ ${formatDate(user.mfaConfirmedAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (user.lockedUntil != null && user.lockedUntil > System.currentTimeMillis()) {
                        Text("الحساب مقفل حتى ${formatDate(user.lockedUntil)}", color = MaterialTheme.colorScheme.error)
                    }
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onRole(user) }) { Text("الدور") }
                            TextButton(onClick = { onReset(user) }) { Text("إعادة كلمة المرور") }
                            if (user.mfaEnabled && user.id != currentUserId) {
                                TextButton(onClick = { onMfaReset(user) }) { Text("إعادة MFA") }
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

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("الأدوار والصلاحيات", style = MaterialTheme.typography.headlineSmall)
                    Text("اختر دورًا ثم حدد ما يستطيع مستخدموه رؤيته وتنفيذه.", style = MaterialTheme.typography.bodySmall)
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
                    Column(Modifier.padding(14.dp)) {
                        Text("${role.nameAr} (${role.code})", style = MaterialTheme.typography.titleMedium)
                        if (role.description.isNotBlank()) Text(role.description, style = MaterialTheme.typography.bodySmall)
                        if (role.code == "ADMIN") Text("دور مدير النظام محمي ويحتفظ دائمًا بجميع الصلاحيات.", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            grouped.forEach { (module, rows) ->
                item { Text(moduleLabel(module), style = MaterialTheme.typography.titleMedium) }
                items(rows, key = { it.code }) { permission ->
                    ListItem(
                        headlineContent = { Text(permission.nameAr) },
                        supportingContent = { Text(permission.code, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            Checkbox(
                                checked = role.code == "ADMIN" || permission.code in selectedCodes,
                                enabled = canManage && role.code != "ADMIN",
                                onCheckedChange = { checked ->
                                    selectedCodes = if (checked) selectedCodes + permission.code else selectedCodes - permission.code
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
            item {
                if (canManage) {
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
    var automaticLogoutEnabled by remember { mutableStateOf(initial.automaticLogoutEnabled) }
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
                                if (automaticLogoutEnabled) "مفعّل — سيتم تطبيق المدد المحددة أدناه"
                                else "متوقف — لن تُغلق الجلسة بسبب مرور الوقت أو الخمول",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = automaticLogoutEnabled,
                            enabled = canManage,
                            onCheckedChange = { automaticLogoutEnabled = it }
                        )
                    }
                    OutlinedTextField(
                        value = idleMinutes,
                        onValueChange = { idleMinutes = it.filter(Char::isDigit).take(5) },
                        label = { Text("الإغلاق بعد الخمول — بالدقائق") },
                        supportingText = { Text("مثال: 30 دقيقة") },
                        enabled = canManage && automaticLogoutEnabled,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxSessionMinutes,
                        onValueChange = { maxSessionMinutes = it.filter(Char::isDigit).take(5) },
                        label = { Text("الحد الأقصى لمدة الجلسة — بالدقائق") },
                        supportingText = { Text("مثال: 480 دقيقة = 8 ساعات") },
                        enabled = canManage && automaticLogoutEnabled,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (canManage) {
                        Button(
                            onClick = {
                                val idle = idleMinutes.toLongOrNull()
                                val maximum = maxSessionMinutes.toLongOrNull()
                                if (automaticLogoutEnabled && (idle == null || maximum == null ||
                                        idle !in SessionPolicy.MIN_TIMEOUT_MINUTES..SessionPolicy.MAX_TIMEOUT_MINUTES ||
                                        maximum !in SessionPolicy.MIN_TIMEOUT_MINUTES..SessionPolicy.MAX_TIMEOUT_MINUTES)) {
                                    sessionMessage = "أدخل مدة صحيحة بين 1 و${SessionPolicy.MAX_TIMEOUT_MINUTES} دقيقة"
                                } else {
                                    val old = container.sessionSettings.current()
                                    val updated = SessionTimeoutSettings(
                                        automaticLogoutEnabled = automaticLogoutEnabled,
                                        idleTimeoutMinutes = idle ?: SessionPolicy.DEFAULT_IDLE_MINUTES,
                                        maxSessionMinutes = maximum ?: SessionPolicy.DEFAULT_MAX_SESSION_MINUTES
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
                                    sessionMessage = if (automaticLogoutEnabled) "تم حفظ مدة الجلسة" else "تم تعطيل الإغلاق التلقائي للجلسة"
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
        item { PolicyCard("الجلسات", "الإغلاق الزمني قابل للتحكم يدويًا من الإعداد أعلاه، والافتراضي هو عدم الإغلاق التلقائي. يبقى تعطيل المستخدم أو تغيير دوره/كلمة مروره أو تسجيل دخول جديد سببًا فوريًا لإنهاء الجلسة القديمة.") }
        item { PolicyCard("MFA للحسابات الحساسة", "التحقق الثنائي إلزامي للمدير ولأي دور يملك إدارة المستخدمين/الأدوار أو استعادة النسخ الاحتياطية. تتوفر 10 رموز استرداد أحادية الاستخدام.") }
        item { PolicyCard("إعادة التحقق للعمليات الحساسة", "إدارة المستخدمين والأدوار واستعادة النسخ الاحتياطية تتطلب كلمة المرور الحالية + MFA حديثًا. صلاحية إعادة التحقق ${com.fush.erp.domain.ReauthenticationPolicy.WINDOW_MINUTES} دقائق فقط ثم يجب التحقق من جديد.") }
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
    var roleMenu by remember { mutableStateOf(false) }
    val roleName = roles.firstOrNull { it.code == selectedRole }?.nameAr ?: selectedRole
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مستخدم جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(username, { username = it }, label = { Text("اسم المستخدم بالإنجليزية") }, singleLine = true)
                OutlinedTextField(display, { display = it }, label = { Text("الاسم الظاهر") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { roleMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("الدور: $roleName") }
                    DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                        roles.forEach { role ->
                            DropdownMenuItem(text = { Text(role.nameAr) }, onClick = { selectedRole = role.code; roleMenu = false })
                        }
                    }
                }
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
private fun AddRoleDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دور جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun FlowRowCompat(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
    "REPORTS" -> "التقارير"
    "SYSTEM" -> "النظام والنسخ الاحتياطي"
    "SECURITY" -> "المستخدمون والأمان"
    else -> module
}

private fun formatDate(value: Long?): String = value?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
} ?: "—"
