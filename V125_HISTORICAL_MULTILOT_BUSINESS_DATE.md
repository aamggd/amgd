# V125 — Historical Multi-Lot Allocation + Business-Date Semantics

Baseline lineage: v124 SalesDiscount100Guard, itself based on v123 TransactionChronologyGuards.

Key release changes:
- Historical FEFO allocation can consume safe quantities across multiple lots.
- BusinessDatePolicy centralizes day-level date semantics for historical stock availability.
- Historical safe quantity uses business-day closing-balance semantics.
- Sale allocation plans all lot quantities before writes; insufficient total safe quantity fails before partial allocation.
- FEFO order and per-lot COGS remain preserved.
- Added focused unit/contract tests for multi-lot allocation and business-date semantics.

Identity:
- applicationId: com.fush.erp.recovery
- versionCode: 125
- versionName: 0.15.4.76-historical-multilot-business-date1
- Room schema: 38

Packaging note: build outputs, Gradle caches, local.properties, and signing materials are intentionally excluded.
