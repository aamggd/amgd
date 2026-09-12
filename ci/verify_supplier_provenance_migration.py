#!/usr/bin/env python3
import re
import sqlite3
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8")
sqls = dict(re.findall(r'internal const val (SQL_[A-Z0-9_]+)\s*=\s*"""(.*?)"""', text, re.S))
sqls.update(dict(re.findall(r'internal const val (SQL_[A-Z0-9_]+)\s*=\s*"([^"]*)"', text)))
expected = [
    "SQL_CREATE_SUPPLIER_STOCK_SOURCES", "SQL_CREATE_SUPPLIER_STOCK_ALLOCATIONS",
    "SQL_INDEX_SOURCES_SUPPLIER", "SQL_INDEX_SOURCES_WAREHOUSE", "SQL_INDEX_SOURCES_ITEM",
    "SQL_INDEX_SOURCES_MOVEMENT", "SQL_INDEX_SOURCES_REFERENCE", "SQL_INDEX_SOURCES_LOT",
    "SQL_INDEX_ALLOC_SOURCE", "SQL_INDEX_ALLOC_MOVEMENT", "SQL_INDEX_ALLOC_REVERSES", "SQL_INDEX_ALLOC_REFERENCE",
    "SQL_BACKFILL_LEGACY_SOURCES", "SQL_SOURCE_NO_UPDATE", "SQL_SOURCE_NO_DELETE",
    "SQL_ALLOC_NO_UPDATE", "SQL_ALLOC_NO_DELETE"
]
missing = [name for name in expected if name not in sqls]
assert not missing, f"Missing migration SQL constants: {missing}"

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE suppliers(id INTEGER PRIMARY KEY);
CREATE TABLE warehouses(id INTEGER PRIMARY KEY);
CREATE TABLE items(id INTEGER PRIMARY KEY);
CREATE TABLE stock_movements(
 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 movementDate INTEGER NOT NULL, warehouseId INTEGER NOT NULL, itemId INTEGER NOT NULL,
 movementType TEXT NOT NULL, quantityBase REAL NOT NULL, unitCostBase REAL NOT NULL,
 referenceType TEXT NOT NULL, referenceId INTEGER NOT NULL, lotNo TEXT, expiryDate INTEGER, createdAt INTEGER NOT NULL DEFAULT 0
);
INSERT INTO warehouses(id) VALUES(1);
INSERT INTO items(id) VALUES(100),(200);
INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate)
VALUES (1,1,100,'PURCHASE',10,5,'X',1,'L1',1000),
       (2,1,100,'SALE',-4,5,'X',2,'L1',1000),
       (3,1,200,'PURCHASE',3,7,'X',3,NULL,NULL),
       (4,1,200,'SALE',-3,7,'X',4,NULL,NULL);
""")
before = list(db.execute("SELECT id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,lotNo,expiryDate FROM stock_movements ORDER BY id"))
for name in expected:
    db.execute(sqls[name])
after = list(db.execute("SELECT id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,lotNo,expiryDate FROM stock_movements ORDER BY id"))
assert before == after, "Legacy stock movements changed during 52->53 migration"
rows = list(db.execute("SELECT supplierId,warehouseId,itemId,lotNo,expiryDate,originalQuantityBase,unitCostBase,attributionStatus,sourceType FROM supplier_stock_sources ORDER BY itemId"))
assert len(rows) == 1, rows
row = rows[0]
assert row[0] is None and row[2] == 100 and abs(row[5] - 6.0) < 1e-9, row
assert row[7] == "UNATTRIBUTED" and row[8] == "LEGACY_OPENING", row
assert db.execute("SELECT COUNT(*) FROM supplier_stock_allocations").fetchone()[0] == 0
try:
    db.execute("UPDATE supplier_stock_sources SET sourceType='X' WHERE id=1")
    raise AssertionError("Source immutability trigger did not block update")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("DELETE FROM supplier_stock_sources WHERE id=1")
    raise AssertionError("Source immutability trigger did not block delete")
except sqlite3.IntegrityError:
    pass
print("MIGRATION_52_53_LEDGER_PRESERVED=PASS")
print("LEGACY_UNATTRIBUTED_BACKFILL=PASS")
print("PROVENANCE_IMMUTABILITY=PASS")
