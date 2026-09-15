package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterNameNormalizerTest {
    @Test
    fun normalizesWhitespaceTatweelArabicDiacriticsAndLatinCase() {
        assertEquals("اكلا", MasterNameNormalizer.normalize("  اَكـلا  "))
        assertEquals("galaxy foods", MasterNameNormalizer.normalize("  GALAXY   Foods "))
    }

    @Test
    fun duplicateComparisonUsesTheSameNormalizationRule() {
        assertTrue(MasterNameNormalizer.isDuplicate(" اَكـلا ", listOf("اكلا", "مشروبات")))
        assertTrue(MasterNameNormalizer.isDuplicate("  GALAXY   FOODS ", listOf("galaxy foods")))
        assertFalse(MasterNameNormalizer.isDuplicate("اكلا", listOf("مشروبات", "ألبان")))
    }

    @Test
    fun keepsMateriallyDifferentNamesDifferent() {
        assertNotEquals(
            MasterNameNormalizer.normalize("اكلا"),
            MasterNameNormalizer.normalize("مشروبات")
        )
    }

    @Test
    fun emptyInputNormalizesToEmptyWithoutInventingAName() {
        assertEquals("", MasterNameNormalizer.normalize("   ـَ  "))
    }
}
