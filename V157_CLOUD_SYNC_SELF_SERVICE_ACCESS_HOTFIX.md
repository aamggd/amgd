# FUSH ERP Mobile v157 — Cloud Sync Self-Service Access Hotfix

## Problem
In v156, navigation to **Cloud Sync** was mapped to `ROLES_MANAGE`. Non-admin users such as `ACCOUNTANT` therefore could not see the Cloud Sync page even after the owner provisioned their cloud identity.

## Fix
- Cloud Sync is now a self-service page available to every authenticated, active local user.
- The page only operates on the current user's own cloud session/device registration.
- Administrative user/cloud provisioning remains protected by `ROLES_MANAGE` in Users & Security.
- No new Room migration. Schema remains 46.
- No destructive migration.

## Release
- versionCode: 157
- versionName: 0.15.4.108-cloud-sync-self-service-access-hotfix
