package com.fush.erp.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

data class ProductionRequirement(
    val itemId: Long,
    val standardForRecipe: Double,
    val requiredQtyBase: Double
)

data class ProductionCostCorrectionSplit(
    val inventoryReductionBase: Double,
    val cogsReductionBase: Double
)

data class ProductionBomLink(
    val recipeComponentId: Long,
    val itemId: Long
)

object ProductionMath {
    fun scaleQuantity(componentQty: Double, recipeOutputQty: Double, plannedOutputQty: Double): Double {
        require(componentQty >= 0.0 && componentQty.isFinite()) { "كمية مكون الوصفة غير صالحة" }
        require(recipeOutputQty > 0.0 && recipeOutputQty.isFinite()) { "ناتج الوصفة القياسي غير صالح" }
        require(plannedOutputQty > 0.0 && plannedOutputQty.isFinite()) { "كمية الإنتاج المخططة غير صالحة" }
        return componentQty * plannedOutputQty / recipeOutputQty
    }

    fun fixedBatchComponentQuantity(componentQty: Double, plannedOutputQty: Double): Double {
        require(componentQty > 0.0 && componentQty.isFinite())
        require(plannedOutputQty > 0.0 && plannedOutputQty.isFinite())
        return componentQty
    }

    fun requireWholePieceQuantity(quantity: Double, label: String = "الكمية"): Double {
        require(quantity >= 0.0 && quantity.isFinite()) { "$label غير صالحة" }
        require(abs(quantity - round(quantity)) <= 1e-9) { "$label يجب أن تكون عددًا صحيحًا من القطع" }
        return round(quantity)
    }

    fun packagingPackCount(bottleQty: Double, bottlesPerPack: Int = 24): Double {
        val bottles = requireWholePieceQuantity(bottleQty, "عدد العبوات")
        require(bottlesPerPack > 0) { "سعة الباكيت غير صالحة" }
        if (bottles <= 0.0) return 0.0
        return ceil(bottles / bottlesPerPack.toDouble())
    }

    fun validateDirectLaborCost(laborCost: Double): Double {
        require(laborCost >= 0.0 && laborCost.isFinite()) { "تكلفة العمالة غير صالحة" }
        return laborCost
    }

    fun parseDirectLaborCostInput(input: String): Double {
        require(input.isNotBlank()) { "يجب إدخال أجور هذه الدفعة؛ لا توجد قيمة عمالة ثابتة" }
        val value = input.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("تكلفة العمالة غير صالحة")
        return validateDirectLaborCost(value)
    }

    fun validateQualitySpecification(
        minValue: Double?,
        maxValue: Double?,
        targetValue: Double?,
        requiredSampleSize: Int
    ) {
        require(minValue != null || maxValue != null) { "يجب تحديد حد قبول أدنى أو أعلى على الأقل" }
        minValue?.let { require(it.isFinite()) { "الحد الأدنى غير صالح" } }
        maxValue?.let { require(it.isFinite()) { "الحد الأعلى غير صالح" } }
        if (minValue != null && maxValue != null) require(minValue <= maxValue) { "الحد الأدنى لا يمكن أن يتجاوز الحد الأعلى" }
        targetValue?.let { target ->
            require(target.isFinite()) { "القيمة المستهدفة غير صالحة" }
            minValue?.let { require(target >= it) { "القيمة المستهدفة أقل من حد القبول الأدنى" } }
            maxValue?.let { require(target <= it) { "القيمة المستهدفة أعلى من حد القبول الأعلى" } }
        }
        require(requiredSampleSize > 0) { "حجم العينة المطلوب يجب أن يكون أكبر من صفر" }
    }

    fun validateQualitySampleSize(actualSampleSize: Int, requiredSampleSize: Int) {
        require(requiredSampleSize > 0) { "حجم العينة المطلوب غير صالح" }
        require(actualSampleSize >= requiredSampleSize) { "حجم العينة الفعلي أقل من الحد المطلوب ($requiredSampleSize)" }
    }

    fun qualityDecision(measuredValue: Double, minValue: Double?, maxValue: Double?): String {
        require(measuredValue.isFinite()) { "القراءة الفعلية غير صالحة" }
        validateQualitySpecification(minValue, maxValue, null, 1)
        val aboveMin = minValue?.let { measuredValue >= it - 1e-12 } ?: true
        val belowMax = maxValue?.let { measuredValue <= it + 1e-12 } ?: true
        return if (aboveMin && belowMax) "PASS" else "FAIL"
    }

    data class QualitySampleSummary(
        val average: Double,
        val minimum: Double,
        val maximum: Double,
        val passedCount: Int,
        val failedCount: Int
    )

    fun summarizeQualitySamples(values: List<Double>, minValue: Double?, maxValue: Double?): QualitySampleSummary {
        require(values.isNotEmpty()) { "يجب إدخال قراءة واحدة على الأقل" }
        require(values.all { it.isFinite() }) { "إحدى قراءات العينة غير صالحة" }
        validateQualitySpecification(minValue, maxValue, null, 1)
        val passed = values.count { qualityDecision(it, minValue, maxValue) == "PASS" }
        return QualitySampleSummary(
            average = values.average(),
            minimum = values.minOrNull()!!,
            maximum = values.maxOrNull()!!,
            passedCount = passed,
            failedCount = values.size - passed
        )
    }

    fun qualitySampleDecision(values: List<Double>, minValue: Double?, maxValue: Double?): String =
        if (summarizeQualitySamples(values, minValue, maxValue).failedCount == 0) "PASS" else "FAIL"

    fun actualUnitCost(materialCost: Double, laborCost: Double, acceptedQty: Double): Double {
        require(materialCost >= 0.0 && materialCost.isFinite()) { "تكلفة المواد غير صالحة" }
        validateDirectLaborCost(laborCost)
        require(acceptedQty > 0.0 && acceptedQty.isFinite()) { "الكمية المقبولة يجب أن تكون أكبر من صفر" }
        return (materialCost + laborCost) / acceptedQty
    }

    fun validateOutput(actualOutput: Double, accepted: Double, rejected: Double, scrap: Double) {
        require(actualOutput >= 0.0 && actualOutput.isFinite()) { "الناتج الفعلي غير صالح" }
        require(accepted >= 0.0 && accepted.isFinite()) { "الكمية المقبولة غير صالحة" }
        require(rejected >= 0.0 && rejected.isFinite()) { "الكمية المرفوضة غير صالحة" }
        require(scrap >= 0.0 && scrap.isFinite()) { "التالف غير صالح" }
        require(accepted + rejected <= actualOutput + 1e-9) { "المقبول والمرفوض يتجاوزان الناتج الفعلي" }
    }

    fun variancePct(actual: Double, standard: Double): Double {
        require(actual >= 0.0 && actual.isFinite()) { "القيمة الفعلية غير صالحة" }
        require(standard >= 0.0 && standard.isFinite()) { "القيمة القياسية غير صالحة" }
        if (abs(standard) < 1e-12) return if (abs(actual) < 1e-12) 0.0 else 100.0
        return ((actual - standard) / standard) * 100.0
    }

    fun validateBomIntegrity(recipeComponents: List<ProductionBomLink>, orderMaterials: List<ProductionBomLink>) {
        require(recipeComponents.isNotEmpty()) { "الوصفة لا تحتوي مكونات" }
        require(orderMaterials.isNotEmpty()) { "أمر الإنتاج لا يحتوي مواد" }
        require(recipeComponents.map { it.recipeComponentId }.distinct().size == recipeComponents.size) {
            "الوصفة تحتوي مكونات مكررة أو غير صالحة"
        }
        require(orderMaterials.map { it.recipeComponentId }.distinct().size == orderMaterials.size) {
            "أمر الإنتاج يحتوي سطراً مكرراً لنفس مكون الوصفة"
        }
        val expected = recipeComponents.associate { it.recipeComponentId to it.itemId }
        val actual = orderMaterials.associate { it.recipeComponentId to it.itemId }
        require(expected == actual) {
            "مواد أمر الإنتاج لا تطابق مكونات الـBOM المعتمدة؛ لا يمكن حجز أو صرف مادة خارج الوصفة"
        }
    }

    fun validateIssueLotTracking(
        lotTracked: Boolean,
        expiryTracked: Boolean,
        lotNo: String?,
        expiryDate: Long?,
        itemLabel: String? = null
    ) {
        val prefix = itemLabel?.trim()?.takeIf { it.isNotBlank() }?.let { "المادة: $it — " } ?: "هذا الصنف "
        if (lotTracked) require(!lotNo.isNullOrBlank()) {
            if (itemLabel.isNullOrBlank()) {
                "هذا الصنف يتطلب رقم تشغيلة (Lot) قبل صرفه للإنتاج"
            } else {
                "${prefix}تتطلب رقم تشغيلة (Lot) قبل صرفها للإنتاج"
            }
        }
        if (expiryTracked) require(expiryDate != null && expiryDate > 0L) {
            if (itemLabel.isNullOrBlank()) {
                "هذا الصنف يتطلب تاريخ صلاحية صالح قبل صرفه للإنتاج"
            } else {
                "${prefix}تتطلب تاريخ صلاحية صالح قبل صرفها للإنتاج"
            }
        }
    }

    fun splitAcceptedBatchCostCorrection(
        correctionCostBase: Double,
        acceptedQtyBase: Double,
        onHandQtyBase: Double
    ): ProductionCostCorrectionSplit {
        require(correctionCostBase >= 0.0 && correctionCostBase.isFinite()) { "قيمة التصحيح غير صالحة" }
        require(acceptedQtyBase > 0.0 && acceptedQtyBase.isFinite()) { "الكمية المقبولة غير صالحة" }
        require(onHandQtyBase >= 0.0 && onHandQtyBase.isFinite()) { "رصيد الدفعة غير صالح" }
        require(onHandQtyBase <= acceptedQtyBase + 1e-7) { "رصيد الدفعة يتجاوز الكمية المقبولة" }
        val inventory = correctionCostBase * (onHandQtyBase.coerceAtMost(acceptedQtyBase) / acceptedQtyBase)
        return ProductionCostCorrectionSplit(
            inventoryReductionBase = inventory,
            cogsReductionBase = (correctionCostBase - inventory).coerceAtLeast(0.0)
        )
    }
}
