from pathlib import Path

root = Path('FushERP_Mobile_Phase5')

# Version bump
p = root / 'app/build.gradle.kts'
s = p.read_text(encoding='utf-8')
assert 'versionCode = 24' in s
assert 'versionName = "0.13.6-phase13-sales-detail"' in s
s = s.replace('versionCode = 24', 'versionCode = 25', 1)
s = s.replace('versionName = "0.13.6-phase13-sales-detail"', 'versionName = "0.13.6.1-phase13-dashboard-return-fix"', 1)
p.write_text(s, encoding='utf-8')

# Executive dashboard/report calculations:
# - collections = receipts minus cash refunds on sales returns
# - receivables include CREDIT invoices only and cannot go negative
# - customer sales report uses the same semantics
p = root / 'app/src/main/java/com/fush/erp/data/dao/ReportDao.kt'
s = p.read_text(encoding='utf-8')
old = """          COALESCE((SELECT SUM(amountBase) FROM customer_receipts WHERE receiptDate BETWEEN :from AND :to),0) AS collectionsBase,"""
new = """          (COALESCE((SELECT SUM(amountBase) FROM customer_receipts WHERE receiptDate BETWEEN :from AND :to),0)
           - COALESCE((SELECT SUM(totalBase) FROM sales_returns
                       WHERE status='POSTED' AND settlementType='CASH_REFUND' AND returnDate BETWEEN :from AND :to),0)) AS collectionsBase,"""
assert old in s
s = s.replace(old, new, 1)

old = """          COALESCE((SELECT SUM(
              si.totalBase
              - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED'),0)
              - COALESCE((SELECT SUM(cra.amountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId=si.id),0)
          ) FROM sales_invoices si WHERE si.status='POSTED'),0) AS receivablesBase,"""
new = """          COALESCE((SELECT SUM(MAX(0,
              si.totalBase
              - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED'),0)
              - COALESCE((SELECT SUM(cra.amountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId=si.id),0)
          )) FROM sales_invoices si WHERE si.status='POSTED' AND si.paymentType='CREDIT'),0) AS receivablesBase,"""
assert old in s
s = s.replace(old, new, 1)

old = """               COALESCE(SUM((SELECT COALESCE(SUM(cra.amountBase),0) FROM customer_receipt_allocations cra
                    JOIN customer_receipts cr ON cr.id=cra.receiptId
                    WHERE cra.invoiceId=si.id AND cr.receiptDate BETWEEN :from AND :to)),0) AS collectionsBase,"""
new = """               (COALESCE(SUM((SELECT COALESCE(SUM(cra.amountBase),0) FROM customer_receipt_allocations cra
                    JOIN customer_receipts cr ON cr.id=cra.receiptId
                    WHERE cra.invoiceId=si.id AND cr.receiptDate BETWEEN :from AND :to)),0)
                - COALESCE(SUM((SELECT COALESCE(SUM(srCash.totalBase),0) FROM sales_returns srCash
                    WHERE srCash.salesInvoiceId=si.id AND srCash.status='POSTED'
                      AND srCash.settlementType='CASH_REFUND' AND srCash.returnDate BETWEEN :from AND :to)),0)) AS collectionsBase,"""
assert old in s
s = s.replace(old, new, 1)

old = """               COALESCE(SUM(si.totalBase
                    - (SELECT COALESCE(SUM(sr2.totalBase),0) FROM sales_returns sr2 WHERE sr2.salesInvoiceId=si.id AND sr2.status='POSTED')
                    - (SELECT COALESCE(SUM(cra2.amountBase),0) FROM customer_receipt_allocations cra2 WHERE cra2.invoiceId=si.id)),0) AS outstandingBase"""
new = """               COALESCE(SUM(CASE WHEN si.paymentType='CREDIT' THEN MAX(0,
                    si.totalBase
                    - (SELECT COALESCE(SUM(sr2.totalBase),0) FROM sales_returns sr2 WHERE sr2.salesInvoiceId=si.id AND sr2.status='POSTED')
                    - (SELECT COALESCE(SUM(cra2.amountBase),0) FROM customer_receipt_allocations cra2 WHERE cra2.invoiceId=si.id)
               ) ELSE 0 END),0) AS outstandingBase"""
assert old in s
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Clarify dashboard label: the figure is net cash collections after actual cash refunds.
p = root / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
s = p.read_text(encoding='utf-8')
old = 'ExecutiveMetric("التحصيل", executive?.collectionsBase, "ريال", Modifier.weight(1f))'
new = 'ExecutiveMetric("صافي التحصيل", executive?.collectionsBase, "ريال", Modifier.weight(1f))'
assert old in s
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
