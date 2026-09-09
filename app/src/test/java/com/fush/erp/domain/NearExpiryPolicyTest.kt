package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NearExpiryPolicyTest {
    @Test fun configurable_warning_window_is_respected() {
        assertEquals("منتهي", NearExpiryPolicy.status(-1, 45))
        assertEquals("قريب الانتهاء", NearExpiryPolicy.status(45, 45))
        assertEquals("ساري الصلاحية", NearExpiryPolicy.status(46, 45))
        assertEquals(1, NearExpiryPolicy.normalize(0))
        assertEquals(3650, NearExpiryPolicy.normalize(9000))
    }
}
