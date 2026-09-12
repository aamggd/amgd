#!/usr/bin/env python3
import re
import sqlite3
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding='utf-8')
sqls = dict(re.findall(r'internal const val (SQL_[A-Z0-9_]+)\s*=\s*"""(.*?)"""', text, re.S))
for name in ('SQL_SUPPLIER_ITEM_MOVEMENT_DETAILS', 'SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES'):
    assert name in sqls and sqls[name].strip(), f'missing SQL: {name}'

db = sqlite3.connect(':memory:')
db.row_factory = sqlite3.Row
db.executescript('''
CREATE TABLE suppliers(id INTEGER PRIMARY KEY, code TEXT NOT NULL, nameAr TEXT NOT NULL);
CREATE TABLE warehouses(id INTEGER PRIMARY KEY, nameAr TEXT NOT NULL);
CREATE TABLE units(id INTEGER PRIMARY KEY, nameAr TEXT NOT NULL);
CREATE TABLE items(id INTEGER PRIMARY KEY, code TEXT NOT NULL, nameAr TEXT NOT NULL, baseUnitId INTEGER NOT NULL);
CREATE TABLE stock_movements(
    id INTEGER PRIMARY KEY, movementDate INTEGER NOT NULL, warehouseId INTEGER NOT NULL, itemId INTEGER NOT NULL,
    movementType TEXT NOT NULL, quantityBase REAL NOT NULL, unitCostBase REAL NOT NULL,
    referenceType TEXT NOT NULL, referenceId INTEGER, lotNo TEXT, expiryDate INTEGER
);
CREATE TABLE supplier_stock_sources(
    id INTEGER PRIMARY KEY, supplierId INTEGER, warehouseId INTEGER NOT NULL, itemId INTEGER NOT NULL,
    lotNo TEXT, expiryDate INTEGER, sourceDate INTEGER NOT NULL, sourceType TEXT NOT NULL,
    sourceMovementId INTEGER, sourceReferenceType TEXT NOT NULL, sourceReferenceId INTEGER,
    originalQuantityBase REAL NOT NULL, unitCostBase REAL NOT NULL, attributionStatus TEXT NOT NULL
);
CREATE TABLE supplier_stock_allocations(
    id INTEGER PRIMARY KEY, sourceId INTEGER NOT NULL, stockMovementId INTEGER NOT NULL,
    movementType TEXT NOT NULL, businessReferenceType TEXT NOT NULL, businessReferenceId INTEGER NOT NULL,
    quantityBase REAL NOT NULL, unitCostBase REAL NOT NULL, inventoryValueDeltaBase REAL NOT NULL,
    reversesAllocationId INTEGER
);
CREATE TABLE purchase_invoices(id INTEGER PRIMARY KEY, invoiceNo TEXT NOT NULL);
CREATE TABLE purchase_lines(id INTEGER PRIMARY KEY, invoiceId INTEGER NOT NULL);
CREATE TABLE purchase_returns(id INTEGER PRIMARY KEY, returnNo TEXT NOT NULL);
CREATE TABLE sales_invoices(id INTEGER PRIMARY KEY, invoiceNo TEXT NOT NULL);
CREATE TABLE sales_lines(id INTEGER PRIMARY KEY, invoiceId INTEGER NOT NULL);
CREATE TABLE sales_returns(id INTEGER PRIMARY KEY, returnNo TEXT NOT NULL);

INSERT INTO suppliers VALUES(1,'SUP-A','المورد A'),(2,'SUP-B','المورد B');
INSERT INTO warehouses VALUES(1,'المخزن الرئيسي');
INSERT INTO units VALUES(1,'قطعة');
INSERT INTO items VALUES(100,'SKU-100','صنف مشترك',1);
INSERT INTO purchase_invoices VALUES(11,'PINV-A'),(12,'PINV-B');
INSERT INTO purchase_lines VALUES(101,11),(201,12);
INSERT INTO purchase_returns VALUES(501,'PRET-B');
INSERT INTO sales_invoices VALUES(31,'SINV-1'),(32,'SINV-2');
INSERT INTO sales_lines VALUES(301,31),(302,32);
INSERT INTO sales_returns VALUES(401,'SRET-1');

INSERT INTO stock_movements VALUES
(1,10,1,100,'PURCHASE',10,100,'PURCHASE_LINE',101,'LOT-X',1000),
(2,11,1,100,'PURCHASE',8,110,'PURCHASE_LINE',201,'LOT-X',1000),
(3,20,1,100,'SALE',-7,105,'SALES_LINE',301,'LOT-X',1000),
(4,22,1,100,'SALES_RETURN',1,100,'SALES_RETURN',401,'LOT-X',1000),
(5,23,1,100,'PURCHASE_RETURN',-2,110,'PURCHASE_RETURN',501,'LOT-X',1000),
(6,24,1,100,'SALE',-2,90,'SALES_LINE',302,'LOT-OLD',900);

INSERT INTO supplier_stock_sources VALUES
(10,1,1,100,'LOT-X',1000,10,'PURCHASE',1,'PURCHASE_LINE',101,10,100,'EXACT'),
(20,2,1,100,'LOT-X',1000,11,'PURCHASE',2,'PURCHASE_LINE',201,8,110,'EXACT'),
(30,NULL,1,100,'LOT-OLD',900,5,'LEGACY_OPENING',NULL,'LEGACY_BALANCE',NULL,5,90,'UNATTRIBUTED');

INSERT INTO supplier_stock_allocations VALUES
(100,10,3,'SALE','SALES_ALLOCATION',1001,-4,100,-400,NULL),
(101,20,3,'SALE','SALES_ALLOCATION',1002,-3,110,-330,NULL),
(102,10,4,'SALES_RETURN','SALES_RETURN_ALLOCATION',1101,1,100,100,100),
(103,20,5,'PURCHASE_RETURN','PURCHASE_RETURN_LINE',1201,-2,110,-220,NULL),
(104,30,6,'SALE','SALES_ALLOCATION',1003,-2,90,-180,NULL);
''')

def q(name, **params):
    return list(db.execute(sqls[name], params))

def close(a, b):
    assert abs(float(a) - float(b)) < 1e-9, (a, b)

# Exact supplier A: one shared physical sale movement must expose only A's 4-unit provenance share.
a_details = q('SQL_SUPPLIER_ITEM_MOVEMENT_DETAILS', **{'from': 1, 'to': 30, 'supplierId': 1, 'itemId': None, 'warehouseId': None})
assert [r['movementType'] for r in a_details] == ['PURCHASE', 'SALE', 'SALES_RETURN'], [dict(r) for r in a_details]
close(sum(r['quantityOutBase'] for r in a_details), 4)
close(sum(r['quantityInBase'] for r in a_details), 11)
assert [r['documentNo'] for r in a_details] == ['PINV-A', 'SINV-1', 'SRET-1']
assert all(r['supplierId'] == 1 and r['attributionStatus'] == 'EXACT' for r in a_details)

# Exact supplier B receives only its own 3-unit share of the same sale plus its own purchase return.
b_details = q('SQL_SUPPLIER_ITEM_MOVEMENT_DETAILS', **{'from': 1, 'to': 30, 'supplierId': 2, 'itemId': None, 'warehouseId': None})
assert [r['movementType'] for r in b_details] == ['PURCHASE', 'SALE', 'PURCHASE_RETURN'], [dict(r) for r in b_details]
close(sum(r['quantityOutBase'] for r in b_details), 5)
assert [r['documentNo'] for r in b_details] == ['PINV-B', 'SINV-1', 'PRET-B']

# Summary must preserve supplier identity even though both suppliers own the same SKU and lot.
a_summary = q('SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES', **{'from': 1, 'to': 30, 'supplierId': 1, 'itemId': None, 'warehouseId': None})
assert len(a_summary) == 1
r = a_summary[0]
close(r['openingQtyBase'], 0); close(r['purchasesQtyBase'], 10); close(r['purchaseReturnsQtyBase'], 0)
close(r['salesQtyBase'], 4); close(r['salesReturnsQtyBase'], 1); close(r['closingQtyBase'], 7); close(r['closingValueBase'], 700)

b_summary = q('SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES', **{'from': 1, 'to': 30, 'supplierId': 2, 'itemId': None, 'warehouseId': None})
r = b_summary[0]
close(r['purchasesQtyBase'], 8); close(r['purchaseReturnsQtyBase'], 2); close(r['salesQtyBase'], 3)
close(r['salesReturnsQtyBase'], 0); close(r['closingQtyBase'], 3); close(r['closingValueBase'], 330)

# A later period derives opening balance from provenance history, not from physical SKU total.
later = q('SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES', **{'from': 15, 'to': 30, 'supplierId': 1, 'itemId': 100, 'warehouseId': 1})
r = later[0]
close(r['openingQtyBase'], 10); close(r['purchasesQtyBase'], 0); close(r['salesQtyBase'], 4); close(r['salesReturnsQtyBase'], 1); close(r['closingQtyBase'], 7)

# All-suppliers mode keeps legacy unknown stock separate instead of assigning it to A or B.
all_rows = q('SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES', **{'from': 1, 'to': 30, 'supplierId': None, 'itemId': 100, 'warehouseId': 1})
assert len(all_rows) == 3, [dict(r) for r in all_rows]
unattributed = next(r for r in all_rows if r['supplierId'] is None)
assert unattributed['supplierName'] == 'غير منسوب' and unattributed['attributionStatus'] == 'UNATTRIBUTED'
close(unattributed['salesQtyBase'], 2); close(unattributed['closingQtyBase'], 3); close(unattributed['closingValueBase'], 270)

print('SUPPLIER_REPORT_SQL_PARSE=PASS')
print('SHARED_SKU_SUPPLIER_SEPARATION=PASS')
print('SUPPLIER_PERIOD_OPENING_CLOSING=PASS')
print('UNATTRIBUTED_NOT_GUESSED=PASS')
