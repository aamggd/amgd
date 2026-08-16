# FUSH ERP Mobile — Official UI Plan P1: Unified Design System

Baseline: Central Phase 14.5.54 Printing Integrated
Branch: `fush/ui-professional-redesign`

## Scope
P1 only: unify presentation primitives for spacing, typography/shapes, inputs, buttons,
dialogs, and content states. This phase does not perform localization completion (P2), RTL/LTR
screen-by-screen work (P3), form workflow changes (P4), or screenshot regression (P5).

## Implemented
- Added central UI token definitions for spacing, radii, dimensions, touch targets, dialog height,
  and subtle elevation.
- Centralized status-tone colors used by pills, metrics, notices and state cards.
- Material typography remains the single type scale; Material shapes now consume the central radius tokens.
- Added shared primary/secondary/destructive/text action button primitives with a common 48dp
  minimum interactive size.
- Standardized shared input fields on the same shape and 56dp visual minimum while preserving all
  existing string values and validation callbacks.
- Standardized long dialog form spacing and maximum scrollable height.
- Normalized the existing shared cards/states/brand/avatar spacing onto the common spacing scale.
- Added JVM contract tests for token ordering and accessibility-critical dimensions.

## Intentionally unchanged
- No screen/domain workflow was rewritten.
- No accounting, inventory, production, security or service logic changed.
- No Room entity, DAO, schema or migration changed.
- No localization completion work from P2 was started.
- No application version or signing configuration changed.

## Integration rule
Transfer the P1 patch selectively onto the current Central Baseline. Do not replace newer central
screen files with historical UI copies. No direct merge to `fush/main` or `fush/integration-current`.
