#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)

# 1) Extend the central searchable selector with an optional inline-create row.
rel = "app/src/main/java/com/fush/erp/ui/FushSearchableSelectionField.kt"
text = read(rel)
text = replace_once(
    text,
    '''    onClearSelected: (() -> Unit)? = null,
    maxResults: Int = 30,
) {''',
    '''    onClearSelected: (() -> Unit)? = null,
    maxResults: Int = 30,
    onCreateNew: ((String) -> Unit)? = null,
    createNewLabel: (String) -> String = { "+ إضافة $it" },
) {''',
    "selector create parameters",
)
text = replace_once(
    text,
    '''    val normalized = query.trim().lowercase(Locale.ROOT)
    val selectedNormalized = selectedText.trim().lowercase(Locale.ROOT)
    val effectiveSearch = if (selectedText.isNotBlank() && normalized == selectedNormalized) "" else normalized
    val filtered = remember(options, effectiveSearch, maxResults) {
        val base = if (effectiveSearch.isBlank()) options else options.filter { option ->
            searchTerms(option).any { term -> term.lowercase(Locale.ROOT).contains(effectiveSearch) }
        }
        base.take(maxResults.coerceAtLeast(1))
    }
''',
    '''    val normalized = query.trim().lowercase(Locale.ROOT)
    val selectedNormalized = selectedText.trim().lowercase(Locale.ROOT)
    val effectiveSearch = if (selectedText.isNotBlank() && normalized == selectedNormalized) "" else query
    val normalizedSearch = effectiveSearch.trim().lowercase(Locale.ROOT)
    val filtered = remember(options, normalizedSearch, maxResults) {
        val base = if (normalizedSearch.isBlank()) options else options.filter { option ->
            searchTerms(option).any { term -> term.lowercase(Locale.ROOT).contains(normalizedSearch) }
        }
        base.take(maxResults.coerceAtLeast(1))
    }
    val canCreateNew = onCreateNew != null && effectiveSearch.trim().isNotBlank() && options.none { option ->
        optionText(option).trim().equals(effectiveSearch.trim(), ignoreCase = true)
    }
''',
    "selector search state",
)
text = replace_once(
    text,
    '''            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("لا توجد نتائج مطابقة") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                filtered.forEach { option ->''',
    '''            if (canCreateNew) {
                DropdownMenuItem(
                    text = { Text(createNewLabel(effectiveSearch.trim()), color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        onCreateNew(effectiveSearch.trim())
                        expanded = false
                    },
                )
                if (filtered.isNotEmpty()) HorizontalDivider()
            }
            if (filtered.isEmpty() && !canCreateNew) {
                DropdownMenuItem(
                    text = { Text("لا توجد نتائج مطابقة") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                filtered.forEach { option ->''',
    "selector create row",
)
write(rel, text)

# 2) One-screen item creation dialog. Quick master creation returns and selects immediately.
write(
    "app/src/main/java/com/fush/erp/ui/screens/UnifiedItemDialog.kt",
    r'''package com.fush.erp.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.BrandEntity
import com.fush.erp.data.entity.ProductCategoryEntity
import com.fush.erp.data.entity.ProductVariantEntity
import com.fush.erp.data.entity.UnitEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.UnifiedItemCreateRequest
import com.fush.erp.ui.FushSearchableSelectionField
import kotlinx.coroutines.launch

@Composable
internal fun UnifiedItemDialog(
    container: AppContainer,
    user: UserEntity,
    categories: List<ProductCategoryEntity>,
    brands: List<BrandEntity>,
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onCreated: (ProductVariantEntity) -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ProductCategoryEntity?>(null) }
    var brand by remember { mutableStateOf<BrandEntity?>(null) }
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var barcode by remember { mutableStateOf("") }
    var referencePurchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var reorderLevel by remember { mutableStateOf("0") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val activeCategories = categories.filter { it.isActive }
    val activeBrands = brands.filter { it.isActive }
    val activeUnits = units.filter { it.isActive }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imageUri = uri.toString()
        }
    }

    fun quickCategory(typed: String) {
        scope.launch {
            runCatching { container.productMasterService.createCategoryQuick(typed, user.id) }
                .onSuccess { category = it; onMessage("تمت إضافة القسم ${it.nameAr}") }
                .onFailure { onMessage(it.message ?: "تعذر إضافة القسم") }
        }
    }

    fun quickBrand(typed: String) {
        scope.launch {
            runCatching { container.productMasterService.createBrandQuick(typed, user.id) }
                .onSuccess { brand = it; onMessage("تمت إضافة الشركة / العلامة ${it.nameAr}") }
                .onFailure { onMessage(it.message ?: "تعذر إضافة العلامة التجارية") }
        }
    }

    fun quickUnit(typed: String) {
        scope.launch {
            runCatching { container.masterDataService.createUnit(typed, createdBy = user.id) }
                .onSuccess { unit = it; onMessage("تمت إضافة الوحدة ${it.nameAr}") }
                .onFailure { onMessage(it.message ?: "تعذر إضافة الوحدة") }
        }
    }

    val purchaseValue = referencePurchasePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val saleValue = salePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0
    val reorderValue = reorderLevel.trim().toDoubleOrNull()
    val canSave = nameAr.isNotBlank() && category != null && brand != null && unit != null &&
        (referencePurchasePrice.isBlank() || (purchaseValue != null && purchaseValue >= 0.0)) &&
        (salePrice.isBlank() || (salePrice.toDoubleOrNull() != null && saleValue >= 0.0)) &&
        reorderValue != null && reorderValue >= 0.0 && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("إضافة صنف جديد") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "كل بيانات الصنف في شاشة واحدة. يمكنك إضافة القسم أو الشركة أو الوحدة من نفس الحقل دون مغادرة النموذج.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم الصنف") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الاسم الإنجليزي - اختياري") },
                    singleLine = true,
                )
                FushSearchableSelectionField(
                    label = "القسم",
                    selectedText = category?.nameAr.orEmpty(),
                    options = activeCategories,
                    optionText = { it.nameAr },
                    searchTerms = { listOf(it.nameAr, it.nameEn, it.code) },
                    onSelected = { category = it },
                    onCleared = { category = null },
                    onCreateNew = ::quickCategory,
                    createNewLabel = { "+ إضافة $it كقسم جديد" },
                )
                FushSearchableSelectionField(
                    label = "الشركة / العلامة التجارية",
                    selectedText = brand?.nameAr.orEmpty(),
                    options = activeBrands,
                    optionText = { it.nameAr },
                    searchTerms = { listOf(it.nameAr, it.nameEn, it.manufacturerName, it.code) },
                    onSelected = { brand = it },
                    onCleared = { brand = null },
                    onCreateNew = ::quickBrand,
                    createNewLabel = { "+ إضافة $it كشركة / علامة جديدة" },
                )
                FushSearchableSelectionField(
                    label = "الوحدة الأساسية",
                    selectedText = unit?.nameAr.orEmpty(),
                    options = activeUnits,
                    optionText = { it.nameAr },
                    searchTerms = { listOf(it.nameAr, it.nameEn, it.code) },
                    onSelected = { unit = it },
                    onCleared = { unit = null },
                    onCreateNew = ::quickUnit,
                    createNewLabel = { "+ إضافة $it كوحدة جديدة" },
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الباركود - اختياري") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = referencePurchasePrice,
                    onValueChange = { referencePurchasePrice = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("سعر الشراء المرجعي - اختياري") },
                    supportingText = { Text("مرجعي فقط؛ لا ينشئ تكلفة مخزون أو قيدًا محاسبيًا") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("سعر البيع - اختياري") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reorderLevel,
                    onValueChange = { reorderLevel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد إعادة الطلب") },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (imageUri == null) "اختيار صورة" else "تغيير الصورة") }
                imageUri?.let {
                    Text("تم اختيار صورة للصنف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val selectedCategory = category ?: return@Button
                    val selectedBrand = brand ?: return@Button
                    val selectedUnit = unit ?: return@Button
                    val reorder = reorderValue ?: return@Button
                    saving = true
                    scope.launch {
                        runCatching {
                            container.productMasterService.createUnifiedItem(
                                UnifiedItemCreateRequest(
                                    nameAr = nameAr,
                                    nameEn = nameEn,
                                    categoryId = selectedCategory.id,
                                    brandId = selectedBrand.id,
                                    baseUnitId = selectedUnit.id,
                                    barcode = barcode.ifBlank { null },
                                    referencePurchasePrice = purchaseValue,
                                    salePrice = saleValue,
                                    imageUri = imageUri,
                                    reorderLevel = reorder,
                                ),
                                createdBy = user.id,
                            )
                        }.onSuccess(onCreated)
                            .onFailure { onMessage(it.message ?: "تعذر حفظ الصنف") }
                        saving = false
                    }
                },
            ) { Text(if (saving) "جاري الحفظ..." else "حفظ الصنف") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } },
    )
}
'''
)

# 3) Route the existing Add Item action to the unified dialog and observe quick-master lists.
rel = "app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt"
text = read(rel)
text = replace_once(
    text,
    '''    val units by container.db.unitDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
''',
    '''    val units by container.db.unitDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
    val productCategories by container.db.productMasterDao().observeCategories().collectAsState(initial = emptyList())
    val productBrands by container.db.productMasterDao().observeBrands().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAllIncludingInactive().collectAsState(initial = emptyList())
''',
    "master list observers",
)
text = replace_once(
    text,
    '''        when (section) {
            "هيكل المنتجات" -> Unit
            "المواد والأصناف" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { showItem = true }, modifier = Modifier.weight(1f), enabled = activeUnits.isNotEmpty()) { Text("إضافة صنف") }
                OutlinedButton(onClick = { showConversion = true }, modifier = Modifier.weight(1f), enabled = activeItems.isNotEmpty() && activeUnits.isNotEmpty()) { Text("إضافة تحويل") }
            }
''',
    '''        when (section) {
            "هيكل المنتجات" -> Button(onClick = { showItem = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة صنف من شاشة واحدة") }
            "المواد والأصناف" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { showItem = true }, modifier = Modifier.weight(1f)) { Text("إضافة صنف") }
                OutlinedButton(onClick = { showConversion = true }, modifier = Modifier.weight(1f), enabled = activeItems.isNotEmpty() && activeUnits.isNotEmpty()) { Text("إضافة تحويل") }
            }
''',
    "unified add item buttons",
)
old_dialog = '''    if (showItem) {
        AddItemDialog(units = activeUnits, onDismiss = { showItem = false }) { nameAr, nameEn, category, baseUnit, reorder, shelfLife, lotTracked, expiryTracked ->
            runAction {
                val item = container.masterDataService.createItem(nameAr, nameEn, category, baseUnit.id, reorder, shelfLife, lotTracked, expiryTracked, user.id)
                showItem = false
                "تمت إضافة ${item.nameAr} بالكود ${item.code}"
            }
        }
    }
'''
new_dialog = '''    if (showItem) {
        UnifiedItemDialog(
            container = container,
            user = user,
            categories = productCategories,
            brands = productBrands,
            units = units,
            onDismiss = { showItem = false },
            onCreated = { variant ->
                showItem = false
                message = "تمت إضافة الصنف ${variant.nameAr} بالكود ${variant.sku}"
            },
            onMessage = { message = it },
        )
    }
'''
text = replace_once(text, old_dialog, new_dialog, "unified dialog route")
write(rel, text)

print("GATE1_TASK4_PATCH_APPLIED=PASS")
