package com.fush.erp.bootstrap

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootstrapInstrumentedTest {
    @Test fun value_isStable() {
        assertEquals("FUSH-v112", Bootstrap.value())
    }
}
