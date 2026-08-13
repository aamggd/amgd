from pathlib import Path

ROOT = Path("FushERP_Mobile_Phase5")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Patch context missing in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def require_contains(path: Path, text: str) -> None:
    if text not in path.read_text(encoding="utf-8"):
        raise SystemExit(f"Verification failed: {path} missing {text!r}")


def main() -> None:
    build = ROOT / "app/build.gradle.kts"
    prod_math = ROOT / "app/src/main/java/com/fush/erp/domain/ProductionMath.kt"
    prod_service = ROOT / "app/src/main/java/com/fush/erp/domain/ProductionService.kt"
    prod_test = ROOT / "app/src/test/java/com/fush/erp/domain/ProductionMathTest.kt"

    require_contains(build, "versionCode = 49")
    require_contains(build, 'versionName = "0.15.4.10-phase14.5-partial-purchase-returns"')
    require_contains(ROOT / "app/src/main/java/com/fush/erp/data/FushDatabase.kt", "version = 21")

    replace_once(build, "versionCode = 49", "versionCode = 50")
    replace_once(
        build,
        'versionName = "0.15.4.10-phase14.5-partial-purchase-returns"',
        'versionName = "0.15.4.11-phase14.5-production-issue-guard"',
    )

    replace_once(
        prod_math,
        """data class ProductionCostCorrectionSplit(\n    val inventoryReductionBase: Double,\n    val cogsReductionBase: Double\n)\n""",
        """data class ProductionCostCorrectionSplit(\n    val inventoryReductionBase: Double,\n    val cogsReductionBase: Double\n)\n\ndata class ProductionBomLink(\n    val recipeComponentId: Long,\n    val itemId: Long\n)\n""",
    )

    replace_once(
        prod_math,
        """    fun splitAcceptedBatchCostCorrection(\n""",
        """    fun validateBomIntegrity(recipeComponents: List<ProductionBomLink>, orderMaterials: List<ProductionBomLink>) {\n        require(recipeComponents.isNotEmpty()) { \"الوصفة لا تحتوي مكونات\" }\n        require(orderMaterials.isNotEmpty()) { \"أمر الإنتاج لا يحتوي مواد\" }\n        require(recipeComponents.map { it.recipeComponentId }.distinct().size == recipeComponents.size) {\n            \"الوصفة تحتوي مكونات مكررة أو غير صالحة\"\n        }\n        require(orderMaterials.map { it.recipeComponentId }.distinct().size == orderMaterials.size) {\n            \"أمر الإنتاج يحتوي سطراً مكرراً لنفس مكون الوصفة\"\n        }\n        val expected = recipeComponents.associate { it.recipeComponentId to it.itemId }\n        val actual = orderMaterials.associate { it.recipeComponentId to it.itemId }\n        require(expected == actual) {\n            \"مواد أمر الإنتاج لا تطابق مكونات الـBOM المعتمدة؛ لا يمكن حجز أو صرف مادة خارج الوصفة\"\n        }\n    }\n\n    fun validateIssueLotTracking(\n        lotTracked: Boolean,\n        expiryTracked: Boolean,\n        lotNo: String?,\n        expiryDate: Long?\n    ) {\n        if (lotTracked) require(!lotNo.isNullOrBlank()) {\n            \"هذا الصنف يتطلب رقم تشغيلة (Lot) قبل صرفه للإنتاج\"\n        }\n        if (expiryTracked) require(expiryDate != null && expiryDate > 0L) {\n            \"هذا الصنف يتطلب تاريخ صلاحية صالح قبل صرفه للإنتاج\"\n        }\n    }\n\n    fun splitAcceptedBatchCostCorrection(\n""",
    )

    replace_once(
        prod_service,
        """        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.isNotEmpty()) { \"لا توجد مواد في أمر الإنتاج\" }\n        val availability = materialAvailability(orderId)\n""",
        """        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.isNotEmpty()) { \"لا توجد مواد في أمر الإنتاج\" }\n        validateOrderBomIntegrity(order, materials)\n        val availability = materialAvailability(orderId)\n""",
    )

    replace_once(
        prod_service,
        """        val materials = db.productionDao().materialsForOrder(orderId)\n        var totalIssueCost = 0.0\n\n        materials.forEach { material ->\n""",
        """        val materials = db.productionDao().materialsForOrder(orderId)\n        validateOrderBomIntegrity(order, materials)\n        var totalIssueCost = 0.0\n\n        materials.forEach { material ->\n""",
    )

    replace_once(
        prod_service,
        """            val balanceNow = AdvancedInventoryService(db).usableBalance(order.rawWarehouseId, material.itemId)\n            require(balanceNow + 1e-9 >= needed) { \"المخزون المقبول وغير المنتهي لا يكفي لإتمام صرف المواد\" }\n\n            var remaining = needed\n            var materialCost = 0.0\n            val lots = AdvancedInventoryService(db).usableLots(order.rawWarehouseId, material.itemId)\n            for (lot in lots) {\n""",
        """            val item = requireNotNull(db.itemDao().byId(material.itemId)) { \"الصنف رقم ${material.itemId} غير موجود\" }\n            val lots = AdvancedInventoryService(db).usableLots(order.rawWarehouseId, material.itemId)\n                .filter { it.quantityBase > 1e-9 }\n            lots.forEach { lot ->\n                ProductionMath.validateIssueLotTracking(\n                    lotTracked = item.lotTracked,\n                    expiryTracked = item.expiryTracked,\n                    lotNo = lot.lotNo,\n                    expiryDate = lot.expiryDate\n                )\n            }\n            val balanceNow = lots.sumOf { it.quantityBase }\n            require(balanceNow + 1e-9 >= needed) {\n                \"المخزون المقبول وغير المنتهي والمكتمل بيانات التتبع لا يكفي لإتمام صرف المادة ${item.nameAr}\"\n            }\n\n            var remaining = needed\n            var materialCost = 0.0\n            for (lot in lots) {\n""",
    )

    replace_once(
        prod_service,
        """    private fun fmt(value: Double): String = \"%.3f\".format(Locale.US, value)\n""",
        """    private suspend fun validateOrderBomIntegrity(\n        order: ProductionOrderEntity,\n        materials: List<ProductionMaterialEntity>\n    ) {\n        val components = db.recipeDao().components(order.recipeId)\n        ProductionMath.validateBomIntegrity(\n            recipeComponents = components.map { ProductionBomLink(it.id, it.itemId) },\n            orderMaterials = materials.map { ProductionBomLink(it.recipeComponentId, it.itemId) }\n        )\n    }\n\n    private fun fmt(value: Double): String = \"%.3f\".format(Locale.US, value)\n""",
    )

    test_text = prod_test.read_text(encoding="utf-8")
    if "rejectsProductionMaterialOutsideBom" in test_text:
        raise SystemExit("Phase 14.5.11 tests already present unexpectedly")
    pos = test_text.rfind("}")
    if pos < 0:
        raise SystemExit("Could not locate ProductionMathTest class end")
    extra = r'''

    @Test
    fun rejectsProductionMaterialOutsideBom() {
        val bom = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 102))
        val order = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 999))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateBomIntegrity(bom, order)
        }
    }

    @Test
    fun acceptsExactBomMaterialLinks() {
        val bom = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 102))
        val order = listOf(ProductionBomLink(2, 102), ProductionBomLink(1, 101))
        ProductionMath.validateBomIntegrity(bom, order)
    }

    @Test
    fun lotTrackedMaterialRequiresLotNumber() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(true, false, null, null)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(true, false, "   ", null)
        }
        ProductionMath.validateIssueLotTracking(true, false, "LOT-001", null)
    }

    @Test
    fun expiryTrackedMaterialRequiresExpiryDate() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(false, true, null, null)
        }
        ProductionMath.validateIssueLotTracking(false, true, null, 1_800_000_000_000L)
    }
'''
    prod_test.write_text(test_text[:pos] + extra + test_text[pos:], encoding="utf-8")

    (ROOT / "PHASE14_5_11_SCOPE.md").write_text(
        """# Phase 14.5.11 — Production Issue Guard\n\n- Rejects any production-order material set that does not exactly match the order BOM components.\n- Revalidates BOM integrity both before reservation and again before inventory issue.\n- Lot-tracked materials cannot be issued from stock rows without a lot number.\n- Expiry-tracked materials cannot be issued from stock rows without a valid expiry date.\n- Production issue availability is calculated only from usable, fully traceable stock lots.\n- Room schema remains 21; no migration is required.\n""",
        encoding="utf-8",
    )

    require_contains(build, "versionCode = 50")
    require_contains(build, "0.15.4.11-phase14.5-production-issue-guard")
    require_contains(prod_service, "validateOrderBomIntegrity")
    require_contains(prod_service, "validateIssueLotTracking")
    require_contains(prod_test, "lotTrackedMaterialRequiresLotNumber")
    print("Phase 14.5.11 production issue guard applied successfully.")


if __name__ == "__main__":
    main()
