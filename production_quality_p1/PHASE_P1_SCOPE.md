# Production / Quality P1 — Physical Stock + Lot Allocation

Status: INITIAL IMPLEMENTATION / WAITING FINAL CENTRAL WAVE VALIDATION

Branch: `fush/production-quality`

Initial Central baseline:
- Branch: `fush/integration-current`
- HEAD: `2cb8da801fc54aec8c1f0d6a83588f097ca85117`
- Central source tree: `7733c6570357eb813f7e05e5093752ea26788749`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

Official phase scope:
- Issue production materials from actual usable stock.
- Link each production issue line to the physical lot/expiry identity when tracking requires it.
- Do not create stock movements without actual quantities.
- Do not start P2.

Implementation:
- Adds a pure pre-allocation contract for production issue lots.
- Allocation consumes the supplied usable-lot rows without overdraw.
- Required lot/expiry metadata is validated before any issue/movement rows are written.
- Insufficient usable physical stock fails before movement creation.
- Production issue rows and `PRODUCTION_ISSUE` stock movements use the exact same allocated lot, expiry, quantity, and unit cost identity.

Room:
- No schema change.
- No migration.
- Schema remains 34.

Accounting:
- Existing material-issue journal posting remains unchanged. No accounting rules are modified.

Exact patch SHA-256:
`fe62719a8b1d44b133145ef552329a6c3c9c7d3fa93fa619289170f2b0abae8a`

Final handoff is intentionally withheld until the current Central wave finishes and the same exact patch is revalidated over the newest Central HEAD.
