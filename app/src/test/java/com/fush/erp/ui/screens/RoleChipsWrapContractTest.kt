package com.fush.erp.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleChipsWrapContractTest {
    @Test
    fun roleAndQuickSessionChipsUseWrappingFlowLayout() {
        val source = sourceFile("com/fush/erp/ui/screens/SecurityScreens.kt").readText()
        val helper = functionBlock(source, "private fun FlowRowCompat(")

        assertTrue("FlowRowCompat must use Compose FlowRow", helper.contains("FlowRow("))
        assertTrue("FlowRowCompat must wrap vertically with spacing", helper.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
        assertFalse("FlowRowCompat must not regress to a single horizontal Row", helper.contains("Row(Modifier.fillMaxWidth()"))
        assertTrue("roles must still be rendered through FlowRowCompat", source.contains("FlowRowCompat {\n                roles.forEach"))
    }

    private fun functionBlock(source: String, marker: String): String {
        val start = source.indexOf(marker)
        require(start >= 0) { "Missing function $marker" }
        val end = source.indexOf("\nprivate fun moduleLabel", start).takeIf { it >= 0 } ?: source.length
        return source.substring(start, end)
    }

    private fun sourceFile(relative: String): File {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }
}
