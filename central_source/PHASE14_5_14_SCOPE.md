# Fush ERP Phase 14.5.14 — Quantitative Quality Specifications

- Adds reusable product quality specifications with units, lower/upper acceptance limits, optional target values and minimum sample sizes.
- Does not invent product limits; users enter their approved internal/specification values.
- Records actual numeric measurements and a snapshot of the specification used for auditability.
- Automatically derives PASS/FAIL from inclusive acceptance limits; users no longer manually choose the result for quantitative checks.
- Enforces the minimum sample size for every quantitative reading.
- Required active FINAL specifications must have a latest PASS result before a batch can be accepted.
- A later passing retest may supersede an earlier failed quantitative reading for acceptance, while the failed historical reading remains in the audit trail; open CAPA still blocks acceptance.
- Keeps legacy descriptive checks for additional non-quantitative observations.
- Expands production detail and exported quality reports with measurement, unit, limits, target and sample size.
- Room schema upgraded from 21 to 22 with a non-destructive migration.
