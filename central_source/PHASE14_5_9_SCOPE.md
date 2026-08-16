# Phase 14.5.9 — Posted Warehouse Transfer Reversal

- Full reversal for POSTED warehouse transfers only.
- Original transfer movements are preserved; reversal adds opposite stock movements.
- Reversal requires a reason and is audit logged.
- Reversal uses the original transferred unit cost, lot and expiry.
- Reversal is blocked when the destination no longer has the full original quantity or when a later historical checkpoint would become negative.
- Transfer status becomes REVERSED and stores reversal reason/user/time.
