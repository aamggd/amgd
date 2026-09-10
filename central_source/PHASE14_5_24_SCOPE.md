# Phase 14.5.24 — Multi-sample quantitative quality checks

- Quantitative quality specifications with required sample size now collect one actual reading per sampled unit.
- A check passes only when every sample reading is within the specification limits.
- The check stores an aggregate average plus a durable child table containing every individual reading.
- UI shows average, minimum, maximum, conforming count and all sample values.
- Legacy aggregate quality checks remain unchanged and are clearly shown as historical aggregate records; no readings are invented.
- Room schema 24 adds only `quality_check_samples`; migration 23 -> 24 is non-destructive.
