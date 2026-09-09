# Phase 14.2 — Demand Plan Approval & Manual Adjustment

- Persist next-month demand plans by finished product and province.
- Snapshot the 12-month baseline, seasonality factor, and system forecast at save time.
- Allow an authorized user to enter a manual planned quantity and explanatory note.
- Keep the manual adjustment amount visible as planned minus system forecast.
- Workflow states: DRAFT and APPROVED.
- Approved plans are locked against editing until explicitly reopened with a mandatory reason.
- Reopening increments the plan revision and writes an immutable audit event.
- Approval, draft saves/updates, and reopen actions are written to the governance audit trail.
- Database migration 14 -> 15 creates `demand_plans` without deleting existing data.
- Version: 0.14.2-phase14-demand-plan-approval, versionCode 31.
