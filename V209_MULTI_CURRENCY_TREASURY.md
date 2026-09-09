# v209 — Multi-Currency Treasury + Treasury Editing

- Room schema 52, non-destructive migration 51 -> 52.
- Existing treasury accounts are preserved and `groupCode` is backfilled from their stable code.
- A logical treasury may have multiple currency-specific posting rows.
- Every currency has its own GL posting account and original-currency balance; balances are never mixed.
- Treasury users with `TREASURY_POST` can edit the logical treasury name/type/bank metadata/active state.
- `TREASURY_POST` can add another active currency to a treasury; the app creates a dedicated ASSET posting account atomically.
- Customer receipt selection continues to require an exact treasury currency match, now with a clear path to add the missing currency.
- Audit events: `TREASURY_UPDATE`, `TREASURY_ADD_CURRENCY`.
- Cloud treasury mirror carries `group_code`; Supabase migration adds and backfills it.
