# Phase 13.1 — Safe Recipe Deletion

- Adds a Delete action to every recipe/version card.
- Requires explicit confirmation before destructive deletion.
- Unused recipes are physically deleted; recipe components cascade safely.
- Recipes referenced by production orders are protected and cannot be deleted.
- Historical production traceability and costing remain intact.
- No database schema change; existing user data is preserved.
