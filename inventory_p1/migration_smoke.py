#!/usr/bin/env python3
import sqlite3

DDL = [
"CREATE TABLE warehouses(id INTEGER PRIMARY KEY)",
"CREATE TABLE items(id INTEGER PRIMARY KEY)",
"CREATE TABLE journal_entries(id INTEGER PRIMARY KEY)",
"CREATE TABLE purchase_lines(id INTEGER PRIMARY KEY)",
"CREATE TABLE purchase_return_lines(id INTEGER PRIMARY KEY)",
"CREATE TABLE sales_allocations(id INTEGER PRIMARY KEY)",
"CREATE TABLE sales_return_allocations(id INTEGER PRIMARY KEY)",
"CREATE TABLE production_issues(id INTEGER PRIMARY KEY)",
"CREATE TABLE production_batches(id INTEGER PRIMARY KEY)",
"CREATE TABLE inventory_count_lines(id INTEGER PRIMARY KEY)",
"CREATE TABLE warehouse_transfer_lines(id INTEGER PRIMARY KEY)",
"CREATE TABLE audit_events(id INTEGER PRIMARY KEY)",
'''CREATE TABLE stock_movements (
 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 movementDate INTEGER NOT NULL,
 warehouseId INTEGER NOT NULL,
 itemId INTEGER NOT NULL,
 movementType TEXT NOT NULL,
 quantityBase REAL NOT NULL,
 unitCostBase REAL NOT NULL,
 referenceType TEXT NOT NULL,
 referenceId INTEGER NOT NULL,
 lotNo TEXT,
 expiryDate INTEGER,
 createdAt INTEGER NOT NULL,
 FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON DELETE RESTRICT,
 FOREIGN KEY(itemId) REFERENCES items(id) ON DELETE RESTRICT
)''',
]

CONTRACT = '''
CREATE TRIGGER trg_stock_movement_source_key_required_insert
BEFORE INSERT ON stock_movements
WHEN TRIM(NEW.sourceKey) = ''
BEGIN SELECT RAISE(ABORT, 'STOCK_MOVEMENT_SOURCE_KEY_REQUIRED'); END;

CREATE TRIGGER trg_stock_movement_contract_insert
BEFORE INSERT ON stock_movements
WHEN NOT (
 (NEW.movementType = 'OPENING' AND NEW.referenceType = 'JOURNAL_ENTRY' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PURCHASE' AND NEW.referenceType = 'PURCHASE_LINE' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PURCHASE_RETURN' AND NEW.referenceType = 'PURCHASE_RETURN_LINE' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'SALE' AND NEW.referenceType = 'SALES_ALLOCATION' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'SALES_RETURN' AND NEW.referenceType = 'SALES_RETURN_ALLOCATION' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PRODUCTION_ISSUE' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'PRODUCTION_ISSUE_RETURN' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PRODUCTION_ISSUE_CORRECTION' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'PRODUCTION_RECEIPT' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PRODUCTION_RECEIPT_CORRECTION' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'PRODUCTION_COST_REVALUE_OUT' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'PRODUCTION_COST_REVALUE_IN' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'COUNT_ADJUSTMENT' AND NEW.referenceType = 'INVENTORY_COUNT_LINE' AND NEW.quantityBase <> 0) OR
 (NEW.movementType = 'LEGACY_LOT_RECLASS_OUT' AND NEW.referenceType = 'AUDIT_EVENT' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'LEGACY_LOT_RECLASS_IN' AND NEW.referenceType = 'AUDIT_EVENT' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'TRANSFER_OUT' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'TRANSFER_IN' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase > 0) OR
 (NEW.movementType = 'TRANSFER_REVERSAL_OUT' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase < 0) OR
 (NEW.movementType = 'TRANSFER_REVERSAL_IN' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase > 0)
)
BEGIN SELECT RAISE(ABORT, 'INVALID_STOCK_MOVEMENT_CONTRACT'); END;

CREATE TRIGGER trg_stock_movement_source_exists_insert
BEFORE INSERT ON stock_movements
WHEN
 (NEW.referenceType = 'JOURNAL_ENTRY' AND NOT EXISTS (SELECT 1 FROM journal_entries WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'PURCHASE_LINE' AND NOT EXISTS (SELECT 1 FROM purchase_lines WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'PURCHASE_RETURN_LINE' AND NOT EXISTS (SELECT 1 FROM purchase_return_lines WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'SALES_ALLOCATION' AND NOT EXISTS (SELECT 1 FROM sales_allocations WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'SALES_RETURN_ALLOCATION' AND NOT EXISTS (SELECT 1 FROM sales_return_allocations WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'PRODUCTION_ISSUE' AND NOT EXISTS (SELECT 1 FROM production_issues WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'PRODUCTION_BATCH' AND NOT EXISTS (SELECT 1 FROM production_batches WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'INVENTORY_COUNT_LINE' AND NOT EXISTS (SELECT 1 FROM inventory_count_lines WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NOT EXISTS (SELECT 1 FROM warehouse_transfer_lines WHERE id = NEW.referenceId)) OR
 (NEW.referenceType = 'AUDIT_EVENT' AND NOT EXISTS (SELECT 1 FROM audit_events WHERE id = NEW.referenceId)) OR
 NEW.referenceType NOT IN ('JOURNAL_ENTRY','PURCHASE_LINE','PURCHASE_RETURN_LINE','SALES_ALLOCATION','SALES_RETURN_ALLOCATION','PRODUCTION_ISSUE','PRODUCTION_BATCH','INVENTORY_COUNT_LINE','WAREHOUSE_TRANSFER_LINE','AUDIT_EVENT')
BEGIN SELECT RAISE(ABORT, 'ORPHAN_STOCK_MOVEMENT_SOURCE'); END;
'''

def expect_rejected(db, sql, code):
    try:
        db.execute(sql)
    except sqlite3.IntegrityError as e:
        assert code in str(e), (code, str(e))
        return
    raise AssertionError(f"expected rejection {code}")

with sqlite3.connect(':memory:') as db:
    db.execute('PRAGMA foreign_keys=ON')
    for ddl in DDL: db.execute(ddl)
    db.executemany('INSERT INTO warehouses(id) VALUES (?)', [(1,), (2,)])
    db.execute('INSERT INTO items(id) VALUES (7)')
    db.execute('INSERT INTO purchase_lines(id) VALUES (100)')
    db.execute('''INSERT INTO stock_movements(id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,createdAt)
                  VALUES (1,1000,1,7,'PURCHASE',10,2.5,'PURCHASE_LINE',100,'LOT-A',5000,1100)''')
    db.execute('''INSERT INTO stock_movements(id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,createdAt)
                  VALUES (2,2000,1,7,'SALE',-3,2.5,'SALES_LINE',999,'LOT-A',5000,2100)''')
    before = db.execute('SELECT id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,createdAt FROM stock_movements ORDER BY id').fetchall()

    db.execute("ALTER TABLE stock_movements ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
    db.execute("UPDATE stock_movements SET sourceKey = 'LEGACY:' || id WHERE sourceKey = ''")
    db.execute("CREATE UNIQUE INDEX index_stock_movements_sourceKey ON stock_movements(sourceKey)")
    db.executescript(CONTRACT)

    after = db.execute('SELECT id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,createdAt FROM stock_movements ORDER BY id').fetchall()
    assert before == after, (before, after)
    assert db.execute('SELECT id,sourceKey FROM stock_movements ORDER BY id').fetchall() == [(1,'LEGACY:1'),(2,'LEGACY:2')]

    expect_rejected(db, "INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(3000,1,7,'PURCHASE',1,2.5,'PURCHASE_LINE',100,NULL,NULL,'',3001)", 'STOCK_MOVEMENT_SOURCE_KEY_REQUIRED')
    expect_rejected(db, "INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(3000,1,7,'PURCHASE',-1,2.5,'PURCHASE_LINE',100,NULL,NULL,'P1:PURCHASE_LINE:100:PURCHASE',3001)", 'INVALID_STOCK_MOVEMENT_CONTRACT')
    expect_rejected(db, "INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(3000,1,7,'PURCHASE',1,2.5,'PURCHASE_LINE',999,NULL,NULL,'P1:PURCHASE_LINE:999:PURCHASE',3001)", 'ORPHAN_STOCK_MOVEMENT_SOURCE')

    db.execute("INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(3000,1,7,'PURCHASE',1,2.5,'PURCHASE_LINE',100,NULL,NULL,'P1:PURCHASE_LINE:100:PURCHASE',3001)")
    expect_rejected(db, "INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(3002,1,7,'PURCHASE',1,2.5,'PURCHASE_LINE',100,NULL,NULL,'P1:PURCHASE_LINE:100:PURCHASE',3003)", 'UNIQUE constraint failed')

    db.execute('INSERT INTO warehouse_transfer_lines(id) VALUES (301)')
    db.execute("INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(4000,1,7,'TRANSFER_OUT',-2,2.5,'WAREHOUSE_TRANSFER_LINE',301,'LOT-A',5000,'P1:WAREHOUSE_TRANSFER_LINE:301:TRANSFER_OUT',4001)")
    db.execute("INSERT INTO stock_movements(movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,sourceKey,createdAt) VALUES(4000,2,7,'TRANSFER_IN',2,2.5,'WAREHOUSE_TRANSFER_LINE',301,'LOT-A',5000,'P1:WAREHOUSE_TRANSFER_LINE:301:TRANSFER_IN',4002)")
    assert db.execute("SELECT SUM(quantityBase) FROM stock_movements WHERE referenceId=301").fetchone()[0] == 0

print('INVENTORY_P1_MIGRATION_DATA_PRESERVATION_OK')
