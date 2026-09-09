package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionBatchNumberContractTest {
    @Test fun productionServiceHasNoSizeSpecificBatchHardcoding() {
        val f = listOf(
            File("src/main/java/com/fush/erp/domain/ProductionService.kt"),
            File("app/src/main/java/com/fush/erp/domain/ProductionService.kt")
        ).first { it.isFile }
        val source = f.readText()
        assertTrue(source.contains("ProductionBatchNumberPolicy.productPrefix(product.code, product.id)"))
        assertFalse(source.contains("contains(\"200\") -> \"F200\""))
        assertFalse(source.contains("contains(\"60\") -> \"F60\""))
    }
}
