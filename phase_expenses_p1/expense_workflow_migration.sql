CREATE TABLE IF NOT EXISTS expense_workflow_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    requestNo TEXT NOT NULL,
    treasuryAccountId INTEGER NOT NULL,
    expenseAccountId INTEGER NOT NULL,
    amountOriginal REAL NOT NULL,
    currencyCode TEXT NOT NULL,
    exchangeRate REAL NOT NULL,
    description TEXT NOT NULL,
    referenceNo TEXT NOT NULL,
    expenseDate INTEGER NOT NULL,
    employeeId INTEGER,
    salesRepId INTEGER,
    costCenterCode TEXT NOT NULL,
    organizationUnit TEXT NOT NULL,
    referenceType TEXT NOT NULL,
    referenceId INTEGER,
    referenceLabel TEXT NOT NULL,
    customerId INTEGER,
    supplierId INTEGER,
    itemId INTEGER,
    attachmentFileName TEXT NOT NULL,
    attachmentMimeType TEXT NOT NULL,
    attachmentUri TEXT NOT NULL,
    attachmentNotes TEXT NOT NULL,
    approvalStatus TEXT NOT NULL,
    paymentStatus TEXT NOT NULL,
    createdBy INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    submittedBy INTEGER,
    submittedAt INTEGER,
    approvedBy INTEGER,
    approvedAt INTEGER,
    rejectedBy INTEGER,
    rejectedAt INTEGER,
    rejectionReason TEXT NOT NULL,
    paidBy INTEGER,
    paidAt INTEGER,
    journalEntryId INTEGER,
    partyVoucherId INTEGER,
    updatedAt INTEGER NOT NULL,
    FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
    FOREIGN KEY(expenseAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
);
CREATE UNIQUE INDEX IF NOT EXISTS index_expense_workflow_requests_requestNo ON expense_workflow_requests(requestNo);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_treasuryAccountId ON expense_workflow_requests(treasuryAccountId);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_expenseAccountId ON expense_workflow_requests(expenseAccountId);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_approvalStatus ON expense_workflow_requests(approvalStatus);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_paymentStatus ON expense_workflow_requests(paymentStatus);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_expenseDate ON expense_workflow_requests(expenseDate);
CREATE INDEX IF NOT EXISTS index_expense_workflow_requests_createdBy ON expense_workflow_requests(createdBy);
