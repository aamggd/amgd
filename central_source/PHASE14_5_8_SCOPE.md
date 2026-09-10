# Phase 14.5.8 — Historical Stock Guard

This phase prevents a backdated warehouse transfer from creating a negative stock balance at any later historical checkpoint for the same warehouse/item/lot.

- Validate the exact lot timeline before saving a transfer line.
- Re-validate at posting time inside the database transaction.
- Reject quantities that are valid on the transfer date but would make a later historical balance negative.
- Preserve existing Phase 14.5.7 warehouse-specific reorder behavior.
- No Room schema change is required; schema remains 20.
