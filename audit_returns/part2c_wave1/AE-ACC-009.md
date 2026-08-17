# Final Part 2C Wave1 Audit Return — AE-ACC-009

Owner branch: `fush/sales-customers`

Status: **OPEN / DEFECT CONFIRMED BY INTEGRATED SOURCE AUDIT / DO NOT CLOSE WITHOUT CENTRAL APK RETEST**

Tested Central:
- commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room: `35`

## Finding

`SalesService.postReturn` accepts `settlementType = CASH_REFUND` for a posted sale after resolving a treasury account, but the integrated source does not first prove that the returned amount was actually collected in cash / that a cash refund is limited to collected cash. The same service separately exposes `invoiceOutstandingBase`, which calculates credit-sale outstanding using received amounts and customer-credit returns, but `postReturn` does not use that settlement state before accepting `CASH_REFUND`.

For a fully uncollected CREDIT sale this permits the return path to choose a cash-refund settlement while the original receivable can remain open, violating the Part 2C acceptance condition that cash, AR, return settlement, customer statement and journal must reconcile atomically.

## Required owner retest

At minimum cover:
1. CREDIT sale with zero collection -> full return; `CASH_REFUND` must not reduce cash while AR remains open.
2. CREDIT sale partially collected -> cash refund must not exceed the amount that is legitimately refundable in cash; remaining settlement must reconcile AR.
3. Fully collected CREDIT sale -> refund/credit behavior must reconcile customer statement, AR, treasury and journal.
4. Partial and full returns, including repeated return attempts.
5. Final integrated Central APK retest after the owner fix is merged.

## Audit boundary

The audit branch did **not** modify Sales production code. This file only returns the defect to the responsible branch.
