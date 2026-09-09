package com.fush.erp.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogScrollSafetyContractTest {
    private val inputTokens = listOf(
        "OutlinedTextField(",
        "TextField(",
        "FushDateField(",
        "DateField(",
        "SelectionField(",
        "PartySelectionField(",
        "FushSearchableSelectionField(",
    )

    @Test
    fun multiInputAlertDialogsAreScrollable() {
        val screensDir = listOf(
            File("src/main/java/com/fush/erp/ui/screens"),
            File("app/src/main/java/com/fush/erp/ui/screens"),
        ).firstOrNull { it.isDirectory }
            ?: error("Cannot locate ui/screens source directory from ${File(".").absolutePath}")

        val violations = mutableListOf<String>()
        screensDir.listFiles { file -> file.extension == "kt" }.orEmpty().forEach { file ->
            val source = file.readText()
            alertDialogBlocks(source).forEach { (line, block) ->
                val inputCount = inputTokens.sumOf { token -> block.windowed(token.length, 1).count { it == token } }
                if (inputCount >= 3) {
                    val scrollSafe = block.contains("FushDialogForm") ||
                        block.contains("verticalScroll(") ||
                        block.contains("LazyColumn")
                    if (!scrollSafe) violations += "${file.name}:$line inputs=$inputCount"
                }
            }
        }

        assertTrue(
            "Multi-input AlertDialogs must be scroll-safe. Violations: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }

    private fun alertDialogBlocks(source: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var searchFrom = 0
        while (true) {
            val start = source.indexOf("AlertDialog(", searchFrom)
            if (start < 0) break
            val openParen = source.indexOf('(', start)
            var depth = 0
            var cursor = openParen
            var end = -1
            while (cursor < source.length) {
                when (source[cursor]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            end = cursor + 1
                            break
                        }
                    }
                }
                cursor++
            }
            if (end <= start) break
            val line = source.substring(0, start).count { it == '\n' } + 1
            result += line to source.substring(start, end)
            searchFrom = end
        }
        return result
    }
}
