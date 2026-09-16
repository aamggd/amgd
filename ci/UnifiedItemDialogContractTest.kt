package com.fush.erp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedItemDialogContractTest {
    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Source file not found: $path")
    }

    @Test
    fun searchableSelectionSupportsInlineCreateFromTypedQuery() {
        val selector = source("src/main/java/com/fush/erp/ui/FushSearchableSelectionField.kt")
        assertTrue(selector.contains("onCreateNew: ((String) -> Unit)? = null"))
        assertTrue(selector.contains("createNewLabel: (String) -> String"))
        assertTrue(selector.contains("onCreateNew(effectiveSearch.trim())"))
        assertTrue(selector.contains("+ إضافة"))
    }

    @Test
    fun unifiedDialogContainsOneScreenOperationalItemFieldsAndQuickMasters() {
        val dialog = source("src/main/java/com/fush/erp/ui/screens/UnifiedItemDialog.kt")
        assertTrue(dialog.contains("fun UnifiedItemDialog("))
        assertTrue(dialog.contains("اسم الصنف"))
        assertTrue(dialog.contains("القسم"))
        assertTrue(dialog.contains("الشركة / العلامة التجارية"))
        assertTrue(dialog.contains("الوحدة الأساسية"))
        assertTrue(dialog.contains("الباركود"))
        assertTrue(dialog.contains("سعر الشراء المرجعي"))
        assertTrue(dialog.contains("سعر البيع"))
        assertTrue(dialog.contains("اختيار صورة"))
        assertTrue(dialog.contains("createCategoryQuick"))
        assertTrue(dialog.contains("createBrandQuick"))
        assertTrue(dialog.contains("masterDataService.createUnit"))
        assertTrue(dialog.contains("productMasterService.createUnifiedItem"))
        assertTrue(dialog.contains("UnifiedItemCreateRequest("))
        assertTrue(dialog.contains("rememberLauncherForActivityResult"))
        assertTrue(dialog.contains("ActivityResultContracts.OpenDocument"))
        assertTrue(dialog.contains("takePersistableUriPermission"))
    }

    @Test
    fun masterDataScreenRoutesAddItemToUnifiedDialogAndKeepsLegacyEditorOnlyForExistingItems() {
        val home = source("src/main/java/com/fush/erp/ui/screens/HomeShell.kt")
        assertTrue(home.contains("observeCategories()"))
        assertTrue(home.contains("observeBrands()"))
        assertTrue(home.contains("UnifiedItemDialog("))
        assertTrue(home.contains("تمت إضافة الصنف"))
        assertFalse(home.contains("AddItemDialog(activeUnits"))
        assertTrue(home.contains("EditItemDialog("))
    }
}
