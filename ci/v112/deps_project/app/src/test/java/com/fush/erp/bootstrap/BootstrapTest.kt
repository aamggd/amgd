package com.fush.erp.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapTest {
    @Test fun value_isStable() {
        assertEquals("FUSH-v112", Bootstrap.value())
    }
}
