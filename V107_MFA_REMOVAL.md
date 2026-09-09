# FUSH ERP Mobile v107 — MFA Removal

## Status
MFA has been removed from the active application flow.

## Functional changes
- Login now requires username + password only.
- Initial administrator creation no longer redirects to MFA enrollment.
- Sensitive actions use recent password reauthentication only.
- Users & Permissions no longer shows MFA state or MFA reset controls.
- Backup restore reauthentication no longer requests MFA.
- MFA/TOTP/recovery-code runtime implementation and tests were removed.
- Permission guards no longer require MFA for ADMIN or privileged roles.

## Database compatibility
Room schema version remains unchanged. Historical MFA columns and the legacy recovery-code table are intentionally retained in the Room model/migrations to avoid a destructive migration and preserve compatibility with existing installations. They are not used to authorize access. When a user logs in or changes/resets a password, legacy MFA state is cleared.

## Session controls
The v106 configurable session-duration feature remains unchanged.

## Version
- versionCode: 107
- versionName: 0.15.4.58-users-permissions-session-no-mfa
