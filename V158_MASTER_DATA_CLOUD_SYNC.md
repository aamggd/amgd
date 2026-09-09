# FUSH ERP Mobile v158 — Master Data Cloud Sync

- applicationId remains `com.fush.erp.recovery`.
- versionCode: `158`.
- versionName: `0.15.4.109-master-data-cloud-sync`.
- Room remains schema `46`; no Android database migration is added.
- Supabase SQL: `cloud/supabase/003_master_data_sync.sql`.
- Synchronized in this wave: currencies, units, warehouses, items, item-unit conversions, customers, suppliers.
- Initial cloud bootstrap is restricted to OWNER/ADMIN when the cloud master-data tables are empty.
- Employee devices use cloud-first baseline import to avoid overwriting the company baseline with stale local data.
- After baseline, a device-local hash checkpoint detects local/remote edits. If both sides changed the same row, v158 reports a conflict and does not silently overwrite either side.
- Hard delete is not synchronized; inactive rows are synchronized with `is_active=false`.
- Automatic sync runs while the app is open: shortly after login and once per minute. A manual "Sync company data now" action is also available.
- Transactions remain out of scope for v158: sales/purchase documents, stock movements, accounting journals, receipts/payments, and production transactions are not synchronized yet.
