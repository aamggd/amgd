import sqlite3

STABLE = "'CASH_COUNT_ADJUSTMENT','FX_REVALUATION','SALE','CUSTOMER_RECEIPT','SALES_RETURN','PURCHASE','PURCHASE_RETURN','SUPPLIER_PAYMENT','INVENTORY_COUNT','PRODUCTION_ISSUE','PRODUCTION_LABOR','PRODUCTION_RECEIPT','PRODUCTION_REJECT'"

db = sqlite3.connect(':memory:')
db.executescript('''
PRAGMA foreign_keys=ON;
CREATE TABLE journal_entries(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  entryNo TEXT NOT NULL UNIQUE,
  entryDate INTEGER NOT NULL,
  description TEXT NOT NULL,
  currencyCode TEXT NOT NULL,
  exchangeRate REAL NOT NULL,
  sourceType TEXT NOT NULL,
  sourceId TEXT,
  status TEXT NOT NULL,
  createdBy INTEGER NOT NULL,
  createdAt INTEGER NOT NULL
);
CREATE TABLE journal_lines(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  entryId INTEGER NOT NULL,
  accountId INTEGER NOT NULL,
  debit REAL NOT NULL,
  credit REAL NOT NULL,
  memo TEXT NOT NULL,
  FOREIGN KEY(entryId) REFERENCES journal_entries(id) ON DELETE CASCADE
);
''')

# Migration must preserve historical duplicates and existing data.
for no in ('OLD-1', 'OLD-2'):
    db.execute(
        "INSERT INTO journal_entries(entryNo,entryDate,description,currencyCode,exchangeRate,sourceType,sourceId,status,createdBy,createdAt) VALUES(?,1,'old','YER_NEW',1,'SALE','historical-dup','POSTED',1,1)",
        (no,),
    )
assert db.execute("SELECT COUNT(*) FROM journal_entries WHERE sourceType='SALE' AND sourceId='historical-dup'").fetchone()[0] == 2

triggers = f'''
CREATE TRIGGER trg_journal_stable_source_id_required_insert BEFORE INSERT ON journal_entries
WHEN NEW.status='POSTED' AND NEW.sourceType IN ({STABLE}) AND (NEW.sourceId IS NULL OR TRIM(NEW.sourceId)='')
BEGIN SELECT RAISE(ABORT,'ACCOUNTING_STABLE_SOURCE_ID_REQUIRED'); END;
CREATE TRIGGER trg_journal_no_duplicate_stable_source_insert BEFORE INSERT ON journal_entries
WHEN NEW.status='POSTED' AND NEW.sourceType IN ({STABLE}) AND NEW.sourceId IS NOT NULL AND TRIM(NEW.sourceId)<>'' AND EXISTS(
  SELECT 1 FROM journal_entries e WHERE e.status='POSTED' AND e.sourceType=NEW.sourceType AND e.sourceId=NEW.sourceId)
BEGIN SELECT RAISE(ABORT,'DUPLICATE_ACCOUNTING_POSTING'); END;
CREATE TRIGGER trg_journal_no_duplicate_stable_source_update BEFORE UPDATE OF status,sourceType,sourceId ON journal_entries
WHEN OLD.status<>'POSTED' AND NEW.status='POSTED' AND NEW.sourceType IN ({STABLE}) AND NEW.sourceId IS NOT NULL AND TRIM(NEW.sourceId)<>'' AND EXISTS(
  SELECT 1 FROM journal_entries e WHERE e.id<>NEW.id AND e.status='POSTED' AND e.sourceType=NEW.sourceType AND e.sourceId=NEW.sourceId)
BEGIN SELECT RAISE(ABORT,'DUPLICATE_ACCOUNTING_POSTING'); END;
CREATE TRIGGER trg_posted_journal_no_update BEFORE UPDATE ON journal_entries WHEN OLD.status='POSTED'
BEGIN SELECT RAISE(ABORT,'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL'); END;
CREATE TRIGGER trg_posted_journal_no_delete BEFORE DELETE ON journal_entries WHEN OLD.status='POSTED'
BEGIN SELECT RAISE(ABORT,'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL'); END;
CREATE TRIGGER trg_journal_line_sanity_insert BEFORE INSERT ON journal_lines
WHEN NEW.debit<0 OR NEW.credit<0 OR (NEW.debit>0 AND NEW.credit>0)
BEGIN SELECT RAISE(ABORT,'INVALID_JOURNAL_LINE'); END;
CREATE TRIGGER trg_posted_journal_line_no_update BEFORE UPDATE ON journal_lines WHEN EXISTS(
  SELECT 1 FROM journal_entries je WHERE je.id=OLD.entryId AND je.status='POSTED')
BEGIN SELECT RAISE(ABORT,'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL'); END;
CREATE TRIGGER trg_posted_journal_line_no_delete BEFORE DELETE ON journal_lines WHEN EXISTS(
  SELECT 1 FROM journal_entries je WHERE je.id=OLD.entryId AND je.status='POSTED')
BEGIN SELECT RAISE(ABORT,'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL'); END;
'''
db.executescript(triggers)

def must_fail(sql, args=()):
    try:
        db.execute(sql, args)
        raise AssertionError('statement unexpectedly succeeded: ' + sql)
    except sqlite3.IntegrityError:
        pass

base = "INSERT INTO journal_entries(entryNo,entryDate,description,currencyCode,exchangeRate,sourceType,sourceId,status,createdBy,createdAt) VALUES(?,1,'x','YER_NEW',1,?,?,?,1,1)"
db.execute(base, ('S-1', 'SALE', '42', 'POSTED'))
sale_id = db.execute("SELECT id FROM journal_entries WHERE entryNo='S-1'").fetchone()[0]
db.execute("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,1,100,0,'')", (sale_id,))
db.execute("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,2,0,100,'')", (sale_id,))
assert db.execute("SELECT ROUND(SUM(debit-credit),6) FROM journal_lines WHERE entryId=?", (sale_id,)).fetchone()[0] == 0

must_fail(base, ('S-2', 'SALE', '42', 'POSTED'))
must_fail(base, ('S-3', 'SALE', ' ', 'POSTED'))

# Repeatable/manual events are intentionally not falsely collapsed.
db.execute(base, ('M-1', 'MANUAL', 'same', 'POSTED'))
db.execute(base, ('M-2', 'MANUAL', 'same', 'POSTED'))

# Invalid debit/credit shapes are rejected.
must_fail("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,3,-1,0,'')", (sale_id,))
must_fail("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,3,1,1,'')", (sale_id,))

# Posted journal and lines are immutable; correction must be a reversal/new event.
must_fail("UPDATE journal_entries SET description='edited' WHERE id=?", (sale_id,))
must_fail("DELETE FROM journal_entries WHERE id=?", (sale_id,))
line_id = db.execute("SELECT id FROM journal_lines WHERE entryId=? LIMIT 1", (sale_id,)).fetchone()[0]
must_fail("UPDATE journal_lines SET debit=99 WHERE id=?", (line_id,))
must_fail("DELETE FROM journal_lines WHERE id=?", (line_id,))

# Reversal remains a new balanced journal and does not mutate the original.
db.execute(base, ('R-1', 'REVERSAL', str(sale_id), 'POSTED'))
rev_id = db.execute("SELECT id FROM journal_entries WHERE entryNo='R-1'").fetchone()[0]
db.execute("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,1,0,100,'')", (rev_id,))
db.execute("INSERT INTO journal_lines(entryId,accountId,debit,credit,memo) VALUES(?,2,100,0,'')", (rev_id,))
assert db.execute("SELECT ROUND(SUM(debit-credit),6) FROM journal_lines WHERE entryId=?", (rev_id,)).fetchone()[0] == 0
assert db.execute("SELECT COUNT(*) FROM journal_entries WHERE id=?", (sale_id,)).fetchone()[0] == 1

print('ACCOUNTING_P1_SQLITE_JOURNAL_INTEGRITY_OK')
