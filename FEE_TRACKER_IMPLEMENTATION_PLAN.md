# CA Fee Tracker — Implementation Plan for yata

> Audience: a coding agent implementing the integration. Read this top-to-bottom before
> writing code. It encodes the Phase 0 audit, the locked decisions, and a file-by-file
> build order. Companion specs: `Integration Prompt` (how) and `CA_Fee_Tracker_Build_Prompt.md`
> (what). Where the two disagree, **this plan wins** because it is grounded in the real codebase.

---

## 0. Phase 0 Audit — what yata actually is

### 0.1 Project shape
- **Single Gradle module** `:app`. Root project name `TodoExpressive`. No multi-module split.
- `applicationId` / `namespace` = `com.example.todo`.
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`. Java 17 / `jvmTarget 17`.
- `versionCode 1`, `versionName 0.1`. `isMinifyEnabled = false`. **No signing config, no Play assets → not published; early dev.** (Many `values-XX` locale folders exist, so it is translation-ready, but no release identity.)
- **Versions:** Kotlin `2.0.21`, AGP `8.7.2`, Compose BOM `2024.02.01`, Room `2.6.1`, Hilt `2.52`, KSP `2.0.21-1.0.26`, Navigation Compose `2.7.7`, DataStore `1.0.0`.
- **Version catalog** `gradle/libs.versions.toml` is the convention. *Exception:* three deps are hardcoded in `app/build.gradle.kts` (`hilt-navigation-compose:1.2.0`, `glance-appwidget:1.0.0`, `glance-material3:1.0.0`). New deps should go through the catalog.

### 0.2 UI layer
- **Jetpack Compose + Material 3**, single-activity (`MainActivity`, `@AndroidEntryPoint`).
- **Custom expressive theme** (`ui/theme/Theme.kt` → `TodoExpressiveTheme`): Material You dynamic color on API 31+, with a hand-built accent-seed fallback scheme (light / dark / amoled) and custom typography (Inter / Mono / Serif via `AppFontFamily`). Custom design tokens in `ui/theme/UiTokens.kt` (`UiSpacing`, `UiPadding`, `UiShape`, `UiSize`) and `ExpressivePalette` accents.
- **Navigation: `ModalNavigationDrawer`, NOT a bottom bar.** `HomeScreen` hosts the drawer (Today / Upcoming / Inbox / Priority + project list + Settings footer) and renders `TaskListScreen`. Routes are a sealed `Screen` class consumed by a single `NavHost` in `ui/navigation/AppNavigation.kt`.
- Screens collect state with `collectAsState` (a few spots; not always `collectAsStateWithLifecycle`).

### 0.3 Data layer
- **Room**, db file `todo_expressive.db`, `@Database(version = 4, exportSchema = false)` in `data/local/db/AppDatabase.kt`.
- Entities: `TaskEntity` (+ `TaskFtsEntity` FTS4), `ProjectEntity`, `LabelEntity`, `TaskLabelCrossRef`, `TaskUpdateEntity`, `PomodoroSessionEntity`, `AttachmentEntity`.
- Migrations registered in `di/DatabaseModule.kt`: `MIGRATION_2_3`, `MIGRATION_3_4` (defined as `companion object` vals on `AppDatabase`). **No `1_2` is registered and no `fallbackToDestructiveMigration` — the app effectively ships at v2+. Follow this pattern: hand-write SQL, never destructive.**
- **`exportSchema = false`** → no schema JSON is emitted today. This must change to test migrations (see §10).
- DAOs return `Flow` for reactive reads + `suspend` for one-shots. Repository pattern: `domain/repository/*Repository` interfaces, `data/repository/*RepositoryImpl`, bound by Hilt `@Binds` in `di/RepositoryModule.kt`. Entity↔domain `data/mapper/Mappers.kt`.
- **No type converters file in use yet** (entities store primitives + `@Embedded` `RepeatRule`; enums stored via Room's built-in enum support / `Priority` etc.). We will add a converters file for fee enums + money is plain `Long`.

### 0.4 Architecture
- **MVVM + Hilt**. `@HiltViewModel` view models expose `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)`. One DI graph (`SingletonComponent`) with `DatabaseModule`, `RepositoryModule`, `PreferencesModule`, `PomodoroModule`. Coroutines + Flow throughout.

### 0.5 Preferences
- **DataStore Preferences** (`data/local/datastore/UserPreferences.kt`, store name `user_settings`). One class, typed keys in a `companion object`, `Flow` getters + `suspend` setters, provided as `@Singleton`. `SettingsViewModel` mirrors each flow as a `StateFlow`.
- **Settings screen is largely mock**: `SettingsScreen.kt` has hardcoded "Mira Castellanos" profile and several `openDialog("…","not wired")` placeholders. Real wired settings: Theme mode toggle, Start-of-week, Hide-label, Export, Import. **This screen is the integration seam for the User Type selector + CA section.**

### 0.6 Existing capabilities to reuse
- **Reminder/scheduling engine = `AlarmManager`** (`notification/ReminderScheduler.kt` implements `TaskReminderScheduler`, `setExactAndAllowWhileIdle` with inexact fallback on `SecurityException`). Keyed by `task.id.toInt()`. `BootReceiver` reschedules via `getUpcomingReminderTasks`. **There is NO WorkManager.** Reuse the AlarmManager path for fee reminders — do not add WorkManager unless a daily sweep is genuinely needed (it is not, for the ticked scope).
- **Notifications:** `notification/NotificationHelper.kt` (channels), `ReminderReceiver`, `NotificationActionReceiver` (complete/snooze).
- **Backup/export:** `util/JsonExporter.kt` — SAF-based JSON export/import (`"version": 3`), invoked from `MainActivity` launchers wired into `SettingsScreen`. **Extend this for fee data.**
- **Widgets:** Glance (`widget/TodoListWidget*`). **App shortcuts:** none today. **App lock / security:** none today. **Date utils:** `util/DateFormatter.kt`, `util/RepeatCalculator.kt`.

### 0.7 Todo domain model
- `TaskEntity` (`data/local/db/entity/TaskEntity.kt`): `id, title, description, isCompleted, completedAt, createdAt, updatedAt, dueDate(ms), dueTime, reminderAt, @Embedded repeat_ RepeatRule?, priority, isStarred, sortOrder, projectId(FK SET_NULL), assignedTo, parentTaskId(self-FK CASCADE), completedBy, estimatedPomodoros, completedPomodoros`. Indices on `projectId`, `parentTaskId`.
- Completion = `isCompleted` + `completedAt`. Reminders = `reminderAt` + AlarmManager. Search = FTS4 (`tasks_fts`, `MATCH`).

### 0.8 Health
- Build status not run here; project looks coherent and should compile. **Test coverage ≈ none** (`junit` + `kotlinx-coroutines-test` deps present, no test sources found). Fragile/mock areas: `SettingsScreen` placeholder content, hardcoded user identity. `exportSchema=false` blocks migration tests until flipped.

### 0.9 Identity
- Not published, no signing, default `com.example.todo` id, `0.1`. Safe to evolve schema and UI.

### 0.10 Audit conclusion
**Pattern A** (feature inside yata, gated by User Type). **Room exists → same database, versioned migration v4→v5, real foreign keys** between tasks and clients/invoices. Reuse: DataStore prefs, Hilt graph, AlarmManager reminders, JsonExporter backup, MaterialTheme, drawer navigation.

---

## 1. Locked decisions (defaults applied; change only if user overrides)

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | DB strategy | **Same DB, FKs, migration v4→v5** | Room present; enables `Task.clientId` FK and real settlement joins. |
| 2 | Modules this pass | **Core `[C]` only** per companion §12 picker: Clients(1), Groups(2), Invoicing(5), Payments/Receipts(8), TDS(10), Dashboard(14), Settings(20). No GST/PDF/reminders-module/security/exports modules — but **invoice due reminders reuse the existing AlarmManager** and **backup reuses JsonExporter**. | Matches ticked boxes; keeps scope tight. |
| 3 | Fees entry point | **Drawer destination "Fees"** (yata has no bottom bar) | Don't redesign nav. |
| 4 | Auto-todo-on-invoice | **Default ON**, toggle in CA settings | Companion default. |
| 5 | User types | Ship `GENERAL`, `CHARTERED_ACCOUNTANT`; model as extensible enum | Future-proof, no extra UI now. |
| 6 | Money | **Integer paise (`Long`)**, format only at display | Companion + avoids float error. |
| 7 | Package root | `com.example.todo.feetracker.*` | Single removable seam. |

If any of these is wrong for the user, only items 1, 3, 4 change meaningful work; flag before Step 3.

---

## 2. Package layout (the removable feature island)

All new code lives under `app/src/main/java/com/example/todo/feetracker/` except the **three explicit seams** listed in §3.

```
feetracker/
  data/
    local/entity/        FirmProfileEntity, ClientEntity, GroupEntity,
                         InvoiceEntity, InvoiceLineItemEntity, PaymentEntity,
                         InvoiceCounterEntity
    local/dao/           FirmProfileDao, ClientDao, GroupDao, InvoiceDao,
                         InvoiceLineItemDao, PaymentDao, InvoiceCounterDao
    local/FeeConverters.kt
    repository/          FirmProfileRepositoryImpl, ClientRepositoryImpl,
                         GroupRepositoryImpl, InvoiceRepositoryImpl,
                         PaymentRepositoryImpl
  domain/
    model/               FeeEnums.kt (EntityType, ClientStatus, InvoiceType,
                         InvoiceStatus, PaymentMode, PaymentType, UserType),
                         domain data classes, FinancialYear.kt
    repository/          *Repository interfaces
    usecase/             RaiseInvoiceUseCase, RecordPaymentUseCase,
                         InvoiceNumberAllocator, SettlementCalculator
  feature/
    FeatureAvailability.kt        (gate; reads prefs)
    FeeFeaturePrefs.kt            (DataStore-backed, or fold into UserPreferences)
  ui/
    navigation/FeeScreen.kt, FeeNavGraph.kt
    dashboard/ receipts/ invoices/ clients/ groups/ settings/ onboarding/
    component/ (FeeCurrencyText, StatusChip, FastReceiptSheet, etc.)
    format/    IndianCurrency.kt
  di/FeeModule.kt                 (DAOs + repo binds + use cases)
  backup/FeeBackup.kt             (serialize/deserialize fee tables)
```

**Convention mirror:** match yata exactly — `domain/repository` interface + `data/repository` Impl + Hilt `@Binds`; `@HiltViewModel` + `StateFlow` via `stateIn(WhileSubscribed(5_000))`; design tokens from `ui/theme/UiTokens.kt`; `MaterialTheme.colorScheme`/`typography` only, no new colors.

---

## 3. The three seams (only places fee types touch todo code)

1. **Navigation registration** — `AppNavigation.kt` calls `feeNavGraph(navController, gate)` and `HomeScreen` drawer shows a "Fees" row, both **conditional on `gate.feesVisible`**.
2. **FeatureAvailability gate** — single source of truth; injected where needed.
3. **Task↔client link** — one nullable column `clientId` added to `TaskEntity` + a few `TaskDao` queries. This is the only edit to an existing entity.

**Removability contract:** deleting the `feetracker/` package + reverting these three seams (and the migration step) must leave yata compiling. Keep seam edits small and clearly commented (`// FEE-TRACKER SEAM`).

---

## 4. Activation model (Step 2 — build first, no fee data yet)

### 4.1 UserType enum
`domain/model/FeeEnums.kt`:
```kotlin
enum class UserType { GENERAL, CHARTERED_ACCOUNTANT }   // extensible
```

### 4.2 Preferences — extend `UserPreferences.kt` (reuse existing DataStore store)
Add keys + flows + setters (mirror existing style):
```kotlin
val USER_TYPE          = stringPreferencesKey("user_type")          // default GENERAL
val FEE_TRACKING_ON    = booleanPreferencesKey("fee_tracking_on")   // default false
val INVOICING_ON       = booleanPreferencesKey("invoicing_on")      // default false
val CA_ONBOARDED       = booleanPreferencesKey("ca_onboarded")
val AUTO_TODO_ON_INVOICE = booleanPreferencesKey("auto_todo_on_invoice") // default true
```
- `userTypeFlow: Flow<UserType>` (parse name, fallback GENERAL).
- `feeTrackingOnFlow`, `invoicingOnFlow`, `autoTodoOnInvoiceFlow`, `caOnboardedFlow`.
- `setUserType(t)`: when switching to CA the first time (`!caOnboarded`), set `feeTrackingOn=true`, `invoicingOn=true` and leave onboarding sheet to flip `caOnboarded` after it is shown/skipped.
- **Toggles never delete.** Switching to GENERAL only writes the pref; data stays.

### 4.3 FeatureAvailability gate
`feetracker/feature/FeatureAvailability.kt` — `@Singleton`, injected `UserPreferences`:
```kotlin
class FeatureAvailability @Inject constructor(prefs: UserPreferences) {
    val feesVisible: Flow<Boolean>      // userType==CA && feeTrackingOn
    val invoicingVisible: Flow<Boolean> // feesVisible && invoicingOn
    val caSettingsVisible: Flow<Boolean>// userType==CA   (so user can re-enable)
}
```
All gate logic lives here. No scattered `if (userType==CA)` in screens.

### 4.4 Settings integration (seam-light — edit `SettingsScreen` + `SettingsViewModel`)
- Add a **User Type** `SettingsGroup` (always visible) with a selector (GENERAL / "Chartered Accountant (CA)"). On select-CA-first-time → show onboarding sheet.
- Add a **"CA / Fee Tracker"** `SettingsGroup` rendered only when `caSettingsVisible`: Fee Tracking switch, Invoicing switch (enabled only while Fee Tracking on), firm profile entry, invoice prefix, default TDS rate, default payment mode, auto-todo-on-invoice switch, fee backup/export, (app-lock row stub — out of scope this pass, leave TODO).
- `SettingsViewModel` gains the new `StateFlow`s + setters mirroring existing pattern.

### 4.5 Onboarding sheet
`feetracker/ui/onboarding/CaOnboardingSheet.kt` — `ModalBottomSheet`: one-line explanation, optional skippable firm-profile capture (firmName, proprietorName, gstin/pan optional), "you can toggle/switch back anytime" note. On finish/skip → `setCaOnboarded(true)`.

### 4.6 Reactivity (no restart)
Drawer "Fees" row, nav graph guard, settings sections, and (future) shortcuts/widgets all collect the gate `Flow`s, so toggles take effect live. The nav graph always *registers* the fee composables, but the drawer entry and any deep-link guard check `feesVisible`; if a user is on a fee screen when toggled off, pop back to Home.

### 4.7 Verify GENERAL untouched
After Step 2: with default prefs, the app is byte-for-byte the old todo experience except one new "User Type" row in Settings. No fee nav, no fee notifications, no fee shortcuts.

---

## 5. Data layer (Step 3)

### 5.1 Entities (paise = `Long`; dates = epoch ms `Long`; enums via converters)

**FirmProfileEntity** (single row, id=1) `[C]`
`id(=1), firmName, proprietorName, membershipNo?, frn?, address?, phone?, email?, pan?, gstin?, stateCode?, upiId?, bankName?, bankAccount?, bankIfsc?, bankBranch?, logoUri?, signatureUri?, isGstRegistered=false, invoicePrefix="INV", gstRate=18, defaultTdsRate=10, termsText?, presumptiveScheme="none"`.

**ClientEntity** `[C]`
`id(auto), name, entityType(EntityType=INDIVIDUAL), phone?, altPhone?, email?, address?, stateCode?, pan?, gstin?, groupId?(FK Group SET_NULL, indexed), status(ClientStatus=ACTIVE), defaultFeeNote?, referralSource?, onboardedAt?, avatarUri?, notes?, createdAt`.

**GroupEntity** `[C]`
`id(auto), groupName, notes?, createdAt`.

**InvoiceEntity** `[C]`
`id(auto), invoiceNumber(String, unique-per-FY), type(InvoiceType=TAX_INVOICE), clientId(FK Client RESTRICT, indexed), dateRaised, dueDate?, period?, subTotalPaise, discountType(=NONE), discountValuePaise=0, taxableValuePaise, gstApplicable=false, reverseCharge=false, cgstPaise=0, sgstPaise=0, igstPaise=0, roundOffPaise=0, totalPaise, settledPaise=0(cached), status(InvoiceStatus=DRAFT), termsText?, notes?, financialYear(String e.g. "2025-26", indexed)`.

**InvoiceLineItemEntity** `[C]`
`id(auto), invoiceId(FK Invoice CASCADE, indexed), serviceTemplateId?(no FK this pass), description, period?, quantity=1, ratePaise, amountPaise, sacCode?`.

**PaymentEntity (FeeReceipt)** `[C]`
`id(auto), receiptNumber(String, auto), clientId(FK Client RESTRICT, indexed), invoiceId?(FK Invoice SET_NULL, indexed), type(PaymentType=PAYMENT), amountReceivedPaise, tdsDeductedPaise=0, dateReceived, mode(PaymentMode=GPAY), purpose?, period?, referenceNo?, chequeStatus?, attachmentUri?, notes?`.

**InvoiceCounterEntity** `[C]`
`financialYear(String, PK), lastSerial(Int)`.

> Settlement is computed; `settledPaise`/`status` are caches recomputed on payment change (see §6). `AppSettings`-style fee config lives in DataStore (§4.2) + FirmProfile, not a table.

### 5.2 Converters
`feetracker/data/local/FeeConverters.kt` — `@TypeConverter` for each fee enum (store `name`). Register on `AppDatabase` (`@TypeConverters(FeeConverters::class)` — additive, doesn't affect existing tables).

### 5.3 AppDatabase changes
- Add the 7 entities to `@Database(entities=[…])`.
- Bump `version = 4` → **`5`**.
- Add abstract DAO getters.
- Add `@TypeConverters(FeeConverters::class)`.
- **Set `exportSchema = true`** and configure the schema dir (see §10) so migration tests can run. Generate schema JSON for v5 (and ideally backfill v4) by building once with the room schema arg.

### 5.4 MIGRATION_4_5 (in `AppDatabase` companion, registered in `DatabaseModule`)
Hand-written SQL — create the 7 fee tables + indices, **and** add the task seam column:
```sql
ALTER TABLE tasks ADD COLUMN clientId INTEGER;     -- nullable, no FK enforced via ALTER
CREATE INDEX IF NOT EXISTS index_tasks_clientId ON tasks (clientId);
-- plus: invoiceId/linkedInvoiceId on tasks if synergy #2 needs a back-link (see §8.2)
ALTER TABLE tasks ADD COLUMN feeInvoiceId INTEGER;
```
Then `CREATE TABLE` for `firm_profile, clients, groups, invoices, invoice_line_items, payments, invoice_counter` with FKs/indices exactly matching the Room entity definitions (Room validates schema on open — match column order/types or the open will throw). **Never** `fallbackToDestructiveMigration`. Register with `.addMigrations(AppDatabase.MIGRATION_4_5)` in `DatabaseModule`.

> Note on the FK on `tasks.clientId`: Room can't add a real FK via `ALTER TABLE`. Either (a) accept a loose indexed `clientId` with app-level integrity (recommended, matches the "survive deletion/hiding" contract), or (b) do a table-rebuild migration. **Choose (a)** — simpler, and the disable-contract already requires graceful "linked item unavailable". Update `TaskEntity` with `clientId: Long? = null` and `feeInvoiceId: Long? = null` but **do not** declare them as Room `ForeignKey`s.

### 5.5 DAOs & repositories
- DAOs: `Flow` reads, `suspend` writes, mirror `TaskDao` style. Key queries: clients by status/group/search; invoices by FY/status/client; outstanding (status DUE/PARTIALLY_PAID); payments by client/invoice/mode/date-range; counter upsert.
- Repos: interface in `domain/repository`, Impl in `data/repository`, `@Binds` in `FeeModule`.
- Seam DAO additions on `TaskDao`: `getTasksForClient(clientId): Flow<List<TaskEntity>>`, `getTaskByFeeInvoiceId(invoiceId): TaskEntity?`.

---

## 6. Core business logic (Step 4)

`domain/usecase/`:
- **`InvoiceNumberAllocator`** — gap-free per FY: `{prefix}/{FY}/{0001}`. Transactionally read+increment `InvoiceCounterEntity` for the FY (use Room `@Transaction`). Drafts: **do not burn a number until issued** (allocate on transition DRAFT→DUE). Cancelled invoices keep their number.
- **`SettlementCalculator`** — `settled = Σ payment.amountReceivedPaise + Σ payment.tdsDeductedPaise` for payments linked to the invoice. Status: `>= total → PAID`, `0 < settled < total → PARTIALLY_PAID`, else `DUE`; `OVERDUE` derived when DUE/PARTIAL and `dueDate < today`. Recompute + persist `settledPaise`/`status` whenever a linked payment is inserted/edited/deleted.
- **`RaiseInvoiceUseCase`** — build invoice + line items, allocate number on issue, optionally fire synergy #2 (auto-todo).
- **`RecordPaymentUseCase`** — insert payment, recompute settlement; if it fully settles a linked invoice, complete the linked todo (synergy #3).
- **`FinancialYear`** (`domain/model/FinancialYear.kt`) — map a date (1 Apr–31 Mar) to `"YYYY-YY"`; current FY; FY list; `dateToFy(ms)`. All FY-scoped totals use this.
- **`IndianCurrency`** (`ui/format/IndianCurrency.kt`) — paise→`₹1,25,000.00` lakh/crore grouping; parse user input → paise.

Edge cases (companion §10): block/soft-block client delete when invoices/payments exist (offer Inactive); recompute on payment edit/delete; cancel invoice keeps payments (warn); prevent negative/over-settlement; FY boundary for numbering; guard duplicate numbers under concurrency (transaction).

---

## 7. Core screens (Step 4) — all gated, all native to yata

Build under `feetracker/ui/`, using `UiTokens`, `MaterialTheme`, M3 components, empty states, swipe actions, confirm dialogs (match `TaskRow`, `YataCard`, `SectionHeader`, `UndoBar` patterns).

1. **Fee Dashboard** `[C]` — KPI cards (received this month, total outstanding, pending invoice count, overdue count), recent payments, quick-add FAB → Fast Receipt sheet, **FY switcher**. When `invoicingVisible == false`: hide outstanding/due/overdue cards, show a "collection" dashboard only.
2. **Receipts** `[C]` — filterable list (client/group/mode/date); **Fast Receipt sheet** (client, amount, date=today, mode=GPay, purpose; optional: period, ref, TDS, link-to-invoice). Invoice-link field hidden when `!invoicingVisible`.
3. **Invoices** `[C]` (only when `invoicingVisible`) — list w/ status chips + filters; create/edit w/ line items; detail w/ breakup, linked payments, actions (record payment, cancel). PDF/share are **out of scope** this pass (no Module 5 `[R]` ticked) — leave hooks/TODOs.
4. **Outstanding** `[R]`-ish (only when `invoicingVisible`) — DUE/PARTIAL sorted by age with ageing buckets. (Companion §12 didn't tick Module 15, but Outstanding is a Core dashboard concept; build the list, skip exportable ageing report.)
5. **Clients** `[C]` — searchable list; add/edit; **client detail**: history (invoices + receipts), running balance, quick actions (call/WhatsApp/new receipt/new invoice) **+ open todos for this client** (synergy #1).
6. **Groups** `[C]` — create/edit; group detail with consolidated received + outstanding across members.
7. **Fee Settings subset** — firm profile editor, invoice prefix, TDS default, default payment mode (lives in the CA settings section, §4.4).

Nav: `FeeNavGraph` nested under a single `Screen.Fees` route; internal routes `FeeScreen.Dashboard/Receipts/Invoices/Clients/Groups/...`. Invoices + Outstanding routes only reachable when `invoicingVisible`.

---

## 8. Synergy features (Step 6 — after core works)

All require `feesVisible`; invoice-linked ones also require `invoicingVisible`. Cross-refs must **survive deletion or toggle-off** → render "linked item unavailable"; never cascade across domains.

1. **Task↔client link** — `TaskEntity.clientId` (added in migration). Task composer/detail gets an optional client picker (rendered only when `feesVisible`). Client detail shows open tasks. With fees off, the tag isn't rendered; the column stays.
2. **Auto-todo on invoice** (default ON, toggle in CA settings) — `RaiseInvoiceUseCase` inserts a `TaskEntity` "Collect ₹{amount} from {client} ({service}, {period})", `dueDate = invoice.dueDate`, `clientId`, `feeInvoiceId = invoice.id`, scheduled via existing `ReminderScheduler` if a reminder time applies. Cancelling the invoice completes/cancels that todo (look up via `getTaskByFeeInvoiceId`). The todo remains a normal todo even if fees later disabled.
3. **Receipt from task** — completing a todo with `feeInvoiceId`/`clientId` prompts "Record receipt?" → opens Fast Receipt sheet pre-filled. A receipt that fully settles the invoice auto-completes its linked todo (in `RecordPaymentUseCase`).
4. **Recurring work as todos** — *Module 12 not ticked; SKIP this pass* (note as future).
5. **Unified Today** — yata's Today list can include due fee follow-ups / overdue invoices as a **distinct row type**, gated, amounts hidden under privacy mode. *Lightweight version optional; can defer.*
6. **Search** — yata has FTS search over tasks. Indexing clients/invoices is **deferred** (would need new FTS tables); note as future. Don't block core.

> Items 4–6 are explicitly deferred to keep this pass within the ticked Core scope. Implement 1–3 fully.

---

## 9. Backup (extend, don't replace)

Extend `util/JsonExporter.kt` (or add `feetracker/backup/FeeBackup.kt` invoked by it) to serialize the 7 fee tables as a **separate, clearly-labeled `"feeTracker"` section** of the same JSON, with independent restore. Bump JSON `"version"` 3→4 (keep reading v3). **Always include fee data regardless of toggle state.** Reuse the existing id-remap-on-import approach (insert, map old→new ids, resolve FKs in a second pass: groups→clients→invoices→line items→payments→counter). No new dependency.

---

## 10. Testing & schema

- Flip `AppDatabase` to `exportSchema = true`; add to `app/build.gradle.kts`:
  ```kotlin
  ksp { arg("room.schemaLocation", "$projectDir/schemas") }
  // and: sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }
  ```
- **MigrationTest (required):** `androidTest` using `MigrationTestHelper` — create v4 with sample task data, run `MIGRATION_4_5`, assert tasks survive and fee tables exist. This is the non-negotiable migration test.
- **Unit tests (JVM):** `InvoiceNumberAllocator` (gap-free, FY reset, cancel keeps number, concurrency guard), `SettlementCalculator` (TDS-inclusive settlement, partial, over-settlement prevention), `FinancialYear` (boundary 31 Mar / 1 Apr), `IndianCurrency` (lakh/crore formatting + parse round-trip).
- **Regression:** existing app builds + behaves identically with default (GENERAL) prefs.

---

## 11. Build order & per-commit checklist

Each commit must leave the app **buildable**. Commit messages: Conventional Commits.

- **Commit 1 — Activation model (Step 2).** UserType enum, `UserPreferences` keys/flows, `FeatureAvailability`, settings User-Type selector + empty CA section + onboarding sheet, drawer "Fees" row guarded (routes to a placeholder). *Verify GENERAL unchanged.* No fee tables yet.
- **Commit 2 — Data layer (Step 3).** Entities, `FeeConverters`, `AppDatabase` v5 + `MIGRATION_4_5` (incl. `tasks.clientId`/`feeInvoiceId`), DAOs, repos, `FeeModule`, `exportSchema=true` + **migration test green**.
- **Commit 3 — Core logic + screens (Step 4).** Use cases (numbering/settlement/FY/currency), Fee Dashboard, Receipts + Fast Receipt sheet, Clients, Groups, fee settings subset. Wire `FeeNavGraph`. **Verify Invoicing-OFF renders correctly** (no invoice link field, no outstanding cards, Invoices/Outstanding routes absent).
- **Commit 4 — Invoicing.** Invoices list/create/detail, Outstanding list, numbering on issue, cancel semantics. Gated on `invoicingVisible`.
- **Commit 5 — Synergy 1–3.** Task↔client link UI, auto-todo on invoice, receipt-from-task. Disable/deletion graceful-degradation tested.
- **Commit 6 — Backup extension.** Fee section in JSON export/import + restore test.
- **Commit 7 — Polish.** Empty states, swipe actions, confirm dialogs, dynamic "Add receipt" shortcut (registered only when `feesVisible`; unregister on toggle-off), toggle-matrix QA, all tests green.

---

## 12. Guardrails recap (must hold)
1. Zero visible change for GENERAL users beyond the Settings User-Type row.
2. Toggles hide, never delete; all combinations reversible; re-enable restores intact.
3. Schema change via versioned migration + migration test; existing task data survives; never destructive.
4. Feature stays cleanly removable: delete `feetracker/` + revert the 3 seams + the migration → yata compiles.
5. No new heavyweight deps; reuse Room/Hilt/DataStore/AlarmManager/JsonExporter/MaterialTheme. (New deps, if any, via `libs.versions.toml`.)
6. Fully offline; no network introduced.
7. Disabled features pause scheduled reminders (cancel via `ReminderScheduler`) and reschedule future ones on re-enable; no fee notification fires while hidden.

---

## 13. Open assumptions (confirm if convenient; defaults are safe)
- **Q1 DB:** same DB + FKs — **assumed yes** (Room present).
- **Q2 Modules:** Core `[C]` set only (Clients, Groups, Invoicing, Payments/Receipts, TDS, Dashboard, Settings) + reminders-via-AlarmManager + backup-via-JsonExporter; GST/PDF/security/exports/widgets **deferred**.
- **Q3 Entry point:** drawer "Fees" row — **assumed** (no bottom bar exists).
- **Q4 Auto-todo-on-invoice:** **default ON**.
- **Q5 Other user types:** none planned; enum kept extensible.

If the user wants GST, invoice PDF, app-lock, or the deferred synergy (4–6) in this pass, add them as Steps 5/8 of the companion `[R]` set after Commit 7.
