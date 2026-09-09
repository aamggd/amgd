# FUSH ERP Mobile v104 — P1 Step 1

## Expense date validation

Status: IMPLEMENTED / BUILD GATE PENDING

Fixes the unsafe behavior where an invalid expense date silently became the device's current date.

New behavior:
- exact yyyy-MM-dd format required;
- impossible dates are rejected;
- the Post Expense button remains disabled while the date is invalid;
- an inline Arabic validation message is shown;
- no fallback to System.currentTimeMillis() exists in expense posting;
- no accounting posting, Room schema, AppID, or migration behavior changed.

Targeted verification:
    .\gradlew.bat :app:testDebugUnitTest --tests "com.fush.erp.domain.ExpenseDatePolicyTest" --no-daemon
    .\gradlew.bat :app:assembleDebug --no-daemon

P0 Final Regression Gate remains pending until executed on the configured Android development machine.
