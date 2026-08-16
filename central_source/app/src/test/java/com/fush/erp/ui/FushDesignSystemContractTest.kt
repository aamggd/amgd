package com.fush.erp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FushDesignSystemContractTest {
    @Test
    fun spacingScale_isOrderedAndStable() {
        val scale = listOf(
            FushSpacing.none,
            FushSpacing.xxs,
            FushSpacing.xs,
            FushSpacing.sm,
            FushSpacing.md,
            FushSpacing.lg,
            FushSpacing.xl,
            FushSpacing.xxl,
            FushSpacing.xxxl,
        )

        assertEquals(0f, scale.first().value, 0f)
        assertTrue(scale.zipWithNext().all { (left, right) -> left.value < right.value })
        assertEquals(32f, scale.last().value, 0f)
    }

    @Test
    fun sharedInteractiveDimensions_meetAccessibilityFloor() {
        assertTrue(FushDimensions.minTouchTarget.value >= 48f)
        assertTrue(FushDimensions.fieldMinHeight.value >= FushDimensions.minTouchTarget.value)
    }

    @Test
    fun dialogAndBrandDimensions_areStable() {
        assertTrue(FushDimensions.dialogFormMaxHeight.value >= 480f)
        assertEquals(40f, FushDimensions.avatarSize.value, 0f)
        assertEquals(40f, FushDimensions.brandCompactSize.value, 0f)
        assertEquals(52f, FushDimensions.brandSize.value, 0f)
    }
}
