# V201 — Admin Unused Shipment Delete

- versionCode: 201
- versionName: `0.15.4.152-admin-unused-shipment-delete`
- Room schema remains 49.
- Adds ADMIN-only delete action in shipment management.
- Hard delete is allowed only when the shipment has no shipment expenses, invoice item allocations, or shipment-expense invoice allocations.
- If cloud sync is active, deletion tombstones for the shipment and its shipment items are published before local deletion so stale devices do not recreate the shipment.
- Used/linked shipments are rejected with an explicit reason instead of deleting dependent business/accounting data.
- No destructive migration and no `fallbackToDestructiveMigration`.
