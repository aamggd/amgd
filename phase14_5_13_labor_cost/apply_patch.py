from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')

def replace(path: str, old: str, new: str, count: int = 1) -> None:
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing expected text in {path}: {old[:120]!r}')
    text2 = text.replace(old, new, count)
    p.write_text(text2, encoding='utf-8')

replace('app/build.gradle.kts', 'versionCode = 51', 'versionCode = 52')
replace(
    'app/build.gradle.kts',
    'versionName = "0.15.4.12-phase14.5-count-missing-lines"',
    'versionName = "0.15.4.13-phase14.5-labor-cost"'
)

replace(
    'app/src/main/java/com/fush/erp/data/entity/ProductionEntities.kt',
    'val directLaborCostBase: Double = 30000.0,',
    'val directLaborCostBase: Double = 0.0,'
)

replace(
    'app/src/main/java/com/fush/erp/domain/ProductionMath.kt',
    '''    fun actualUnitCost(materialCost: Double, laborCost: Double, acceptedQty: Double {\n        require(materialCost >= 0.0 && materialCost.isFinite()) { "تكلفة المواد غير صالحة" }\n        require(laborCost >= 0.0 && laborCost.isFinite()) { "تكلفة العمالة غير صالحة" }\n        require(acceptedQty > 0.0 && acceptedQty.isFinite()) { "الكمية المقبولة يجب أن تكون أكبر من صفر" }\n        return (materialCost + laborCost) / acceptedQty\n    }\n'''.replace('Double {', 'Double) {'),
    '''    fun validateDirectLaborCost(laborCost: Double): Double {\n        require(laborCost >= 0.0 && laborCost.isFinite()) { "تكلفة العمالة غير صالحة" }\n        return laborCost\n    }\n\n    fun parseDirectLaborCostInput(input: String): Double {\n        require(input.isNotBlank()) { "يجب إدخال أجور هذه الدفعة؛ لا توجد قيمة عمالة ثابتة" }\n        val value = input.trim().toDoubleOrNull()\n            ?: throw IllegalArgumentException("تكلفة العمالة غير صالحة")\n        return validateDirectLaborCost(value)\n    }\n\n    fun actualUnitCost(materialCost: Double, laborCost: Double, acceptedQty: Double): Double {\n        require(materialCost >= 0.0 && materialCost.isFinite()) { "تكلفة المواد غير صالحة" }\n        validateDirectLaborCost(laborCost)\n        require(acceptedQty > 0.0 && acceptedQty.isFinite()) { "الكمية المقبولة يجب أن تكون أكبر من صفر" }\n        return (materialCost + laborCost) / acceptedQty\n    }\n'''
)

replace(
    'app/src/main/java/com/fush/erp/domain/ProductionService.kt',
    'require(directLaborCostBase >= 0.0 && directLaborCostBase.isFinite()) { "تكلفة العمالة غير صالحة" }',
    'ProductionMath.validateDirectLaborCost(directLaborCostBase)'
)

replace(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    'var laborText by remember { mutableStateOf("30000") }',
    'var laborText by remember { mutableStateOf("") }'
)

replace(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    'OutlinedTextField(laborText, { laborText = it }, label = { Text("أجور الدفعة بالريال") }, singleLine = true)',
    '''OutlinedTextField(\n                    laborText,\n                    { laborText = it },\n                    label = { Text("أجور هذه الدفعة بالريال") },\n                    supportingText = { Text("أدخل التكلفة الفعلية أو المتفق عليها لهذه الدفعة؛ لا توجد قيمة 30,000 ثابتة في النظام.") },\n                    singleLine = true\n                )'''
)

replace(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''enabled = recipe != null && raw != null && finished != null && (asset == null || operator != null) && outputText.toDoubleOrNull()?.let { it > 0 } == true && laborText.toDoubleOrNull()?.let { it >= 0 } == true,\n                onClick = { onSave(recipe!!, raw!!, finished!!, asset, operator, outputText.toDouble(), laborText.toDouble(), notes) }''',
    '''enabled = recipe != null && raw != null && finished != null && (asset == null || operator != null) && outputText.toDoubleOrNull()?.let { it > 0 } == true && runCatching { com.fush.erp.domain.ProductionMath.parseDirectLaborCostInput(laborText) }.isSuccess,\n                onClick = { onSave(recipe!!, raw!!, finished!!, asset, operator, outputText.toDouble(), com.fush.erp.domain.ProductionMath.parseDirectLaborCostInput(laborText), notes) }'''
)

test_path = ROOT / 'app/src/test/java/com/fush/erp/domain/ProductionMathTest.kt'
test_text = test_path.read_text(encoding='utf-8')
insert_marker = '\n}\n'
if 'labor cost input requires explicit per batch value' not in test_text:
    extra = '''\n    @Test\n    fun `labor cost input requires explicit per batch value`() {\n        assertThrows(IllegalArgumentException::class.java) {\n            ProductionMath.parseDirectLaborCostInput("   ")\n        }\n    }\n\n    @Test\n    fun `labor cost accepts arbitrary per batch values including zero`() {\n        assertEquals(12_750.0, ProductionMath.parseDirectLaborCostInput("12750"), 1e-9)\n        assertEquals(0.0, ProductionMath.parseDirectLaborCostInput("0"), 1e-9)\n    }\n\n    @Test\n    fun `labor cost rejects negative per batch value`() {\n        assertThrows(IllegalArgumentException::class.java) {\n            ProductionMath.parseDirectLaborCostInput("-1")\n        }\n    }\n'''
    pos = test_text.rfind(insert_marker)
    if pos < 0:
        raise SystemExit('could not find ProductionMathTest class end')
    test_text = test_text[:pos] + extra + test_text[pos:]
    test_path.write_text(test_text, encoding='utf-8')

scope = ROOT / 'PHASE14_5_13_SCOPE.md'
scope.write_text('''# Fush ERP Phase 14.5.13 — Per-Batch Direct Labor Cost\n\n- Removes the hidden 30,000 YER default from production order data and UI.\n- Requires the user to explicitly enter direct labor cost for every new production order/batch.\n- Allows any valid non-negative amount, including zero.\n- Existing orders retain their stored labor cost; no schema migration is needed.\n- Direct labor remains part of actual production cost and WIP posting exactly as before.\n- Adds domain validation and tests for blank, arbitrary and negative labor-cost inputs.\n''', encoding='utf-8')

entity = (ROOT / 'app/src/main/java/com/fush/erp/data/entity/ProductionEntities.kt').read_text(encoding='utf-8')
ui = (ROOT / 'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt').read_text(encoding='utf-8')
assert 'directLaborCostBase: Double = 30000.0' not in entity
assert 'mutableStateOf("30000")' not in ui
assert 'versionCode = 52' in (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
print('Phase 14.5.13 patch applied successfully')
