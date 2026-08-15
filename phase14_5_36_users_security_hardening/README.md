# FUSH ERP — Users & Security Hardening 14.5.36

Branch: `fush/users-permissions`

This stage hardens the Phase 14.5.34 users/permissions implementation. It intentionally contains only security/user changes so the central integration chat can merge it with the UI and sales branches.

## Changes
- Removes the embedded default `admin / Fush@2026` bootstrap credential.
- Adds first-run administrator setup; the first admin chooses the username/display name/password.
- Preserves ADMIN as the protected first role and records `ADMIN_BOOTSTRAPPED` in audit.
- Enforces one concurrent session by incrementing `sessionVersion` on every successful login.
- Session timeout: 5 minutes for ADMIN, 10 minutes for other users.
- Idle timeout: 3 minutes for ADMIN, 5 minutes for other users.
- Absolute session ceiling: 4 hours for ADMIN, 8 hours for other users.
- Password maximum age: 60 days; expired passwords force the existing change-password flow.
- Password history remains 10 and minimum length remains 15 characters.
- Polls account/session validity every 15 seconds and terminates stale/disabled/replaced sessions.
- Installs SQLite triggers that allow audit inserts but block UPDATE and DELETE on `audit_events`.
- Adds/extends unit tests for session and password-age policies.

## Validation
- `git diff --check`: PASS
- legacy hardcoded password scan: PASS (not present in production source)
- `git apply --check` against Phase 14.5.34 users-permissions source: PASS
- exact post-apply changed-file comparison: PASS
- SQLite audit immutability test: INSERT PASS; UPDATE BLOCKED; DELETE BLOCKED

## Patch integrity
SHA-256: `a17965137613db3c7e7a48ddcfae4c083e35e45088c9d367c2e421863a9bdef8`

## Apply
From a clean Phase 14.5.34 users-permissions source checkout:

```bash
python path/to/phase14_5_36_users_security_hardening/apply_patch.py
```

The global `fush/main` branch also contains UI/sales work, so central integration may need to resolve overlapping UI files (`HomeShell.kt`, `FushErpApp.kt`, and version metadata) instead of blindly applying patches in arbitrary order.
