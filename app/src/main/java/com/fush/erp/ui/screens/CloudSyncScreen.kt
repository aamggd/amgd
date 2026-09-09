package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.cloud.AccountingCloudSyncResult
import com.fush.erp.cloud.AccountingConflictResolution
import com.fush.erp.cloud.AccountingSyncConflict
import com.fush.erp.cloud.CloudConnectionResult
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.cloud.CompanySyncAllResult
import com.fush.erp.cloud.MasterDataSyncResult
import com.fush.erp.cloud.InventoryProductionConflict
import com.fush.erp.cloud.InventoryProductionSyncResult
import com.fush.erp.cloud.PurchaseDocumentsConflict
import com.fush.erp.cloud.PurchaseDocumentsSyncResult
import com.fush.erp.cloud.SalesReceivablesConflict
import com.fush.erp.cloud.SalesReceivablesSyncResult
import com.fush.erp.cloud.SalesAuxiliaryConflict
import com.fush.erp.cloud.SalesAuxiliaryConflictResolution
import com.fush.erp.cloud.SalesAuxiliarySyncResult
import com.fush.erp.cloud.SupplierPaymentsTreasuryConflict
import com.fush.erp.cloud.SupplierPaymentsTreasurySyncResult
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSyncScreen(
    container: AppContainer,
    user: UserEntity,
    modifier: Modifier = Modifier,
) {
    val repository = container.cloudSyncRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember(user.id) { mutableStateOf(repository.currentSession(user)) }
    var binding by remember(user.id) { mutableStateOf(repository.bindingFor(user)) }
    var email by remember(user.id) { mutableStateOf(binding?.email ?: session?.email.orEmpty()) }
    var password by remember(user.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf<CloudConnectionResult?>(null) }
    var masterDataResult by remember { mutableStateOf<MasterDataSyncResult?>(null) }
    var lastMasterDataSyncAt by remember(user.id) { mutableStateOf(repository.lastMasterDataSyncAt(user)) }
    var salesReceivablesResult by remember { mutableStateOf<SalesReceivablesSyncResult?>(null) }
    var lastSalesReceivablesSyncAt by remember(user.id) { mutableStateOf(repository.lastSalesReceivablesSyncAt(user)) }
    var salesConflicts by remember(user.id) { mutableStateOf(repository.lastSalesReceivablesConflicts(user)) }
    var purchaseDocumentsResult by remember { mutableStateOf<PurchaseDocumentsSyncResult?>(null) }
    var lastPurchaseDocumentsSyncAt by remember(user.id) { mutableStateOf(repository.lastPurchaseDocumentsSyncAt(user)) }
    var purchaseConflicts by remember(user.id) { mutableStateOf(repository.lastPurchaseDocumentsConflicts(user)) }
    var supplierPaymentsTreasuryResult by remember { mutableStateOf<SupplierPaymentsTreasurySyncResult?>(null) }
    var lastSupplierPaymentsTreasurySyncAt by remember(user.id) { mutableStateOf(repository.lastSupplierPaymentsTreasurySyncAt(user)) }
    var supplierPaymentsTreasuryConflicts by remember(user.id) { mutableStateOf(repository.lastSupplierPaymentsTreasuryConflicts(user)) }
    var inventoryProductionResult by remember { mutableStateOf<InventoryProductionSyncResult?>(null) }
    var lastInventoryProductionSyncAt by remember(user.id) { mutableStateOf(repository.lastInventoryProductionSyncAt(user)) }
    var inventoryProductionConflicts by remember(user.id) { mutableStateOf(repository.lastInventoryProductionConflicts(user)) }
    var accountingResult by remember { mutableStateOf<AccountingCloudSyncResult?>(null) }
    var lastAccountingSyncAt by remember(user.id) { mutableStateOf(repository.lastAccountingSyncAt(user)) }
    var accountingConflicts by remember(user.id) { mutableStateOf(repository.lastAccountingConflicts(user)) }
    var companySyncResult by remember { mutableStateOf<CompanySyncAllResult?>(null) }
    var salesAuxiliaryResult by remember { mutableStateOf<SalesAuxiliarySyncResult?>(null) }
    var lastSalesAuxiliarySyncAt by remember(user.id) { mutableStateOf(repository.lastSalesAuxiliarySyncAt(user)) }
    var salesAuxiliaryConflicts by remember(user.id) { mutableStateOf(repository.lastSalesAuxiliaryConflicts(user)) }
    var showAdvancedSyncDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cloud_sync_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.cloud_sync_multi_user_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.cloud_sync_local_identity), style = MaterialTheme.typography.titleMedium)
                Text("${user.displayName} • ${user.username}")
                Text(
                    stringResource(R.string.cloud_sync_local_role, user.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                binding?.let {
                    Text(
                        stringResource(R.string.cloud_sync_bound_identity, it.email ?: it.cloudUserId, it.cloudRole),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cloud_sync_server), style = MaterialTheme.typography.titleMedium)
                Text(BuildConfig.SUPABASE_URL, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.cloud_sync_org_id, session?.organizationId ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (session == null) {
            Text(stringResource(R.string.cloud_sync_sign_in_user_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.cloud_sync_sign_in_user_help, user.username),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cloud_sync_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !busy,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cloud_sync_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
            )
            Button(
                onClick = {
                    busy = true
                    message = null
                    connection = null
                    scope.launch {
                        when (val result = repository.signIn(user, email, password)) {
                            is CloudOperationResult.Success -> {
                                session = result.value
                                binding = repository.bindingFor(user)
                                password = ""
                                messageIsError = false
                                message = result.value.email?.takeIf { it.isNotBlank() }?.let {
                                    context.getString(R.string.cloud_sync_sign_in_success_email, it)
                                } ?: context.getString(R.string.cloud_sync_sign_in_success)
                            }
                            is CloudOperationResult.Failure -> {
                                session = repository.currentSession(user)
                                binding = repository.bindingFor(user)
                                messageIsError = true
                                message = result.message
                            }
                        }
                        busy = false
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cloud_sync_sign_in))
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Text(stringResource(R.string.cloud_sync_signed_in), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(session?.email ?: session?.userId.orEmpty())
                    Text(
                        stringResource(R.string.cloud_sync_user_id, session?.userId.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    binding?.let {
                        Text(
                            stringResource(R.string.cloud_sync_cloud_role, it.cloudRole),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    busy = true
                    message = null
                    connection = null
                    scope.launch {
                        when (val result = repository.testAndRegisterDevice(user)) {
                            is CloudOperationResult.Success -> {
                                connection = result.value
                                session = repository.currentSession(user)
                                binding = repository.bindingFor(user)
                                when (val syncResult = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = syncResult.value
                                        lastMasterDataSyncAt = syncResult.value.completedAtEpochMillis
                                        messageIsError = syncResult.value.conflicts > 0
                                        message = null
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = syncResult.message
                                    }
                                }
                            }
                            is CloudOperationResult.Failure -> {
                                session = repository.currentSession(user)
                                binding = repository.bindingFor(user)
                                messageIsError = true
                                message = result.message
                            }
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cloud_sync_test_register))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مزامنة الشركة بالكامل", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "المزامنة ثنائية الاتجاه لكل عضو نشط في الشركة، ولا تعتمد على دور ACCOUNTANT / SALES / PRODUCTION. الصلاحيات تُفحص فقط عند إنشاء أو تعديل أو ترحيل العملية داخل التطبيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val result = repository.syncAllCompanyData(user)) {
                                    is CloudOperationResult.Success -> {
                                        companySyncResult = result.value
                                        messageIsError = result.value.conflicts > 0
                                        message = if (result.value.conflicts > 0)
                                            "اكتملت مزامنة الكل مع ${result.value.conflicts} تعارضات تحتاج مراجعة."
                                        else "اكتملت مزامنة كل بيانات الشركة بنجاح."
                                        lastMasterDataSyncAt = repository.lastMasterDataSyncAt(user)
                                        lastSalesReceivablesSyncAt = repository.lastSalesReceivablesSyncAt(user)
                                        lastPurchaseDocumentsSyncAt = repository.lastPurchaseDocumentsSyncAt(user)
                                        lastSupplierPaymentsTreasurySyncAt = repository.lastSupplierPaymentsTreasurySyncAt(user)
                                        lastInventoryProductionSyncAt = repository.lastInventoryProductionSyncAt(user)
                                        lastAccountingSyncAt = repository.lastAccountingSyncAt(user)
                                        lastSalesAuxiliarySyncAt = repository.lastSalesAuxiliarySyncAt(user)
                                        salesConflicts = repository.lastSalesReceivablesConflicts(user)
                                        purchaseConflicts = repository.lastPurchaseDocumentsConflicts(user)
                                        supplierPaymentsTreasuryConflicts = repository.lastSupplierPaymentsTreasuryConflicts(user)
                                        inventoryProductionConflicts = repository.lastInventoryProductionConflicts(user)
                                        accountingConflicts = repository.lastAccountingConflicts(user)
                                        salesAuxiliaryConflicts = repository.lastSalesAuxiliaryConflicts(user)
                                    }
                                    is CloudOperationResult.Failure -> { messageIsError = true; message = result.message }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "جارٍ مزامنة الكل..." else "مزامنة الكل الآن") }
                    companySyncResult?.let { result ->
                        Text("رفع ${result.uploadedRecords} • تنزيل/تسوية ${result.downloadedRecords} • تعارضات ${result.conflicts}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedButton(
                onClick = { showAdvancedSyncDetails = !showAdvancedSyncDetails },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showAdvancedSyncDetails) "إخفاء تفاصيل المزامنة" else "تفاصيل المزامنة والتعارضات")
            }

            OutlinedButton(
                onClick = {
                    repository.clearSession(user)
                    session = null
                    connection = null
                    password = ""
                    messageIsError = false
                    message = null
                    email = repository.bindingFor(user)?.email.orEmpty()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cloud_sync_sign_out))
            }

            if (showAdvancedSyncDetails) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.cloud_master_data_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.cloud_master_data_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (lastMasterDataSyncAt > 0L) {
                            stringResource(R.string.cloud_master_data_last_sync, formatSyncTime(lastMasterDataSyncAt))
                        } else {
                            stringResource(R.string.cloud_master_data_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val syncResult = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = syncResult.value
                                        lastMasterDataSyncAt = syncResult.value.completedAtEpochMillis
                                        messageIsError = syncResult.value.conflicts > 0
                                        message = null
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = syncResult.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_master_data_sync_now))
                    }
                }
            }

            masterDataResult?.let { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (result.conflicts > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                            )
                            Text(stringResource(R.string.cloud_master_data_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.cloud_master_data_result,
                                result.uploaded, result.downloaded, result.unchanged, result.conflicts
                            )
                        )
                        if (result.skippedLocalOnFirstBaseline > 0 || result.skippedUnauthorized > 0) {
                            Text(
                                stringResource(
                                    R.string.cloud_master_data_skipped,
                                    result.skippedLocalOnFirstBaseline, result.skippedUnauthorized
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.bootstrappedCloud) {
                            Text(
                                stringResource(R.string.cloud_master_data_bootstrapped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (result.conflicts > 0) {
                            Text(
                                stringResource(R.string.cloud_master_data_conflict_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_sales_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.cloud_sales_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (lastSalesReceivablesSyncAt > 0L) {
                            stringResource(R.string.cloud_sales_last_sync, formatSyncTime(lastSalesReceivablesSyncAt))
                        } else {
                            stringResource(R.string.cloud_sales_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val master = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = master.value
                                        lastMasterDataSyncAt = master.value.completedAtEpochMillis
                                        when (val sales = repository.syncSalesReceivables(user)) {
                                            is CloudOperationResult.Success -> {
                                                salesReceivablesResult = sales.value
                                                salesConflicts = sales.value.conflictDetails
                                                lastSalesReceivablesSyncAt = sales.value.completedAtEpochMillis
                                                messageIsError = sales.value.conflicts > 0
                                                message = null
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = sales.message
                                            }
                                        }
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = master.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_sales_sync_now))
                    }
                }
            }

            salesReceivablesResult?.let { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (result.conflicts > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                            )
                            Text(stringResource(R.string.cloud_sales_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.cloud_sales_result,
                                result.uploadedDocuments, result.downloadedDocuments,
                                result.unchangedDocuments, result.conflicts
                            )
                        )
                        if (result.skippedLocalDocuments > 0) {
                            Text(
                                stringResource(R.string.cloud_sales_skipped_local, result.skippedLocalDocuments),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.bootstrappedCloud) {
                            Text(
                                stringResource(R.string.cloud_sales_bootstrapped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_sales_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (salesConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.cloud_sales_conflict_inspector_title, salesConflicts.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_sales_conflict_inspector_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        salesConflicts.forEach { conflict ->
                            SalesConflictCard(conflict)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_purchase_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.cloud_purchase_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (lastPurchaseDocumentsSyncAt > 0L) {
                            stringResource(R.string.cloud_purchase_last_sync, formatSyncTime(lastPurchaseDocumentsSyncAt))
                        } else {
                            stringResource(R.string.cloud_purchase_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val master = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = master.value
                                        lastMasterDataSyncAt = master.value.completedAtEpochMillis
                                        when (val purchase = repository.syncPurchaseDocuments(user)) {
                                            is CloudOperationResult.Success -> {
                                                purchaseDocumentsResult = purchase.value
                                                lastPurchaseDocumentsSyncAt = purchase.value.completedAtEpochMillis
                                                purchaseConflicts = purchase.value.conflictDetails
                                                messageIsError = purchase.value.conflicts > 0
                                                message = null
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = purchase.message
                                            }
                                        }
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = master.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_purchase_sync_now))
                    }
                }
            }

            purchaseDocumentsResult?.let { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (result.conflicts > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                            )
                            Text(stringResource(R.string.cloud_purchase_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.cloud_purchase_result,
                                result.uploadedDocuments, result.downloadedDocuments,
                                result.unchangedDocuments, result.conflicts
                            )
                        )
                        if (result.skippedLocalDocuments > 0) {
                            Text(
                                stringResource(R.string.cloud_purchase_skipped_local, result.skippedLocalDocuments),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.bootstrappedCloud) {
                            Text(
                                stringResource(R.string.cloud_purchase_bootstrapped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_purchase_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (purchaseConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.cloud_purchase_conflict_inspector_title, purchaseConflicts.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_purchase_conflict_inspector_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        purchaseConflicts.forEach { conflict -> PurchaseConflictCard(conflict) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_supplier_payment_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.cloud_supplier_payment_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (lastSupplierPaymentsTreasurySyncAt > 0L) {
                            stringResource(R.string.cloud_supplier_payment_last_sync, formatSyncTime(lastSupplierPaymentsTreasurySyncAt))
                        } else {
                            stringResource(R.string.cloud_supplier_payment_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val master = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = master.value
                                        lastMasterDataSyncAt = master.value.completedAtEpochMillis
                                        when (val purchase = repository.syncPurchaseDocuments(user)) {
                                            is CloudOperationResult.Success -> {
                                                purchaseDocumentsResult = purchase.value
                                                lastPurchaseDocumentsSyncAt = purchase.value.completedAtEpochMillis
                                                purchaseConflicts = purchase.value.conflictDetails
                                                when (val supplierPayments = repository.syncSupplierPaymentsTreasury(user)) {
                                                    is CloudOperationResult.Success -> {
                                                        supplierPaymentsTreasuryResult = supplierPayments.value
                                                        lastSupplierPaymentsTreasurySyncAt = supplierPayments.value.completedAtEpochMillis
                                                        supplierPaymentsTreasuryConflicts = supplierPayments.value.conflictDetails
                                                        messageIsError = supplierPayments.value.conflicts > 0
                                                        message = null
                                                    }
                                                    is CloudOperationResult.Failure -> {
                                                        messageIsError = true
                                                        message = supplierPayments.message
                                                    }
                                                }
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = purchase.message
                                            }
                                        }
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = master.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_supplier_payment_sync_now))
                    }
                }
            }

            supplierPaymentsTreasuryResult?.let { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (result.conflicts > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle, contentDescription = null)
                            Text(stringResource(R.string.cloud_supplier_payment_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.cloud_supplier_payment_result,
                                result.uploadedPayments, result.downloadedPayments,
                                result.unchangedPayments, result.conflicts, result.treasuryAccountsReady
                            )
                        )
                        if (result.skippedLocalPayments > 0) {
                            Text(
                                stringResource(R.string.cloud_supplier_payment_skipped_local, result.skippedLocalPayments),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.bootstrappedCloud) {
                            Text(
                                stringResource(R.string.cloud_supplier_payment_bootstrapped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_supplier_payment_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (supplierPaymentsTreasuryConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.cloud_supplier_payment_conflict_title, supplierPaymentsTreasuryConflicts.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_supplier_payment_conflict_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        supplierPaymentsTreasuryConflicts.forEach { conflict -> SupplierPaymentsTreasuryConflictCard(conflict) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_inventory_production_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.cloud_inventory_production_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (lastInventoryProductionSyncAt > 0L) {
                            stringResource(R.string.cloud_inventory_production_last_sync, formatSyncTime(lastInventoryProductionSyncAt))
                        } else {
                            stringResource(R.string.cloud_inventory_production_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val master = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> {
                                        masterDataResult = master.value
                                        lastMasterDataSyncAt = master.value.completedAtEpochMillis
                                        when (val inventoryProduction = repository.syncInventoryProduction(user)) {
                                            is CloudOperationResult.Success -> {
                                                inventoryProductionResult = inventoryProduction.value
                                                lastInventoryProductionSyncAt = inventoryProduction.value.completedAtEpochMillis
                                                inventoryProductionConflicts = inventoryProduction.value.conflictDetails
                                                messageIsError = inventoryProduction.value.productionConflicts > 0
                                                message = null
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = inventoryProduction.message
                                            }
                                        }
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = master.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_inventory_production_sync_now))
                    }
                }
            }

            inventoryProductionResult?.let { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (result.productionConflicts > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle, contentDescription = null)
                            Text(stringResource(R.string.cloud_inventory_production_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(
                                R.string.cloud_inventory_production_result,
                                result.uploadedProductionOrders,
                                result.downloadedProductionOrders,
                                result.unchangedProductionOrders,
                                result.productionConflicts,
                                result.inventoryLotsPublished,
                                result.inventoryLotsAdjusted,
                                result.inventoryLotsUnchanged,
                            )
                        )
                        if (result.inventoryAbsoluteQuantityDelta > 0.000001) {
                            Text(
                                stringResource(R.string.cloud_inventory_production_qty_delta, result.inventoryAbsoluteQuantityDelta),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.skippedLocalProductionOrders > 0) {
                            Text(
                                stringResource(R.string.cloud_inventory_production_skipped_local, result.skippedLocalProductionOrders),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (result.bootstrappedCloud) {
                            Text(
                                stringResource(R.string.cloud_inventory_production_bootstrapped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_inventory_production_safety_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (inventoryProductionConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.cloud_inventory_production_conflict_title, inventoryProductionConflicts.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_inventory_production_conflict_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        inventoryProductionConflicts.forEach { conflict -> InventoryProductionConflictCard(conflict) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(stringResource(R.string.cloud_accounting_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(R.string.cloud_accounting_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lastAccountingSyncAt > 0L) {
                        Text(
                            stringResource(R.string.cloud_accounting_last_sync, formatSyncTime(lastAccountingSyncAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val result = repository.syncAccounting(user)) {
                                    is CloudOperationResult.Success -> {
                                        accountingResult = result.value
                                        lastAccountingSyncAt = result.value.completedAtEpochMillis
                                        accountingConflicts = result.value.conflictDetails
                                        messageIsError = result.value.conflicts > 0 || result.value.treasuryBalanceDifferenceBase > 0.01
                                        message = if (result.value.conflicts > 0) {
                                            context.getString(R.string.cloud_accounting_conflicts_found, result.value.conflicts)
                                        } else null
                                    }
                                    is CloudOperationResult.Failure -> {
                                        messageIsError = true
                                        message = result.message
                                    }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cloud_accounting_sync_now))
                    }
                    accountingResult?.let { result ->
                        HorizontalDivider()
                        Text(
                            stringResource(
                                R.string.cloud_accounting_result,
                                result.uploadedJournals, result.downloadedJournals, result.unchangedJournals,
                                result.uploadedTreasuryVouchers, result.downloadedTreasuryVouchers, result.unchangedTreasuryVouchers,
                                result.conflicts,
                            )
                        )
                        Text(
                            stringResource(
                                R.string.cloud_accounting_treasury_check,
                                result.treasuryAccountsChecked, result.treasuryBalanceDifferenceBase,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.treasuryBalanceDifferenceBase > 0.01) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (result.skippedLocalJournals > 0 || result.skippedLocalTreasuryVouchers > 0) {
                            Text(
                                stringResource(R.string.cloud_accounting_skipped_local, result.skippedLocalJournals, result.skippedLocalTreasuryVouchers),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (accountingConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.cloud_accounting_conflict_title, accountingConflicts.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_accounting_conflict_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        accountingConflicts.forEach { conflict ->
                            AccountingConflictCard(
                                conflict = conflict,
                                busy = busy,
                                onKeepLocal = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        when (val result = repository.resolveAccountingConflict(user, conflict, AccountingConflictResolution.KEEP_LOCAL)) {
                                            is CloudOperationResult.Success -> {
                                                accountingResult = result.value
                                                lastAccountingSyncAt = result.value.completedAtEpochMillis
                                                accountingConflicts = result.value.conflictDetails
                                                messageIsError = result.value.conflicts > 0
                                                message = context.getString(R.string.cloud_accounting_resolution_done)
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = result.message
                                            }
                                        }
                                        busy = false
                                    }
                                },
                                onUseCloud = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        when (val result = repository.resolveAccountingConflict(user, conflict, AccountingConflictResolution.USE_CLOUD)) {
                                            is CloudOperationResult.Success -> {
                                                accountingResult = result.value
                                                lastAccountingSyncAt = result.value.completedAtEpochMillis
                                                accountingConflicts = result.value.conflictDetails
                                                messageIsError = result.value.conflicts > 0
                                                message = context.getString(R.string.cloud_accounting_resolution_done)
                                            }
                                            is CloudOperationResult.Failure -> {
                                                messageIsError = true
                                                message = result.message
                                            }
                                        }
                                        busy = false
                                    }
                                },
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    repository.clearSession(user)
                    session = null
                    connection = null
                    password = ""
                    messageIsError = false
                    message = null
                    email = repository.bindingFor(user)?.email.orEmpty()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cloud_sync_sign_out))
            }
        }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text("الرسوم الإضافية وتتبع الشحنات", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "مزامنة ثنائية الاتجاه لـ AdditionalCharges والشحنات والتخصيصات. يبدأ المسار بالمبيعات ثم الأستاذ والخزينة، وبعدها Hydration مباشر دون إعادة قيد الصندوق أو الأستاذ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lastSalesAuxiliarySyncAt > 0L) {
                        Text("آخر مزامنة: ${formatSyncTime(lastSalesAuxiliarySyncAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val master = repository.syncMasterData(user)) {
                                    is CloudOperationResult.Success -> when (val sales = repository.syncSalesReceivables(user)) {
                                        is CloudOperationResult.Success -> when (val accounting = repository.syncAccounting(user)) {
                                            is CloudOperationResult.Success -> when (val aux = repository.syncSalesAuxiliary(user)) {
                                                is CloudOperationResult.Success -> {
                                                    salesAuxiliaryResult = aux.value
                                                    lastSalesAuxiliarySyncAt = aux.value.completedAtEpochMillis
                                                    salesAuxiliaryConflicts = aux.value.conflictDetails
                                                    messageIsError = aux.value.conflicts > 0
                                                    message = if (aux.value.conflicts > 0) "وجدت ${aux.value.conflicts} تعارضات في الرسوم/الشحنات وتحتاج قراراً صريحاً." else null
                                                }
                                                is CloudOperationResult.Failure -> { messageIsError = true; message = aux.message }
                                            }
                                            is CloudOperationResult.Failure -> { messageIsError = true; message = accounting.message }
                                        }
                                        is CloudOperationResult.Failure -> { messageIsError = true; message = sales.message }
                                    }
                                    is CloudOperationResult.Failure -> { messageIsError = true; message = master.message }
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("مزامنة الرسوم والشحنات الآن") }
                    salesAuxiliaryResult?.let { result ->
                        HorizontalDivider()
                        Text("رفع: ${result.uploadedDocuments} • تنزيل: ${result.downloadedDocuments} • مطابق: ${result.unchangedDocuments} • تعارضات: ${result.conflicts}")
                        if (result.skippedLocalDocuments > 0) {
                            Text("مستندات محلية لم يسمح الدور السحابي بنشرها: ${result.skippedLocalDocuments}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (salesAuxiliaryConflicts.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text("تعارضات الرسوم والشحنات (${salesAuxiliaryConflicts.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("لا توجد كتابة صامتة فوق مستند مختلف. OWNER / ADMIN / ACCOUNTANT يختار اعتماد الهاتف أو السحابة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        salesAuxiliaryConflicts.forEach { conflict ->
                            SalesAuxiliaryConflictCard(
                                conflict = conflict,
                                busy = busy,
                                onKeepLocal = {
                                    busy = true; message = null
                                    scope.launch {
                                        when (val result = repository.resolveSalesAuxiliaryConflict(user, conflict, SalesAuxiliaryConflictResolution.KEEP_LOCAL)) {
                                            is CloudOperationResult.Success -> {
                                                salesAuxiliaryResult = result.value; lastSalesAuxiliarySyncAt = result.value.completedAtEpochMillis; salesAuxiliaryConflicts = result.value.conflictDetails; messageIsError = result.value.conflicts > 0
                                            }
                                            is CloudOperationResult.Failure -> { messageIsError = true; message = result.message }
                                        }
                                        busy = false
                                    }
                                },
                                onUseCloud = {
                                    busy = true; message = null
                                    scope.launch {
                                        when (val result = repository.resolveSalesAuxiliaryConflict(user, conflict, SalesAuxiliaryConflictResolution.USE_CLOUD)) {
                                            is CloudOperationResult.Success -> {
                                                salesAuxiliaryResult = result.value; lastSalesAuxiliarySyncAt = result.value.completedAtEpochMillis; salesAuxiliaryConflicts = result.value.conflictDetails; messageIsError = result.value.conflicts > 0
                                            }
                                            is CloudOperationResult.Failure -> { messageIsError = true; message = result.message }
                                        }
                                        busy = false
                                    }
                                },
                            )
                        }
                    }
                }
            }

            } // showAdvancedSyncDetails

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        message?.let { text ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (messageIsError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                    )
                    Text(text, modifier = Modifier.weight(1f))
                }
            }
        }

        connection?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.cloud_sync_connection_success), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text(stringResource(R.string.cloud_sync_company_value, result.organizationName))
                    Text(stringResource(R.string.cloud_sync_local_user_value, result.localUsername))
                    Text(stringResource(R.string.cloud_sync_cloud_role, result.cloudRole))
                    Text(stringResource(R.string.cloud_sync_device_value, result.deviceName))
                    Text(stringResource(R.string.cloud_sync_device_id_value, result.deviceId), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.cloud_sync_identity_ready), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.cloud_sync_v165_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun SalesAuxiliaryConflictCard(
    conflict: SalesAuxiliaryConflict,
    busy: Boolean,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${conflict.entityType}: ${conflict.entityKey}", style = MaterialTheme.typography.titleSmall)
            conflict.differences.take(6).forEach { diff ->
                Text("${diff.field}: الهاتف=${diff.localValue} | السحابة=${diff.cloudValue}", style = MaterialTheme.typography.bodySmall)
            }
            if (conflict.differences.size > 6) {
                Text("+ ${conflict.differences.size - 6} اختلافات أخرى", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUseCloud, enabled = !busy, modifier = Modifier.weight(1f)) { Text("اعتماد السحابة") }
                Button(onClick = onKeepLocal, enabled = !busy, modifier = Modifier.weight(1f)) { Text("اعتماد هذا الهاتف") }
            }
        }
    }
}

@Composable
private fun AccountingConflictCard(
    conflict: AccountingSyncConflict,
    busy: Boolean,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${conflict.entityType}: ${conflict.entityKey}", style = MaterialTheme.typography.titleSmall)
            conflict.differences.take(6).forEach { diff ->
                Text(
                    stringResource(R.string.cloud_accounting_conflict_difference, diff.field, diff.localValue, diff.cloudValue),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (conflict.differences.size > 6) {
                Text(
                    stringResource(R.string.cloud_accounting_more_differences, conflict.differences.size - 6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUseCloud, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cloud_accounting_use_cloud))
                }
                Button(onClick = onKeepLocal, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cloud_accounting_keep_local))
                }
            }
        }
    }
}

@Composable
private fun SalesConflictCard(conflict: SalesReceivablesConflict) {
    val typeLabel = when (conflict.documentType) {
        "INVOICE" -> stringResource(R.string.cloud_sales_conflict_invoice)
        "RECEIPT" -> stringResource(R.string.cloud_sales_conflict_receipt)
        "RETURN" -> stringResource(R.string.cloud_sales_conflict_return)
        else -> conflict.documentType
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$typeLabel: ${conflict.documentNo}", style = MaterialTheme.typography.titleSmall)
            conflict.differences.forEach { diff ->
                Text(
                    stringResource(
                        R.string.cloud_sales_conflict_difference,
                        diff.field, diff.localValue, diff.cloudValue,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.cloud_sales_conflict_preserved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun PurchaseConflictCard(conflict: PurchaseDocumentsConflict) {
    val typeLabel = when (conflict.documentType) {
        "INVOICE" -> stringResource(R.string.cloud_purchase_conflict_invoice)
        "RETURN" -> stringResource(R.string.cloud_purchase_conflict_return)
        else -> conflict.documentType
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$typeLabel: ${conflict.documentNo}", style = MaterialTheme.typography.titleSmall)
            conflict.differences.forEach { diff ->
                Text(
                    stringResource(
                        R.string.cloud_purchase_conflict_difference,
                        diff.field, diff.localValue, diff.cloudValue,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.cloud_purchase_conflict_preserved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun SupplierPaymentsTreasuryConflictCard(conflict: SupplierPaymentsTreasuryConflict) {
    val typeLabel = when (conflict.documentType) {
        "PAYMENT" -> stringResource(R.string.cloud_supplier_payment_conflict_payment)
        "TREASURY" -> stringResource(R.string.cloud_supplier_payment_conflict_treasury)
        else -> conflict.documentType
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$typeLabel: ${conflict.documentNo}", style = MaterialTheme.typography.titleSmall)
            conflict.differences.forEach { diff ->
                Text(
                    stringResource(R.string.cloud_supplier_payment_conflict_difference, diff.field, diff.localValue, diff.cloudValue),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.cloud_supplier_payment_conflict_preserved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun InventoryProductionConflictCard(conflict: InventoryProductionConflict) {
    val typeLabel = when (conflict.documentType) {
        "PRODUCTION_ORDER" -> stringResource(R.string.cloud_inventory_production_conflict_order)
        "RECIPE" -> stringResource(R.string.cloud_inventory_production_conflict_recipe)
        "INVENTORY_LOT" -> stringResource(R.string.cloud_inventory_production_conflict_inventory)
        else -> conflict.documentType
    }
    val businessCount = conflict.differences.count { it.severity == "BUSINESS" }
    val timingCount = conflict.differences.count { it.severity == "TIMING" }
    val descriptiveCount = conflict.differences.count { it.severity == "DESCRIPTIVE" }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("$typeLabel: ${conflict.documentNo}", style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.cloud_inventory_production_conflict_summary, businessCount, timingCount, descriptiveCount),
                style = MaterialTheme.typography.labelMedium,
                color = if (businessCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            conflict.differences.forEach { diff ->
                val severityLabel = when (diff.severity) {
                    "TIMING" -> stringResource(R.string.cloud_inventory_production_conflict_timing)
                    "DESCRIPTIVE" -> stringResource(R.string.cloud_inventory_production_conflict_descriptive)
                    else -> stringResource(R.string.cloud_inventory_production_conflict_business)
                }
                Text(
                    severityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (diff.severity) {
                        "BUSINESS" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    stringResource(R.string.cloud_inventory_production_conflict_difference, diff.field, diff.localValue, diff.cloudValue),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.cloud_inventory_production_conflict_preserved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private fun formatSyncTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
