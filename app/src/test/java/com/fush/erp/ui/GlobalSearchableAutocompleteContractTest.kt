package com.fush.erp.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchableAutocompleteContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun centralComponentSupportsTypedSearchAndRecordSelection() {
        val component = source("com/fush/erp/ui/FushSearchableSelectionField.kt")
        assertTrue(component.contains("fun <T> FushSearchableSelectionField"))
        assertTrue(component.contains("searchTerms(option)"))
        assertTrue(component.contains("contains(effectiveSearch)"))
        assertTrue(component.contains("onSelected(option)"))
        assertTrue(component.contains("maxResults"))
    }

    @Test
    fun majorLegacyDynamicSelectorsDelegateToCentralSearchableComponent() {
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val sales = source("com/fush/erp/ui/screens/SalesScreens.kt")
        val expense = source("com/fush/erp/ui/screens/ExpenseScreens.kt")
        val employee = source("com/fush/erp/ui/screens/EmployeeScreens.kt")
        val party = source("com/fush/erp/ui/screens/PartyScreens.kt")
        assertTrue(home.substring(home.indexOf("fun <T> SelectionField"), home.indexOf("fun StringSelectionField")).contains("FushSearchableSelectionField"))
        assertTrue(sales.substring(sales.indexOf("private fun <T> SalesSelectionField"), sales.indexOf("private fun SalesStringSelectionField")).contains("FushSearchableSelectionField"))
        assertTrue(expense.contains("private fun <T> ExpenseSelectionField") && expense.contains("FushSearchableSelectionField"))
        assertTrue(employee.contains("private fun <T> HrSelectionField") && employee.contains("FushSearchableSelectionField"))
        assertTrue(party.contains("private fun <T> PartySelectionField") && party.contains("FushSearchableSelectionField"))
    }

    @Test
    fun collectionCustomerSearchIncludesNameCodePhoneProvinceAndStrictSelection() {
        val sales = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(sales.contains("customer.nameAr"))
        assertTrue(sales.contains("customer.nameEn"))
        assertTrue(sales.contains("customer.code"))
        assertTrue(sales.contains("customer.phone"))
        assertTrue(sales.contains("customer.province"))
        assertTrue(sales.contains("النص المكتوب وحده لا يعتبر اختياراً"))
    }

    @Test
    fun collectionUiExposesDateDiscountReasonAndPassesThemToDomain() {
        val sales = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(sales.contains("label = \"تاريخ التحصيل\"") || sales.contains("\"تاريخ التحصيل\", modifier"))
        assertTrue(sales.contains("خصم مسموح به للعميل"))
        assertTrue(sales.contains("سبب الخصم — إلزامي"))
        assertTrue(sales.contains("receiptDate = selectedDate"))
        assertTrue(sales.contains("discountOriginal = discountOriginal"))
        assertTrue(sales.contains("discountReason = discountReason"))
    }
    @Test
    fun remainingDynamicEntitySelectorsUseCentralSearchableComponent() {
        val files = listOf(
            "com/fush/erp/ui/screens/SalesRepresentativeScreens.kt",
            "com/fush/erp/ui/screens/GeographyScreens.kt",
            "com/fush/erp/ui/screens/AdvancedInventoryScreens.kt",
            "com/fush/erp/ui/screens/PlanningScreen.kt",
            "com/fush/erp/ui/screens/ReportsScreen.kt",
            "com/fush/erp/ui/screens/ProductionScreens.kt",
            "com/fush/erp/ui/screens/RiskControlScreen.kt",
            "com/fush/erp/ui/screens/SecurityScreens.kt",
        )
        files.forEach { path ->
            assertTrue("Expected central searchable selector in $path", source(path).contains("FushSearchableSelectionField"))
        }
    }

    @Test
    fun searchableMenusKeepEditableFocusAndBoundPopupHeight() {
        val component = source("com/fush/erp/ui/FushSearchableSelectionField.kt")
        val sales = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(component.contains("ExposedDropdownMenuAnchorType.PrimaryEditable"))
        assertTrue(component.contains("heightIn(max = 300.dp)"))
        assertTrue(sales.contains("ExposedDropdownMenuAnchorType.PrimaryEditable"))
        assertTrue(sales.contains("heightIn(max = 300.dp)"))
        assertTrue(sales.contains("matches.take(30)"))
    }

}
