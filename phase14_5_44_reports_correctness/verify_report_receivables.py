from pathlib import Path
import sqlite3

SOURCE = Path("app/src/main/java/com/fush/erp/data/dao/ReportDao.kt")
text = SOURCE.read_text(encoding="utf-8")


def query_for(method: str) -> str:
    marker = f"suspend fun {method}"
    method_pos = text.index(marker)
    query_start = text.rfind('@Query("""', 0, method_pos) + len('@Query("""')
    query_end = text.find('""")', query_start, method_pos)
    if query_start < len('@Query("""') or query_end < 0:
        raise AssertionError(f"Unable to extract Room query for {method}")
    return text[query_start:query_end]


executive_sql = query_for("executive")
customer_sql = query_for("customerSales")

conn = sqlite3.connect(":memory:")
c = conn.cursor()
c.executescript(
    """
    CREATE TABLE customers(id INTEGER PRIMARY KEY, nameAr TEXT, province TEXT);
    CREATE TABLE sales_invoices(id INTEGER PRIMARY KEY, customerId INTEGER, status TEXT, paymentType TEXT, invoiceDate INTEGER, dueDate INTEGER, totalBase REAL, invoiceNo TEXT);
    CREATE TABLE sales_returns(id INTEGER PRIMARY KEY, customerId INTEGER, salesInvoiceId INTEGER, status TEXT, settlementType TEXT, returnDate INTEGER, totalBase REAL);
    CREATE TABLE customer_receipts(id INTEGER PRIMARY KEY, customerId INTEGER, receiptDate INTEGER, amountBase REAL);
    CREATE TABLE customer_receipt_allocations(id INTEGER PRIMARY KEY, receiptId INTEGER, invoiceId INTEGER, amountBase REAL);
    CREATE TABLE party_vouchers(id INTEGER PRIMARY KEY, customerId INTEGER, status TEXT, partyType TEXT, voucherType TEXT, voucherDate INTEGER, amountBase REAL);
    CREATE TABLE purchase_invoices(id INTEGER PRIMARY KEY, status TEXT, invoiceDate INTEGER, totalBase REAL);
    CREATE TABLE purchase_returns(id INTEGER PRIMARY KEY, status TEXT, returnDate INTEGER, totalBase REAL);
    CREATE TABLE stock_movements(id INTEGER PRIMARY KEY, movementDate INTEGER, quantityBase REAL, unitCostBase REAL);
    CREATE TABLE production_orders(id INTEGER PRIMARY KEY, plannedDate INTEGER, productItemId INTEGER);
    CREATE TABLE production_batches(id INTEGER PRIMARY KEY, orderId INTEGER, manufactureDate INTEGER, acceptedQtyBase REAL, scrapQtyBase REAL);
    CREATE TABLE items(id INTEGER PRIMARY KEY, code TEXT, nameAr TEXT, nameEn TEXT);
    CREATE TABLE non_conformances(id INTEGER PRIMARY KEY, status TEXT, createdAt INTEGER);
    CREATE TABLE maintenance_work_orders(id INTEGER PRIMARY KEY, openedAt INTEGER, costBase REAL);
    """
)

# Credit invoice 100,000 that is already overdue.
c.execute("INSERT INTO customers VALUES(1,'محمد','تعز')")
c.execute("INSERT INTO sales_invoices VALUES(1,1,'POSTED','CREDIT',100,150,100000,'SI-1')")

# A linked party receipt must clear both report receivable and overdue.
c.execute("INSERT INTO party_vouchers VALUES(1,1,'POSTED','CUSTOMER','RECEIPT',180,100000)")
executive = c.execute(executive_sql, {"from": 0, "to": 200}).fetchone()
assert executive is not None
assert abs(executive[6]) < 1e-9, executive
assert abs(executive[7]) < 1e-9, executive
customer_rows = c.execute(customer_sql, {"from": 0, "to": 200}).fetchall()
assert len(customer_rows) == 1, customer_rows
assert abs(customer_rows[0][-1]) < 1e-9, customer_rows

# A later customer PAYMENT voucher creates a new receivable, but must not age it as old overdue debt.
c.execute("INSERT INTO party_vouchers VALUES(2,1,'POSTED','CUSTOMER','PAYMENT',190,5000)")
executive = c.execute(executive_sql, {"from": 0, "to": 200}).fetchone()
assert abs(executive[6] - 5000.0) < 1e-9, executive
assert abs(executive[7]) < 1e-9, executive
customer_rows = c.execute(customer_sql, {"from": 0, "to": 200}).fetchall()
assert abs(customer_rows[0][-1] - 5000.0) < 1e-9, customer_rows

print("Phase 14.5.44 report receivables SQL regression: PASS")
