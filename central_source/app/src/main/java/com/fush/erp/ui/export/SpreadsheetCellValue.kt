package com.fush.erp.ui.export

enum class SpreadsheetCellKind { TEXT, NUMBER, CURRENCY }

data class SpreadsheetCellValue(
    val kind: SpreadsheetCellKind,
    val number: Double? = null
)

private val currencyCellRegex = Regex("^([+-]?(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?)\\s*ر\\.ي$")
private val numberCellRegex = Regex("^[+-]?(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?$")

fun parseSpreadsheetCell(raw: String): SpreadsheetCellValue {
    val value = raw.trim()
    currencyCellRegex.matchEntire(value)?.let { match ->
        val parsed = match.groupValues[1].replace(",", "").toDoubleOrNull()
        if (parsed != null) return SpreadsheetCellValue(SpreadsheetCellKind.CURRENCY, parsed)
    }
    if (numberCellRegex.matches(value)) {
        val unsigned = value.removePrefix("+").removePrefix("-").replace(",", "")
        val integerPart = unsigned.substringBefore('.')
        val preservesLeadingZero = integerPart.length > 1 && integerPart.startsWith('0')
        if (!preservesLeadingZero) {
            value.replace(",", "").toDoubleOrNull()?.let {
                return SpreadsheetCellValue(SpreadsheetCellKind.NUMBER, it)
            }
        }
    }
    return SpreadsheetCellValue(SpreadsheetCellKind.TEXT)
}
