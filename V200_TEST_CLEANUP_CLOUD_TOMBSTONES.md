# v200 — Test Cleanup Cloud Tombstones

- versionCode: 200
- versionName: 0.15.4.151-test-cleanup-cloud-tombstones
- Room schema remains 49.
- No destructive migration and no Supabase schema migration is required.

## Purpose
Temporary ADMIN/FUSH_SUPPORT test-data cleanup now retires a deliberately-created test shipment expense in both local storage and cloud synchronization state. The existing v179 sales-auxiliary document and v177 accounting journal/voucher cloud rows are replaced at the same natural keys with explicit `TEST_DATA_SUPPORT` deletion tombstones.

## Ordering and safety
1. Publish shipment-expense tombstone first.
2. Publish accounting journal/voucher tombstones second.
3. Verify the tombstones are durable in cloud state.
4. Delete the local shipment expense, treasury voucher and journal inside one Room transaction.
5. During unified sync, remote shipment-expense tombstones are consumed before accounting tombstones so RESTRICT foreign keys are never bypassed out of order.
6. Tombstones are consumed before stale local copies are republished, preventing resurrection on another phone.
7. Posted-journal DELETE guards are relaxed only inside the protected tombstone transaction and are restored in `finally`.

The feature remains restricted to the temporary test-cleanup maintenance workflow and requires an explicit reason identifying the operation as test/demo data.
