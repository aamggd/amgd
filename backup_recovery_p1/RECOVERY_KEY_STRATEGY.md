# Backup / Recovery P1 — Key Separation & Recovery Strategy

Phase scope: P1 only. P2 restore-preflight expansion is intentionally not started.

## Format v3

New backups use a random 256-bit AES data-encryption key (DEK) per backup. The DEK encrypts the entire inner backup payload with AES-256-GCM.

The DEK is never written in plaintext. It is wrapped twice:

1. **Local device wrapper:** the existing non-exportable Android Keystore AES key (`fush_backup_master_v1`) wraps the DEK for normal same-install restores.
2. **Recovery wrapper:** a random 256-bit recovery secret wraps the same DEK. The recovery secret is encoded as a URL-safe Base64 recovery code and shown to the authorized user after backup creation.

The archive stores only IVs, GCM ciphertext/tags, the two wrapped DEKs, and the encrypted payload. It does **not** store the recovery code or any plaintext DEK.

## Recovery behavior

- Same installation/device with the original Android Keystore key: restore opens without asking for the recovery code.
- Different device, reinstall, cleared Keystore, or unavailable original device key: the local unwrap fails and the UI asks for the recovery code. A correct code unwraps the DEK and allows the normal verified restore flow to continue.
- Wrong recovery code: rejected; any partial extracted database is deleted.
- If both the original local Android Keystore key **and** the recovery code are lost, the v3 backup is cryptographically unrecoverable by design.

The recovery code is held only in transient application memory while the user is viewing/using it. P1 does not persist it to the backup archive, SharedPreferences, Room, source code, logs, or diagnostics. Users must store it in an approved secure location outside the protected device.

## Compatibility

- v3: portable via recovery code as above.
- v2 (P0 encrypted backups): remains readable only while the original Android Keystore key survives. P1 cannot retroactively add a recovery wrapper to an already-created v2 archive without first decrypting and recreating it.
- v1 legacy plaintext backup: existing read compatibility remains unchanged.

## Data / schema impact

P1 changes backup key management/envelope and backup/restore UI only. It does not change Room entities, Room schema, migrations, accounting posting, inventory valuation/movements, or production logic.
