# Phase 15.1 — Backup, Restore & Backup Sharing

- Admin-only full SQLite database backup.
- WAL FULL checkpoint before snapshot.
- Backup archive `.fushbackup` contains the database and a manifest with app version, schema version, timestamp and database SHA-256.
- Backup archive is written to Downloads/FushERP/Backups on Android 10+ and can be shared from the app.
- Restore uses Android document picker and verifies archive format, package id, SHA-256, SQLite quick_check and schema compatibility before staging.
- Restore is never applied while Room is open. It is staged and applied before AppContainer opens on the next process start.
- Before replacement, current db/wal/shm are copied into an internal pre-restore safety directory.
- Older supported schema backups may be restored and then upgraded by the existing Room migrations. Newer unsupported schemas are rejected.
- Backup and restore actions are recorded in audit events.
- No Room schema change in this phase.
