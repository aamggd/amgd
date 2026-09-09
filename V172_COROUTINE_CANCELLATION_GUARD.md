# v172 Coroutine Cancellation Guard

- Fixes customer-visible Compose cancellation message `The coroutine scope left the composition`.
- Receipt print `LaunchedEffect(pendingReceiptPrintId)` no longer clears its own key before PDF/print work completes.
- `CancellationException` is rethrown instead of being converted into business/UI error messages in sales invoice, receipt, reversal, return, and exchange-rate coroutine paths.
- No Room schema change. Room remains 46.
