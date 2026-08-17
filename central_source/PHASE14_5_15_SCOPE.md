# Phase 14.5.15 — Price List Validity Enforcement

This phase makes sales price lists operational instead of informational.

Implemented:
- Sales price rows now support an optional validity end date (`effectiveTo`) in addition to `effectiveFrom`.
- Room schema upgraded from 22 to 23 with migration 22→23.
- Existing historical prices are closed automatically one millisecond before the next price for the same item/channel/province/currency.
- Only active price rows whose validity period contains the invoice date are eligible for sales.
- New active price periods cannot overlap another active price period for the same item/channel/province/currency.
- Adding a later active price automatically closes a prior open-ended active price.
- Price lists can be activated/deactivated from the price-list screen; inactive rows remain visible for audit/history.
- Sales invoice entry no longer silently falls back to Taiz pricing.
- The sales service requires an exact active and valid price list for customer province + channel + invoice currency + invoice date.
- The line unit price must match the configured base-unit price multiplied by the selected sales-unit conversion factor.
- Unit tests cover active/date validity, invalid periods, and unit-price enforcement.

Version:
- versionCode: 54
- versionName: 0.15.4.15-phase14.5-price-list-validity
- Room schema: 23
