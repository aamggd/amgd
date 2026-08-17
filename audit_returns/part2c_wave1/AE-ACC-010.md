# Final Part 2C Wave1 Audit Return — AE-ACC-010

Owner branch: `fush/purchases-suppliers`

Status: **OPEN / DEFECT CONFIRMED BY INTEGRATED SOURCE AUDIT / DO NOT CLOSE WITHOUT CENTRAL APK RETEST**

Tested Central:
- commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room: `35`

## Finding

`PurchaseService.postPurchaseReturn` accepts `settlementType = CASH_REFUND` for a posted purchase after resolving a treasury account, but the integrated source does not first prove that the invoice was paid in cash / that the refund is limited to amounts actually paid. Supplier payment logic separately calculates AP outstanding using supplier-credit returns and prior payments, but the purchase-return path does not use that settlement state before accepting `CASH_REFUND`.

For an unpaid CREDIT purchase this permits a cash-refund return path while the original supplier payable can remain open, violating the Part 2C acceptance condition that cash, AP, return settlement, supplier statement and journal reconcile atomically.

## Required owner retest

At minimum cover:
1. CREDIT purchase with zero supplier payment -> full return; `CASH_REFUND` must not increase cash while AP remains open.
2. CREDIT purchase partially paid -> cash refund must not exceed the legitimately refundable cash amount; remaining settlement must reconcile AP.
3. Fully paid CREDIT purchase -> refund/credit behavior must reconcile supplier statement, AP, treasury and journal.
4. Partial and full returns, including repeated return attempts.
5. Final integrated Central APK retest after the owner fix is merged.

## Audit boundary

The audit branch did **not** modify Purchases production code. This file only returns the defect to the responsible branch.
