from pathlib import Path

ROOT = Path.cwd()


def replace_once(path: str, old: str, new: str) -> None:
    file_path = ROOT / path
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/fush/erp/domain/AccountingService.kt",
    '                val id = requireNotNull(request.customerId) { "يجب تحديد العميل عند استخدام حساب العملاء" }',
    '                val id = CustomerMovementIdentity.requireId(request.customerId)',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val customer = requireNotNull(db.customerDao().byId(request.customerId)) { "العميل غير موجود" }',
    '        val customerId = CustomerMovementIdentity.requireId(request.customerId)\n'
    '        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val open = db.salesDao().openInvoiceSummaries(customerId)\n'
    '            .filter { it.currencyCode == currencyCode }',
    '        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)\n'
    '        val open = db.salesDao().openInvoiceSummaries(validatedCustomerId)\n'
    '            .filter { it.currencyCode == currencyCode }',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '            customerId = customerId,\n'
    '            allocations = plan.allocations.map { ReceiptAllocationRequest(it.invoiceId, it.amountOriginal) },',
    '            customerId = validatedCustomerId,\n'
    '            allocations = plan.allocations.map { ReceiptAllocationRequest(it.invoiceId, it.amountOriginal) },',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }\n'
    '        val treasury = resolveTreasury(treasuryAccountId, currencyCode)',
    '        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)\n'
    '        val customer = requireNotNull(db.customerDao().byId(validatedCustomerId)) { "العميل غير موجود" }\n'
    '        val treasury = resolveTreasury(treasuryAccountId, currencyCode)',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val original = requireNotNull(db.salesDao().receiptById(receiptId)) { "التحصيل غير موجود" }\n'
    '        require(original.reversalOfReceiptId == null) { "لا يمكن عكس مستند عكس" }',
    '        val original = requireNotNull(db.salesDao().receiptById(receiptId)) { "التحصيل غير موجود" }\n'
    '        val customerId = CustomerMovementIdentity.requireId(original.customerId)\n'
    '        require(original.reversalOfReceiptId == null) { "لا يمكن عكس مستند عكس" }',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val invoices = allocations.map { allocation ->\n'
    '            requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة التحصيل غير موجودة" }\n'
    '        }',
    '        val invoices = allocations.map { allocation ->\n'
    '            requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة التحصيل غير موجودة" }.also { invoice ->\n'
    '                val invoiceCustomerId = CustomerMovementIdentity.requireId(invoice.customerId)\n'
    '                require(invoiceCustomerId == customerId) {\n'
    '                    "فاتورة التحصيل لا تخص العميل المرتبط بالتحصيل"\n'
    '                }\n'
    '            }\n'
    '        }',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '                customerId = original.customerId,\n'
    '                receiptDate = reversalDate,',
    '                customerId = customerId,\n'
    '                receiptDate = reversalDate,',
)

replace_once(
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    '        val customer = requireNotNull(db.customerDao().byId(invoice.customerId)) { "العميل غير موجود" }\n'
    '        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null',
    '        val customerId = CustomerMovementIdentity.requireId(invoice.customerId)\n'
    '        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }\n'
    '        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null',
)

(ROOT / "app/src/main/java/com/fush/erp/domain/CustomerMovementIdentity.kt").write_text(
    '''package com.fush.erp.domain

/**
 * Identity guard for every business movement that belongs to a customer.
 *
 * Customer names are display snapshots only and must never be used as the
 * transaction identity. A persisted receivable/customer movement must carry
 * a real, positive customer primary key.
 */
object CustomerMovementIdentity {
    fun requireId(customerId: Long?): Long {
        val id = requireNotNull(customerId) { "يجب تحديد العميل وربط الحركة بمعرف العميل" }
        require(id > 0L) { "معرف العميل غير صالح" }
        return id
    }
}
''',
    encoding="utf-8",
)

(ROOT / "app/src/test/java/com/fush/erp/domain/CustomerMovementIdentityTest.kt").write_text(
    '''package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomerMovementIdentityTest {
    @Test
    fun positiveCustomerIdIsAccepted() {
        assertEquals(42L, CustomerMovementIdentity.requireId(42L))
    }

    @Test
    fun missingCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(null)
        }
    }

    @Test
    fun zeroCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(0L)
        }
    }

    @Test
    fun negativeCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(-7L)
        }
    }
}
''',
    encoding="utf-8",
)
