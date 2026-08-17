# Sales / Customers — Customer Receipt Atomicity (Separate Handoff)

Owner branch: `fush/sales-customers`

This work is **not AE-ACC-009**. It is retained as a separate customer-receipt atomicity hardening handoff.

## Exact baseline used by the historical validation

- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Exact `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`

## Scope

The receipt hardening makes customer receipt header, allocations, numbering, commission effects and the cash/AR journal execute in one Room transaction, preflights GL dependencies before receipt-side mutations, and lets journal failures propagate so Room can roll the operation back.

This work remains separate from the official Final Audit defect `AE-ACC-009`, which concerns `SalesService.postReturn` / `CASH_REFUND` eligibility and refundable collected cash.

## Historical validation record

- Historical workflow run: `31983195587`
- Historical tested branch head: `645e71d125810163fb99a9119bc636e145190ebb`
- Historical exact application patch SHA-256: `2f9b3f8c4d215b85735c0f518a09deaafe35f42ae135cb920bae14fdb1d8439b`

Do not merge this handoff as the resolution of AE-ACC-009.