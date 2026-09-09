# Phase 14.5.16 — Safe Backup / Restore

- Centralize backup schema version on the real Room schema constant (23) to prevent version drift.
- Clean backup/inspection/staging temporary files on success and failure.
- Preserve an existing pending restore until a replacement candidate is fully verified.
- Re-open and re-hash the selected backup when staging to detect changed source files.
- Sync staged/replacement files before atomic moves.
- Checkpoint the live SQLite database before restore so WAL data is consolidated.
- Keep an internal pre-restore safety database set and automatically roll back if post-swap verification fails.
- Consume the pending restore source after success to prevent accidental repeated restores.
- Keep only the latest three internal safety snapshots.
- Backup archive extraction deletes partial database files on any failure and rejects duplicate critical ZIP entries.
- No Room schema change in this phase.
