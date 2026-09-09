# v164 — Cloud Document Number Guard

- Baseline: v163 Supplier Payments & Treasury Cloud Mirror.
- versionCode 164. Room remains schema 46.
- Adds an organization-wide cloud reservation registry for document numbers.
- Sales invoices and cash receipts reserve manual numbers before posting when the local user is cloud-linked.
- Purchase invoices reserve generated PINV numbers and automatically skip numbers already reserved by another device.
- Existing mirrored sales, purchase, receipt, return, and supplier-payment numbers are backfilled into the registry by the SQL migration.
- For a cloud-linked user, reservation fails closed on cloud/network/session errors to prevent a second phone from posting a colliding number.
- Users that have never linked a cloud session retain local-only behavior.
- No destructive migration, no Room schema change, and no business rows are deleted.
