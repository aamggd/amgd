from pathlib import Path

ROOT = Path("FushERP_Mobile_Phase5")


def repl(rel: str, old: str, new: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Pattern not found in {rel}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


repl("app/build.gradle.kts", "versionCode = 56", "versionCode = 57")
repl(
    "app/build.gradle.kts",
    'versionName = "0.15.4.17-phase14.5-master-data-controls"',
    'versionName = "0.15.4.18-phase14.5-manual-sales-price"',
)

repl(
    "app/src/main/java/com/fush/erp/domain/SalesMath.kt",
    '        require(line.unitPriceOriginal >= 0.0 && line.unitPriceOriginal.isFinite()) { "سعر البيع غير صالح" }',
    '        require(line.unitPriceOriginal > 0.0 && line.unitPriceOriginal.isFinite()) { "سعر البيع يجب أن يكون أكبر من صفر" }',
)

repl(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '''            val configuredPrice = requireNotNull(\n                db.salesDao().latestPrice(\n                    itemId = line.itemId,\n                    channel = customer.channel,\n                    province = customer.province,\n                    currencyCode = request.currencyCode,\n                    at = request.invoiceDate\n                )\n            ) {\n                "لا توجد قائمة أسعار فعالة وسارية للصنف ${item.nameAr} في ${customer.province} / ${customer.channel} / ${request.currencyCode} بتاريخ الفاتورة"\n            }\n            SalesMath.validateConfiguredUnitPrice(\n                requestedUnitPriceOriginal = line.unitPriceOriginal,\n                baseUnitPriceOriginal = configuredPrice.baseUnitPriceOriginal,\n                factorToBase = conversion.factorToBase\n            )\n''',
    '''            // Phase 14.5.18: manual sales pricing is allowed.\n            // A valid price list is only an optional reference/default, not a posting prerequisite.\n            // The entered unit price is preserved on the invoice line as the historical snapshot.\n''',
)

repl(
    "app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt",
    '''                OutlinedTextField(\n                    value = priceText,\n                    onValueChange = {},\n                    label = { Text("سعر الوحدة من قائمة الأسعار السارية") },\n                    readOnly = true,\n                    singleLine = true\n                )\n                resolvedPrice?.let { price ->\n                    Text(\n                        "القائمة: ${price.province} • ${salesChannelLabel(price.channel)} • من ${salesDate(price.effectiveFrom)}${price.effectiveTo?.let { " إلى ${salesDate(it)}" } ?: ""}",\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.primary\n                    )\n                } ?: Text(\n                    "لا توجد قائمة أسعار فعالة وسارية لهذه المحافظة/القناة/العملة. أضف القائمة قبل البيع.",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.error\n                )\n''',
    '''                OutlinedTextField(\n                    value = priceText,\n                    onValueChange = { priceText = it },\n                    label = { Text("سعر الوحدة — إدخال يدوي أو سعر مقترح") },\n                    singleLine = true\n                )\n                resolvedPrice?.let { price ->\n                    Text(\n                        "السعر الظاهر مقترح من القائمة: ${price.province} • ${salesChannelLabel(price.channel)} • من ${salesDate(price.effectiveFrom)}${price.effectiveTo?.let { " إلى ${salesDate(it)}" } ?: ""}. يمكنك تعديله يدويًا قبل إضافة السطر.",\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.primary\n                    )\n                } ?: Text(\n                    "لا توجد قائمة أسعار سارية لهذه المحافظة/القناة/العملة؛ يمكنك إدخال سعر البيع يدويًا.",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.secondary\n                )\n''',
)

repl(
    "app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt",
    '''                        requireNotNull(resolvedPrice) { "لا توجد قائمة أسعار فعالة وسارية لهذا الصنف" }\n                        val p = requireNotNull(priceText.toDoubleOrNull()) { "سعر قائمة البيع غير متاح" }\n                        require(q > 0 && p > 0) { "الكمية والسعر غير صالحين" }''',
    '''                        val p = requireNotNull(priceText.toDoubleOrNull()) { "أدخل سعر بيع صحيح" }\n                        require(q > 0 && p > 0) { "الكمية والسعر يجب أن يكونا أكبر من صفر" }''',
)

repl(
    "app/src/test/java/com/fush/erp/domain/SalesMathTest.kt",
    '''    @Test\n    fun configuredUnitPriceMustMatchPriceListAndUnitFactor() {\n        SalesMath.validateConfiguredUnitPrice(24_000.0, 1_000.0, 24.0)\n        assertThrows(IllegalArgumentException::class.java) {\n            SalesMath.validateConfiguredUnitPrice(23_999.0, 1_000.0, 24.0)\n        }\n    }\n''',
    '''    @Test\n    fun manualSalesPriceMayDifferFromConfiguredReference() {\n        val manual = SalesDraftLine(1, 1, 2.0, 24.0, 23_500.0)\n        SalesMath.validateLine(manual)\n        assertEquals(47_000.0, manual.grossOriginal, 0.0001)\n    }\n\n    @Test\n    fun zeroManualSalesPriceIsRejected() {\n        assertThrows(IllegalArgumentException::class.java) {\n            SalesMath.validateLine(SalesDraftLine(1, 1, 1.0, 1.0, 0.0))\n        }\n    }\n''',
)

obsolete = ROOT / "app/src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt.orig"
if obsolete.exists():
    obsolete.unlink()

(ROOT / "PHASE14_5_18_SCOPE.md").write_text(
    """# Phase 14.5.18 — Manual Sales Price\n\n"
    "- Sales price lists remain available as optional reference/default prices.\n"
    "- The sales invoice unit-price field is editable.\n"
    "- If a valid list exists, its converted unit price is prefilled as a suggestion and can be changed.\n"
    "- If no valid price list exists, the user can enter a manual price and continue the sale.\n"
    "- The posted invoice preserves the entered unit price as the historical snapshot.\n"
    "- Fush 60 ml below-floor protection remains enforced and still requires documented approval.\n"
    "- Zero/negative manual prices are rejected.\n"
    "- Room schema remains 23; no migration is required.\n"
    "- Removed obsolete AdvancedInventoryScreens.kt.orig from packaged source.\n"
    """,
    encoding="utf-8",
)

print("patched")
