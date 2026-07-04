# Fee Tracker Handoff Pending Work

This file is a handoff-oriented snapshot of what is still pending after the current fee tracker implementation work in `D:\AntiGravity\yata`.

It is intended for another coding agent to continue from the current state without needing the full conversation history.

---

## Current State

The fee tracker is already substantially implemented and working:

- activation and gating through Settings are implemented
- Room schema and migration to fee tables are implemented
- client/group/invoice/payment repositories and use cases exist
- fee dashboard, receipts, invoices, outstanding, clients, and groups screens exist
- client detail shows linked todo work, invoices, and receipts
- invoice detail supports record payment and cancel invoice
- invoice editor supports:
  - create
  - save draft
  - issue
  - edit existing invoices
  - multi-line items
- synergy features are in place:
  - task `clientId`
  - task `feeInvoiceId`
  - auto todo on invoice
  - receipt flow from task context
- invoice lifecycle logic was strengthened:
  - issued invoice edits recalculate status from current payments
  - linked collection todos stay in sync with amount, due date, client, and completion state
  - editing an invoice back into an outstanding state reopens the linked todo
  - paid or cancelled invoices close the linked todo
- fee JSON export/import is implemented in `JsonExporter`
- backup/import instrumentation coverage now exists and passes

Recent verification completed:

- `./gradlew.bat compileDebugKotlin` passed
- `./gradlew.bat testDebugUnitTest --console=plain` passed
- `./gradlew.bat compileDebugAndroidTestKotlin` passed
- `./gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.todo.util.JsonExporterFeeTrackerTest` passed on connected device
- debug APK was installed to device `49261FDAS003Z8`

---

## Highest Priority Pending Work

These are the best next tasks if the goal is to improve confidence and ship-readiness rather than add entirely new features.

### 1. UI cleanup in `FeeDashboardScreen.kt`

Status:

- The screen works, but the file has become large and contains multiple unrelated responsibilities.
- There is still a legacy unused invoice dialog renamed to `LegacyInvoiceDialog`.
- Some invoice/client status text still renders raw enum names instead of fully polished human-readable labels.
- The file contains some encoding/misrendered symbol artifacts in strings.

Files:

- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardScreen.kt`

Concrete tasks:

1. Remove `LegacyInvoiceDialog` entirely.
2. Replace remaining raw enum status usage with `invoiceStatusLabel(...)` or equivalent normalized UI text.
3. Clean up string artifacts like:
   - rupee symbol rendering
   - bullet separators
   - multiplication symbol in line item display
4. Consider extracting the following into smaller private files or subcomponents:
   - `OutstandingTab`
   - `ClientDetailDialog`
   - `InvoiceDetailDialog`
   - `InvoiceEditorDialog`
   - shared fee list/status helpers

Why this matters:

- The main lifecycle and data logic are already strong.
- The biggest remaining roughness is now UI maintainability and polish.

---

### 2. Device QA for invoice lifecycle and linked todo behavior

Status:

- Logic is implemented and tested at unit level.
- Backup/import was verified through instrumentation on device.
- Full manual lifecycle QA of invoice-task behavior is still pending.

Device previously used:

- `49261FDAS003Z8`

Concrete manual test scenarios:

1. Create a draft invoice.
   - confirm it appears as draft
   - confirm no payment action is shown for draft if the current UI intends that behavior
2. Issue the draft invoice.
   - confirm invoice number allocation if applicable
   - confirm linked collection todo exists
3. Record a partial payment.
   - confirm status changes to partial
   - confirm linked todo stays open
4. Record full settlement.
   - confirm status becomes paid
   - confirm linked todo closes
5. Edit a paid invoice to increase total above settled amount.
   - confirm status becomes partial or due again
   - confirm linked todo reopens
6. Edit due date and client on an issued invoice.
   - confirm linked todo title/due date/client link update
7. Cancel an issued invoice.
   - confirm status becomes cancelled
   - confirm linked todo closes
8. Export data and import into a fresh state manually if a UI entry point exists.
   - confirm fee records and task links survive

Why this matters:

- This is the best remaining real-world validation of the most important cross-domain workflow.

---

### 3. Add instrumentation coverage for linked task lifecycle

Status:

- Unit tests cover settlement transitions.
- Instrumentation tests cover fee export/import.
- There is not yet an instrumentation test covering invoice edit/payment/cancel interactions with linked task state.

Suggested test area:

- `app/src/androidTest/java/com/example/todo/...`

Suggested scenarios:

1. Issued invoice + linked todo + payment -> linked todo completes when fully settled.
2. Paid invoice edited upward -> linked todo reopens.
3. Cancelled invoice -> linked todo completes/closes.
4. Edited invoice updates linked todo title and due date.

Why this matters:

- The most fragile business behavior now lives at the invoice-task boundary.
- Instrumentation coverage would reduce regression risk there.

---

## Medium Priority Pending Work

These improve product quality but are less urgent than the items above.

### 4. Normalize invoice status UX everywhere

Status:

- Backend lifecycle handling is stronger than the current display layer.
- Some UI surfaces still likely show inconsistent or overly raw state wording.

Files likely involved:

- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardScreen.kt`

Concrete tasks:

1. Ensure the same status labels are used in:
   - invoice list
   - invoice detail
   - client detail invoice history
   - outstanding list
2. Consider visual emphasis for:
   - draft
   - due
   - partially paid
   - paid
   - cancelled
3. Optionally introduce chips/badges if the UI needs stronger readability.

---

### 5. Refactor the invoice editor into a smaller surface

Status:

- `InvoiceEditorDialog` works, but its state and helpers live in the already-large screen file.

Files likely involved:

- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardScreen.kt`

Suggested extraction:

- `InvoiceEditorDialog.kt`
- `InvoiceEditorModels.kt`
- `InvoiceStatusUi.kt`

Suggested extractions:

- `InvoiceEditorSubmission`
- `InvoiceLineItemInput`
- `buildInvoiceEditorSubmission(...)`
- `dueDateFromDays(...)`
- `initialDueDaysText(...)`

Why this matters:

- Improves readability
- reduces merge conflicts
- lowers risk for future invoice work

---

### 6. Add stronger tests for invoice editor behavior

Status:

- Editor flows compile and work through UI wiring.
- No focused tests yet validate editor-specific transformations.

Suggested tests:

1. Draft submission ignores invalid line items.
2. Due-days conversion behaves correctly.
3. Multi-line total matches editor input.
4. Edit flow preserves invoice identity while changing fields.

---

## Lower Priority Pending Work

These are useful but can wait until the core flow is cleaner.

### 7. Expand fee settings UX

Status:

- Settings integration exists.
- Some settings surfaces are still light/polish-level rather than fully mature.

Potential tasks:

1. Better firm profile editing UX
2. clearer CA settings grouping
3. revisit fee backup/export discoverability in Settings

---

### 8. Improve outstanding/invoice filtering

Status:

- Outstanding and invoices lists exist.
- Filtering/search/sorting is still basic.

Potential tasks:

1. filter by client
2. filter by status
3. sort by due date / raised date / amount
4. search by invoice number or client name

---

### 9. String cleanup / localization readiness

Status:

- Some strings are still inline in composables.
- Some symbols appear garbled due to encoding in file content.

Potential tasks:

1. move fee strings into resources if desired
2. normalize symbols and punctuation
3. make display strings consistent with rest of app style

---

## Backup / Export / Import Status

Implemented:

- fee data is exported in JSON version `4`
- `feeTracker` section includes:
  - firm profile
  - groups
  - clients
  - invoices
  - invoice line items
  - payments
  - invoice counters
- import restores fee records
- import remaps task `clientId` and `feeInvoiceId` links correctly

Tests added:

- `app/src/androidTest/java/com/example/todo/util/JsonExporterFeeTrackerTest.kt`

Still worth doing:

1. optional UI-level backup/import smoke test if Settings entry point is user-facing
2. optional import tests for malformed or partial fee sections

---

## Lifecycle / Business Logic Status

Implemented:

- settlement calculation test coverage exists
- edited issued invoices recalculate status from payments
- linked todo synchronization exists after invoice edits
- paid/cancelled closes linked todo
- re-open to outstanding reopens linked todo

Files:

- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardViewModel.kt`
- `app/src/main/java/com/example/todo/feetracker/domain/usecase/SettlementCalculator.kt`
- `app/src/test/java/com/example/todo/feetracker/SettlementCalculatorTest.kt`

Still worth doing:

1. instrumentation coverage for linked-task lifecycle
2. optional explicit tests around cancelling an invoice with prior payments

---

## Suggested Execution Order For Next Agent

Recommended order:

1. Clean `FeeDashboardScreen.kt`
   - remove `LegacyInvoiceDialog`
   - fix status label display
   - clean encoding artifacts
2. Run compile
   - `./gradlew.bat compileDebugKotlin`
3. Run unit tests
   - `./gradlew.bat testDebugUnitTest --console=plain`
4. Do manual/device lifecycle QA
5. Add instrumentation test for linked task lifecycle
6. Optional refactor of invoice editor out of giant screen file

---

## Important Files To Read First

If another agent is continuing from here, these are the best entry points:

- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardScreen.kt`
- `app/src/main/java/com/example/todo/feetracker/ui/FeeDashboardViewModel.kt`
- `app/src/main/java/com/example/todo/util/JsonExporter.kt`
- `app/src/main/java/com/example/todo/feetracker/domain/usecase/SettlementCalculator.kt`
- `app/src/test/java/com/example/todo/feetracker/SettlementCalculatorTest.kt`
- `app/src/androidTest/java/com/example/todo/util/JsonExporterFeeTrackerTest.kt`

---

## Last Verified Commands

These were the last known-good verification commands:

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest --console=plain
./gradlew.bat compileDebugAndroidTestKotlin
./gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.todo.util.JsonExporterFeeTrackerTest
```

---

## Notes

- The fee tracker is no longer in early scaffolding. Most remaining work is cleanup, consistency, and additional validation.
- The biggest correctness risks have already been addressed:
  - migration
  - invoice lifecycle recalculation
  - task link restoration
  - fee backup/import round trip
- The biggest remaining maintainability risk is the size and mixed responsibility of `FeeDashboardScreen.kt`.
