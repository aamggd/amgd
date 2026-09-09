# FUSH ERP Mobile v156 — Multi-User Cloud Identity

## Scope

v156 extends the v155 Cloud Sync Foundation so every local FUSH ERP user can have an independent Supabase Auth identity and per-user encrypted cloud session while remaining inside the same FUSH organization.

This version **does not synchronize ERP business data yet**. Sales, purchases, inventory, accounting and production remain local Room data.

## Upgrade safety

- App ID remains `com.fush.erp.recovery`.
- Room schema remains **46**.
- No destructive Room migration is added.
- Existing v155 owner cloud session is migrated only when a local `ADMIN` opens cloud sync, then the server-side owner identity is claimed and bound to that local username.
- Access and refresh tokens remain protected by Android Keystore AES/GCM and are now scoped per local user.

## Employee flow

1. Run `supabase/V156_MULTI_USER_CLOUD_IDENTITY.sql` once in Supabase SQL Editor.
2. Owner keeps the existing cloud session and opens Cloud Sync once; v156 automatically claims the owner binding.
3. In `Users & Permissions`, choose an employee and press `Cloud`.
4. Enter that employee's email and a temporary cloud password.
5. The app calls the normal Supabase sign-up endpoint, then an OWNER-only SECURITY DEFINER RPC attaches the auth user to the FUSH organization and the local username/role.
6. On the employee's phone, the employee logs into FUSH locally with their own local account, opens Cloud Sync once, and signs into their own cloud account. The app verifies local username and role against the server binding before storing the session.
7. After the first link, each local user has an independent persistent cloud session on that device.

## Security notes

- No `service_role`, secret key, database password or privileged backend key is embedded in the APK.
- The APK contains only the Supabase publishable key already used by v155.
- Employee provisioning is authorized by an authenticated OWNER/ADMIN membership on the server.
- A cloud identity cannot be used with a different local username or incompatible role.
