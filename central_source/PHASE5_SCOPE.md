# Fush ERP Mobile — Phase 5

Version: 0.5.0-phase5

## Scope completed: Assets, maintenance, calibration and safety

### Asset register
- Asset code, Arabic/English name, type, location and serial number.
- Operational status: ACTIVE / OUT_OF_SERVICE / UNDER_MAINTENANCE.
- Criticality: LOW / MEDIUM / HIGH / CRITICAL.
- Usage hours and production-batch counters.
- Inspection due date and calibration due date.
- Calibration-required flag.
- Seeded factory assets: filling machine, two burners, production scale and main fire extinguisher.

### Preventive maintenance plans
- BEFORE_EACH_RUN, AFTER_EACH_BATCH, WEEKLY, MONTHLY, QUARTERLY and CUSTOM frequencies.
- Checklist text, last completion and next due date.
- Seeded schedules follow the industrial study: pre-run visual/electric/gas/filling checks, post-batch cleaning, weekly hoses/connections/filling accuracy, monthly filler/electric/gas/scale/fire-extinguisher checks and quarterly overhaul/failure analysis.
- Preventive work orders can be created from an existing plan.

### Work orders and breakdowns
- Preventive and corrective work orders.
- Due date, status, problem, action taken, technician, downtime and maintenance cost.
- Return-to-service approval is recorded explicitly.
- Breakdown reporting creates a corrective work order automatically.
- HIGH/CRITICAL breakdowns place the asset OUT_OF_SERVICE.
- A repeat breakdown within the 90-day window is flagged as recurring.

### Equipment inspection and production interlock
- PRE_START and POST_BATCH equipment checks, plus weekly/monthly/safety inspections.
- Failed equipment inspection puts the asset OUT_OF_SERVICE.
- Production orders can reference the primary production asset.
- Before the MIXING -> FILLING transition, the selected asset must be ACTIVE, have no overdue scheduled maintenance, no overdue required inspection/calibration, and have a recent successful PRE_START check.
- This implements the study control that stopped/overdue equipment should not be operated without control.

### Calibration
- Calibration/verification records for measuring equipment.
- PASS/FAIL result, reference standard, measured error, tolerance, next due date, certificate reference and notes.
- Failed calibration puts the asset OUT_OF_SERVICE.

### Safety
- Safety incidents: accident, near miss, spill, fire, gas leak or other.
- Area, description, impact/injury, immediate action and CAPA-required flag.
- Safety inspection register with PASS/FAIL, findings and corrective action.
- Incident close-out supports root cause, corrective and preventive action.

### Maintenance KPIs
Domain support added for:
- Preventive maintenance compliance percentage (study target >= 95%).
- Work orders closed on time percentage (study target >= 90%).
- Overdue equipment inspection/calibration count (study target = 0).
- Unplanned downtime minutes.

### UI
- New bottom-navigation tab: الصيانة.
- Asset cards with status and criticality.
- Add asset, report breakdown, create preventive work order.
- Record equipment inspection and calibration.
- Close maintenance work orders and approve return to service.
- Record safety incident/spill and safety inspection.
- Dashboard includes asset count, open maintenance, overdue maintenance and open safety incidents.

### Database upgrade
- Room database advanced from version 4 to 5.
- Explicit MIGRATION_4_5 preserves all Phase 1–4 data.
- Eight new tables: assets, maintenance_plans, maintenance_work_orders, breakdowns, asset_inspections, calibration_records, safety_incidents, safety_inspections.
- Production orders receive nullable primaryAssetId to link the production flow to the selected machine.
- Destructive migration is not enabled.
