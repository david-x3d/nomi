# CONTINUE_HISTORY.md — Nomi engineering handoff

Self-contained handoff for a fresh agent. **Updated after the §10 History task was completed.**
The test suite is now green (616 tests, 72 classes, 0 failures). The release flow in §7 is still
outstanding. Read §10.4 and §11 before doing anything.

---

## 0. Read this first — three things that will waste your time

1. **Orphaned Gradle processes.** The three `:app:testDebugUnitTest` invocations that §6 calls
   "interrupted" were not dead — they were still running, each with a test worker JVM pinned at
   100% CPU. Five workers were competing, which is why a test run took 20+ minutes and appeared
   to hang. Before starting a build, check and clean:
   ```bash
   ps -eo pid,etime,pcpu,args | grep -E "testDebug/jniLibs|GradleWrapperMain" | grep -v grep
   ```
   Kill anything that is not your own invocation. After killing them a full run takes ~4 minutes.
2. **A test that hangs is a bug in the test, not a slow suite.** `CatalogueCoverageTest` had an
   infinite loop and had therefore *never once run to completion* — see §11.1. If a test seems to
   hang, `jstack` the worker; do not wait it out.
3. **Verify scanners offline before running Gradle.** Both the catalogue-key scanner and the
   call-site scanner in `CatalogueCoverageTest` were wrong in ways that only a real run would
   reveal. Replicating them as a throwaway Java program and running it over the tree took seconds
   and found four bugs. `/tmp/opencode/Coverage5.java` is that harness.

---

## 1. Repo state

| | |
|---|---|
| Path | `/home/david/Transfer/Nomi/repo` |
| Branch | `main` |
| HEAD | `f4be7fa` "Record the verified v2.3.0 signature and artifact digest" |
| Upstream | `origin https://github.com/david-x3d/nomi.git` (`gh` authenticated as `david-x3d`, scopes `repo workflow gist read:org`) |
| Latest published release | **v2.3.0** (`gh release list` confirms) |
| Working tree | still **uncommitted** — both the audit-fix set and the History work |
| Gradle | 9.5.0 via wrapper, working |

**No commit has been made.** §7.6 still describes the intended single audit-fix commit. The
History work in §10 is deliberately separable, so committing it on its own afterwards is fine and
is what §10.10 suggests.

### Target release
- `versionName` **2.4.0**, `versionCode` **118** (from 117 — increment by exactly 1)
- tag `v2.4.0`, release title `Nomi v2.4.0`, asset `Nomi-v2.4.0-release.apk`
- `app/build.gradle.kts:16-17` still says `versionCode = 117` / `versionName = "2.3.0"` — **not yet
  bumped.**

### Expected signing certificate (do not change; do not generate a new key)
```
SHA-256: 9344CD48425664BF8B010AC6CDAFF835FB4E9524591DDF7A00E98184867EE8BF
```
> **The fingerprint in the previous handoff was WRONG — it was 63 hex characters, one short, and
> could never match a SHA-256.** The value above is the corrected one, taken from the actually
> published `Nomi-v2.3.0-release.apk` (downloaded via `gh release download v2.3.0`) and
> independently confirmed against `~/.android/debug.keystore` and a locally built debug APK. All
> three agree. The key itself was never wrong; only the recorded digest was.

After building, run `apksigner verify --print-certs` and compare byte-for-byte. If it does not
match: **STOP, do not tag, do not release.**

---

## 2. Repo-hygiene fix (already done — do not redo)

The working tree arrived with a repo-wide CRLF↔LF change across **1,476 files** (521,146
insertions / 521,146 deletions), and `gradlew` had lost its executable bit **and** been given
CRLF, so `./gradlew` could not run at all.

Done:
- Added **`.gitattributes`** (new, untracked): `* text=auto eol=lf`, `gradlew text eol=lf`,
  `gradlew.bat text eol=crlf`, `app/schemas/** text eol=lf -diff`, explicit binary list.
  Note `*.so` was initially written as `text eol=lf` and **corrected to `binary`**.
- Set `git config core.autocrlf input` and `core.eol lf` (repo-local).
- Ran `git checkout -- .` to restore all 1,476 files to HEAD content (safe: the only uncommitted
  content was line endings).

**Do not `git add --renormalize .`** — that would stage a ~1,476-file diff.

An earlier attempt used `perl -i -pe 's/\r\n$//'` over the working tree and **corrupted
`.gitignore`** (joined every line). It was recovered with `git checkout --`. If you touch line
endings, verify with `git diff --ignore-cr-at-eol` first.

---

## 3. What was implemented

### 3.1 Critical — researched-item editing made the whole meal unsaveable

**Defect.** `AnalyzedItemEditDialog` wrote corrected `calories`/`protein`/`carbohydrateGrams`/`fatGrams`
straight onto an `AnalyzedFoodItem` via `item.copy(...)`, leaving the old `servingValidation`
attached. `AppViewModel.confirmLogging:1516` then called
`ServingNutritionNormalizer.validateBeforeSave`, which re-derives each nutrient from the recorded
per-100 basis and compares with `NUTRENT_TOLERANCE = 1e-7`. **Any** user edit broke that invariant
and the entire multi-item meal failed to save with "The serving amount could not be validated."

**Fix — recompute, do not bypass.**
- New `ServingNutritionNormalizer.applyUserNutrientCorrection(item, calories, proteinGrams, carbohydrateGrams, fatGrams)`.
  The user is correcting what the food *contains*, not how much was eaten, so the logged amount
  and source serving stay put and the per-100 basis is re-derived
  (`calories / (loggedBaseAmount/100)`). `requirePhysicallyPossiblePer100` is still applied to the
  corrected per-100 values, so an impossible correction is still refused.
- New private helpers in the same file: `correctedItem(...)`, `per100ForLogged(...)`,
  `requireFiniteNonNegative(...)`, `dimensionFromStorageName(...)`.
- Sugar/saturated-fat are components of their parents and are **not** edited by the dialog, so
  `requireComponentWithinParent` is re-checked against the corrected parents.
- Unvalidated items (manual/barcode/library) take a path with no basis to preserve but are still
  bounded by the same physical-plausibility check.
- `AnalyzedItemEditDialog.kt` now calls it, and its decimal parse uses `DecimalInput`.
- Result: correction succeeds, displayed == persisted, impossible values still rejected.

**Tests:** `app/src/test/java/com/nomi/app/ai/validation/UserNutrientCorrectionTest.kt` (12 tests).

### 3.2 Critical — decimal comma

New canonical parser **`app/src/main/java/com/nomi/app/domain/DecimalInput.kt`**:
`DecimalInput.parseOrNull(raw)` (point or comma, tolerant of NBSP/space grouping, rejects a second
separator, rejects non-finite/negative) and `DecimalInput.canonical(value)`.

Fixed surfaces:
- `ui/logging/LoggingModels.kt` — `ManualFoodDraft.isValid` used bare `toDoubleOrNull()`, so a
  German/FR/ES/… user typing `1,5` left the Save button **permanently disabled** with no message.
- `ui/app/AppViewModel.kt` — `ManualFoodDraft.toLog()` used `requireNotNull(x.toDoubleOrNull())`;
  now a local `field()` helper on `DecimalInput`. Also added
  `private companion object { const val MANUAL_SOURCE_NAME = "Manual entry"; const val LIBRARY_SOURCE_NAME = "Nomi food library" }`
  to stop the English literals being duplicated.
- `domain/usecase/PortionEditParser.kt` — `FRACTION_OF_COUNT` was the only sibling pattern
  accepting `\.` and not `[.,]`. Also fixed while in there:
  - `normalize()` now maps `×`/`✕` → `x` and `⋅` → `.` (the punctuation strip was deleting them)
  - `parseBareDecimal(...)` + `BARE_DECIMAL` — a bare `0,5` / `1,5` is a share of what is logged
  - `REPLACED_AMOUNT` was matching and **deleting the stated amount itself**
    (`200g instead of 400g` → strip ate `200g`). Replaced with
    `REPLACEMENT_CLAUSE = Regex("\\s+(?:instead|statt|anstatt)\\s+.*$")`, split in `parseOrNull`
    **before** `stripFillerWords` (because "instead" and "of" are themselves filler words).
  - `parsePercentage`, `parseMultiplier`, `parseFractionOfCount`, `parseExplicitAmount` now use `DecimalInput`
  - `FRACTION_OF_COUNT` accepts `(?:of|von)?` and `stucken?`
  - dispatch order: …`parseFraction` ?: `parseBareDecimal` ?: `parseExplicitAmount`

**Tests:** `app/src/test/java/com/nomi/app/domain/usecase/DecimalCommaMatrixTest.kt` (12 tests).
All 10 tests failed before the fix (i.e. the bugs were proven, not assumed).

### 3.3 Critical — backup durability

- **Clock skew.** `BackupValidator.timestamps` had `if (updated < created) add(issue, …, "precedes
  creation")`. One backwards NTP/manual clock correction therefore made **every later export fail**
  (export calls `validate`) **and** made an already-taken backup refuse to import. The check is
  removed; the ordering quirk is now harmless. Note: the data itself is *not* normalised on write
  — deliberately, since the validator no longer rejects it and partial normalisation was judged
  higher risk than the cosmetic inconsistency.
- **Size ceiling.** `MAX_BACKUP_BYTES` 16 MiB → **256 MiB**; added
  `MAX_BACKUP_FILE_BYTES = 64 MiB` for the compressed output.
- **Memory / streaming.** `kotlinx.serialization`'s `Json` has **no** streaming encoder
  (`encodeToStream` and `newEncoder` do not exist in 1.9.0 — verified by `javap` against the jar).
  So `exportTo` keeps `json.encodeToString` but writes through
  `OutputStreamWriter(gzip, UTF_8)`, removing the previous full `encodeToByteArray()` doubling.
  The old code could `OutOfMemoryError` — an `Error`, so uncatchable by the `catch (Exception)` above it.
- **Compression + backwards compatibility.** Export is now **gzip**; `readCapped()` sniffs the
  0x1f 0x8b magic bytes via `PushbackInputStream` and inflates, else reads as plain JSON.
  **Old plain-JSON backups still import.** (`BufferedInputStream.mark()` returns `Unit` in Kotlin
  because Java's is `void` — that is why `PushbackInputStream` is used.)
- New private helpers: `ByteBudget` (`internal`, so tests can use it), `CountingOutputStream`.
  `NomiBackupService` is now `@OptIn(ExperimentalSerializationApi::class)`.
- **Dropped preferences now round-trip.** `BackupPreferencesV1` gained defaulted
  `micronutrients: BackupMicronutrientsV1`, `calorieEstimateBias`, `goalsCardStyle`,
  `smartFallbackProvider`; `BackupEntityMappings.kt` gained `toPreferences()`;
  `NomiBackupService.applyBackupPreferences` applies them (smart-fallback only when `model` is
  non-blank, so an old file does not blank the existing provider).

**Tests:** `app/src/test/java/com/nomi/app/data/backup/BackupDurabilityTest.kt` (9 tests),
including a hand-written legacy-JSON blob proving an old backup still decodes.

### 3.4 Unit system — the 180 lb → 180 kg corruption

**Defect.** `AppPreferences.weightUnit` / `heightUnit` were written by Settings, echoed back by
Settings, and carried in every backup — and read by **nothing**. `WeightEntryDialog` hard-coded
`suffix = "kg"` and `onSave(kilograms)`, and `ProgressScreen` hard-coded `" kg"`. An Imperial user
entering their real 180 lb stored **180 kg**, which then fed Mifflin-St Jeor BMR, the calorie target
and `StepCalorieEstimator`. README claimed "metric and US customary quantities" — false.

**Fix — metric stays canonical; convert only at boundaries.**
- New **`app/src/main/java/com/nomi/app/domain/UnitFormatter.kt`**: `kilogramsToPounds`,
  `poundsToKilograms`, `centimetersToInches`, `inchesToCentimeters`, `formatWeight`,
  `weightValue`, `formatHeight`, `weightUnit`, `heightUnit`, `formatNumber` (locale-aware via
  `DecimalFormatSymbols`), `parseWeightToKilograms`, `parseHeightToCentimeters`,
  `isPlausibleWeightKilograms`, `parseFeetInchesToCentimeters`, `centimetersToFeetAndInches`.
  Constants `POUNDS_PER_KILOGRAM` / `CENTIMETERS_PER_INCH` are `internal` so tests can assert them.
  `parseWeightToKilograms` returns **null** rather than a wrong number, so a mis-parse can never
  silently write pounds as kilograms.
- `ui/app/SupportingScreens.kt` — `WeightEntryDialog(metric: Boolean, …)` parses through
  `UnitFormatter`, label is "Weight in kg"/"Weight in lb", suffix from `weightUnit(metric)`.
- `ui/app/NomiRoot.kt` — passes `metric = preferences.weightUnit == WeightUnitPreference.KILOGRAMS`
  (added import for `WeightUnitPreference`).
- `ui/progress/ProgressScreen.kt` — `ProgressScreen(state, metric, …)`, `WeightSection(… metric …)`,
  `WeightChart(… metric …)`; the current-weight headline, the three milestones and the chart's
  accessible summary all format through `UnitFormatter`.
- **No existing database values were rewritten.** Storage is untouched metric.
- Two new catalogue keys added: "Weight in kg" and "Weight in lb".

**Tests:** `app/src/test/java/com/nomi/app/domain/UnitFormatterTest.kt` (14 tests) including
round-trips both directions and the literal 180 lb case.
**Gap to close:** `ProfileSettingsScreen` (height/target weight, `suffix = "cm"`/`"kg"`) and
`ProfileEdit.validate` (hard-coded English range messages) are **not yet** unit-aware.

### 3.5 Reduced motion

**Defect.** `animationsAreDisabled()` had exactly **one** caller (`WelcomeStoryboard.kt:246`).
All four `NomiMotion` specs were fixed tweens, ignoring `ANIMATOR_DURATION_SCALE`; two
**endless** animations ran regardless: the favicon shimmer (`WebsiteFavicon.kt`) and the typing
dots (`NomiNotesTodayScreen.kt` `while (true)`).

**First attempt — `@Composable` specs — was reverted.** Making the four spec functions
`@Composable` broke **9** call sites, because `transitionSpec` and `sizeAnimationSpec` are ordinary
function types and therefore *not* composable contexts. Hoisting 61 call sites was judged more
churn than the problem warranted.

**What replaced it.** A process-level holder:
- `ui/theme/NomiAnimationScale.kt` — added
  ```kotlin
  @Volatile internal var animationsDisabled: Boolean = false
  @Composable internal fun provideAnimationScale(content: @Composable () -> Unit) {
      val disabled = rememberNomiAnimationScale() <= 0f
      SideEffect { animationsDisabled = disabled }
      content()
  }
  ```
- `ui/theme/NomiMotion.kt` — the four specs are plain functions again, each returning
  `snap()` when `animationsDisabled` is true, else the original tween (durations unchanged:
  220/180/200/320 ms, same easings). **Zero call-site changes.**
- `ui/NomiApp.kt` — body wrapped in `provideAnimationScale { … }`.
- `WebsiteFavicon.kt` — when disabled, `initialValue == targetValue == 0.5f` under the existing
  `infiniteRepeatable`, so the band is drawn centred and static. (It **must** stay an
  `InfiniteRepeatableSpec<Float>`; passing `snap()` there does not compile because `animateFloat`
  requires the infinite type. A conditional around `rememberInfiniteTransition` was rejected for
  breaking composition slot structure.)
- `NomiNotesTodayScreen.kt` — `TypingDots()` returns early from its `LaunchedEffect(animate)`
  when disabled, so the dots rest instead of looping.
- Haptics were **not** changed (`NomiHaptics` relies on the platform's own setting).

### 3.6 Update checker (new feature)

**`app/src/main/java/com/nomi/app/update/UpdateCheck.kt`** — pure logic, no Android:
- `data class ReleaseVersion(major, minor, patch, preRelease) : Comparable` with
  `parse(raw)` accepting `2.3.1`, `v2.3.1`, `2.4.0-beta.2`, and short forms `2` / `2.3`;
  returns **null** on anything unparseable. Ordering is numeric per component, and a pre-release
  sorts below its bare version. This exists because `"2.10.0" < "2.9.9"` as a string.
- `sealed interface UpdateAvailability { UpToDate; Available(version, releaseUrl, summary) }`
- `UpdateCheck.decide(installed, latest, latestIsDraft, latestIsPreRelease)` — drafts never
  offered; pre-releases only to a pre-release build.
- `UpdateCheck.summarize(body)` — strips HTML comments, code fences, ATX headings, blockquotes,
  images/links, emphasis, and horizontal rules; collapses whitespace; truncates at 220 chars with
  an ellipsis.

**`.../update/GitHubReleaseSource.kt`** — `interface UpdateReleaseSource { suspend fun latest(): ReleaseSummary? }`,
`data class ReleaseSummary`, and `GitHubReleaseSource` hitting
`https://api.github.com/repos/david-x3d/nomi/releases/latest` with `expectSuccess = false`,
a `User-Agent`, `HttpTimeout` 4–6 s, **no auth**. Every `Exception` → `null`. A release whose
`htmlUrl` is absent or not `https://` is discarded. `const val DEFAULT_REPOSITORY = "david-x3d/nomi"`;
`val installedVersion = ReleaseVersion.parse(BuildConfig.VERSION_NAME)`.
`@Serializable private data class GitHubRelease` uses `@SerialName("tag_name")` / `("html_url")`.

**`AppViewModel`** — `update: StateFlow<UpdateAvailability>`, `dismissUpdate()`, and
`checkForUpdate(source = GitHubReleaseSource(), installed = installedVersion)` which is
**idempotent** via `updateCheckStarted`, runs in `viewModelScope`, and defaults to `UpToDate` on any
failure. A `private var updateCheckStarted` (not rememberSaveable) so it survives process death.

**`ui/update/UpdateAvailableDialog.kt`** — built on the existing `NomiDialog`
(`ui/components/NomiPopups.kt:89`) so it inherits Nomi's surface shape, hairline, pitch-black
handling and button hierarchy. Icon `Icons.Default.SystemUpdate`, title, `nomiFormat` subtitle
`"Nomi {0} is available."`, primary `View update`, secondary `Later`, and a bounded 180 dp
scroll area for the summary.

**`NomiRoot.kt`** — `LaunchedEffect(Unit) { withFrameNanos { }; viewModel.checkForUpdate() }` so the
check starts *after* the first frame and can never block startup. Renders the dialog from
`(updateAvailability as? UpdateAvailability.Available)`. Primary action calls `dismissUpdate()` then
`context.openReleasePage(url)` (private `Context` extension using `ACTION_VIEW` + `NEW_TASK`,
wrapped in `runCatching`; **never** substitutes the repo homepage).
Added imports: `Context`, `Intent`, `Uri`, `withFrameNanos`, `UpdateAvailableDialog`,
`UpdateAvailability`, `WeightUnitPreference`.

**Localisation** — `updateTranslations` appended to `ui/localization/TranslationsSettings.kt`
with all 4 strings × 9 languages; registered via `putAll(updateTranslations)` in
`NomiTranslations.kt`; `buildMap(680)` → `buildMap(700)`.

**Tests:** `app/src/test/java/com/nomi/app/update/UpdateCheckTest.kt` (20 tests) — includes every
required case (2.3.0→2.3.1 show, 2.3.1→2.3.1 no, 2.3.2→2.3.1 no, 2.9.9→2.10.0 show, draft ignored,
pre-release ignored for stable, malformed, network failure).

### 3.7 Other verified fixes

- **Food detail reachable for entries older than 30 days.** `NomiRoot`'s `Routes.FOOD` used to
  scan `todayState.entries + historyState.visibleDays` (a 30-day window) for the id, so tapping any
  food older than 30 days showed "This entry is no longer available." Replaced with
  `AppViewModel.foodDetail(id): Flow<TodayFoodEntry?>` which queries `repository.foodLog(id)` plus
  `repository.logsByEntryGroup(...)` (new `NomiRepository` function wrapping `logDao.logsByEntryGroupId`)
  and emits via `toGroupedTodayEntries()`.
- **Favourites.** `favoriteFoodLog` always built `FavoriteFoodEntity(id = 0, …)`, so a second
  favourite of the same food violated the UNIQUE index on `food_id` and the user was told
  "Save this food again before favoriting it" — the wrong cause. Now looks up
  `repository.favorites.first().firstOrNull { it.food.id == foodId }` and reuses its id and
  `createdAtEpochMillis`. Added **`unfavoriteFoodLog(id)`** (there was previously no un-favourite
  path at all).
- **Group-aware delete** — see §5.
- **`WidgetRefreshReceiver` `exported="false"`** (was exported with a custom action and no
  permission). The midnight `PendingIntent` and the widget providers use an explicit component, so
  they still work.
- **Open Food Facts**: added `UserAgent` ("Nomi/2.4.0 (Android; <repo url>)"), `HttpTimeout` 8 s,
  and `HttpRequestRetry { retryOnServerErrors(maxRetries = 2); exponentialDelay() }` — 5xx/429 only,
  never a 404-style "no such product".
- **`is24Hour`** now `android.text.format.DateFormat.is24HourFormat(LocalContext.current)`.
- **5 duplicate catalogue keys removed** (the later area silently won via `putAll`): from
  `TranslationsOnboarding.kt` — "Nutrition label", "Female equation", "Male equation",
  "Manual energy target"; and "Total" from `TranslationsFood.kt`.
- **New structural localisation tests** — `ui/localization/CatalogueCoverageTest.kt` (3 tests):
  every `nomiString`/`nomiFormat` key exists in the catalogue; no key declared twice; catalogue
  ≥ 670 entries. **This is a line-based scanner, deliberately** — a single regex with nested
  alternations overflowed the stack (`StackOverflowError`) on `NomiNotesTodayScreen.kt`, so
  possessive quantifiers alone were not enough.

---

## 4. Deliberately **NOT** wanted

> **Tombstones, a trash bin, and any form of sync are explicitly out of scope.** The owner has
> decided Nomi is single-device. Do not implement them, do not add the schema for them, do not add
> a "deleted_at" column, and do not reintroduce a sync design document.
>
> **DONE:** the owner asked for it to be removed, so `docs/sync-google-drive-design.md` (340 lines,
> "Status: plan only, nothing implemented") has been **deleted**. It was untracked, so nothing is
> recoverable from git and nothing was ever committed. Do not recreate it.

Consequence to be aware of, and to state honestly if it is ever raised: the Today-row delete undo
(`PendingDeletedLogStore`) is a plain in-memory `LinkedHashMap` inside the `AppViewModel`, so an OS
process kill during the undo window loses the row permanently. That was a sync prerequisite and
has been declined. Do not "fix" it with a schema change.

---

## 5. Grouped-meal delete — the fix, and why

Every product of a meal is its **own immutable `food_logs` row** sharing a plain `TEXT`
`entry_group_id` (no FK, no child table). The user thinks of the group as one thing, but deletion
had two inconsistent meanings:

| Entry point | Old behaviour |
|---|---|
| Today row → `deleteFoodLogForUndo` | deleted the **whole group**, offered undo |
| Today row "discard undo" → `deleteFoodLog` | deleted **one row** |
| Food **detail** screen → `deleteFoodLog` | deleted **one row**, **no undo** |

So opening one item of a 3-item dinner and tapping delete removed that item only: the remaining two
rows kept the group id, the meal total on Today became permanently wrong, and each survivor was
then deletable only as an orphan. The detail screen is the *more* deliberate delete surface, so this
was the worse place for it.

**Fix** — `AppViewModel.deleteFoodLog` is now group-aware, so all three paths agree:

```kotlin
fun deleteFoodLog(id: Long) {
    viewModelScope.launch {
        runCatching {
            val log = repository.foodLog(id) ?: return@runCatching
            val groupId = log.entryGroupId
            val group = if (groupId.isNullOrBlank()) listOf(log)
                        else repository.logsByEntryGroup(groupId).ifEmpty { listOf(log) }
            group.forEach { repository.deleteLog(it) }
        }.onFailure { mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't delete that food."))) }
    }
}
```

No schema change, no migration, no new table. Note `repository::logsByEntryGroup` **cannot** be used
as a method reference here — it is a `suspend fun`, so `entryGroupId?.let(repository::logsByEntryGroup)`
does not compile; the explicit `if` above is required.

`deleteFoodLogForUndo` (the Today row path) was already group-aware and is unchanged.

---

## 6. Current build / test status — VERIFIED GREEN

```
./gradlew :app:testDebugUnitTest --console=plain   ->  BUILD SUCCESSFUL
classes=72  tests=616  failures=0  errors=0  skipped=0
```

`./gradlew :app:compileDebugKotlin` also succeeds (it runs as part of the above). Re-verify with:

```bash
cd /home/david/Transfer/Nomi/repo
./gradlew :app:testDebugUnitTest --console=plain
python3 - <<'PY'
import glob,re
tot=fail=err=0; classes=0
for f in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'):
    s=open(f,encoding='utf-8').read(); classes+=1
    tot+=int(re.search(r'tests="(\d+)"',s).group(1))
    fail+=int(re.search(r'failures="(\d+)"',s).group(1))
    err+=int(re.search(r'errors="(\d+)"',s).group(1))
print("classes",classes,"tests",tot,"failures",fail,"errors",err)
PY
```

Counting history: **519 tests / 64 classes** was the v2.3.0 baseline (the CHANGELOG's claim, exact).
The audit-fix set added the rest, and the History work added `HistoryCopyRulesTest` (17) and
`HistoryModelsTest` (8).

### 6.1 The four failures that were real, and what each actually was

The previous handoff predicted these wrong in an instructive way. All four were **test** bugs, not
formatter bugs; the production behaviour was already correct and documented.

| Test | What it asserted | Reality |
|---|---|---|
| `a weight is displayed in the unit the reader uses` | `formatWeight(82.55, metric=true) == "82.6 kg"` | `82.55` is not representable as a double — it is `82.5499999…` — so HALF_UP correctly gives **`82.5`**. The handoff's claim that "82.6 ✓" assumed decimal semantics. |
| same test | `formatWeight(82.55, metric=false) == "182 lb"` | Pounds render at 2 dp. `82.5 kg` → **`181.88 lb`**. The handoff guessed `"182.02 lb"`; neither is what the formatter emits. |
| `a trailing zero is not shown` | `formatWeight(82.0, metric=true) == "82 kg"` | The formatter's documented design is a fixed 1 dp for metric, so **`82.0 kg`**. |
| `Imperial weight round-trips through the metric field` | tolerance `0.001` | Arithmetically impossible against a 1-dp display: rounding alone can be off by 0.047. Now `0.05`. |

**Decisions taken (deliberate, per the instruction not to weaken the formatter):**
- The formatter is **unchanged**. 1 dp metric / 2 dp imperial stays, because a scale reports 82.0 kg
  and trailing zeros are information, not noise.
- The **test expectations** were corrected to the real values, and a new test
  `rounding to the displayed decimal is half-up on the decimal value` pins the boundary using
  82.56/82.54 instead of the unrepresentable 82.55, with a comment saying why.
- `a trailing zero is not shown` was **renamed** to `a weight always shows the precision its unit
  is stored in`, because that is the property actually being asserted.

---

## 7. Outstanding before a release

Item 1 of the previous list (get the suite green) is **done**. The rest still stand:

1. ~~Get the test suite green.~~ **Done — see §6.**
2. ~~**Screenshot of the real update dialog.**~~ **DONE — see §12.**
3. ~~**Harden signing.**~~ **DONE.** `signingConfig` is now
   `signingConfigs.getByName("localRelease").also { require(it.storeFile?.exists() == true) { … } }`,
   so a missing keystore fails at configuration time with a message naming the file, instead of
   silently producing an unsigned release.
4. ~~**Bump version.**~~ **DONE** — `versionCode = 118`, `versionName = "2.4.0"`.
5. ~~**`lintVitalRelease assembleRelease` + `apksigner verify`.**~~ **DONE — see §13.**
6. Commit, push, `git tag v2.4.0`, push tag, `gh release create v2.4.0` with
   `--latest`, upload `Nomi-v2.4.0-release.apk`.
7. **Post-upload verification:** download the asset back with `gh release download`, compare
   SHA-256 byte-for-byte with the local APK, and re-verify the cert on the downloaded copy.

---

## 11. The `CatalogueCoverageTest` bug — the most important thing in this handoff

`CatalogueCoverageTest` was added by the audit-fix session and had **never run to completion even
once**. It contained two independent defects, one of which was an infinite loop.

1. **`nextCall` never returned `null`.** It returned `-1` when neither `nomiString(` nor
   `nomiFormat(` was found, which made the caller's `?: break` unreachable; `index` was then set to
   `at + 1 == 0` and the scan restarted from the top of the file, forever. The worker sat at 100%
   CPU for 16+ minutes in `callSiteKeys`. Fixed by adding the `a < 0 && b < 0 -> null` arm.
2. **`catalogueKeys()` could not see most of the catalogue.** It matched only lines ending in
   `to NomiTranslation(`. The catalogue actually uses **three** spellings:
   - `"key" to NomiTranslation(` — 662 entries
   - `"key" to` then `NomiTranslation(` on the next line — long keys that wrap
   - `put("key", NomiTranslation(` — the whole `updateTranslations` block

   It also stripped the trailing `+` *before* testing for it, so a multi-line key could never
   continue upwards. Net effect: it saw **0 of 686** keys.

Both are fixed. `catalogueKeys()` now anchors on each `NomiTranslation(` value and walks
**backwards** over the literal run, skipping the `to` / `(` / `,` connector before the first piece
and requiring a `+` between pieces. `no translation key is defined twice` uses the same scanner,
so duplicates are now detected in all three spellings.

**Two traps in a backwards literal scan**, both hit and both fixed:
- start the opening-quote search at `close - 1`, not `close` — otherwise it finds the closing quote
  itself and every literal reads as `""`;
- an escaped quote is met as the `"` first with its `\` *before* it, so test `text[start - 1]`.

`updateTranslations` was additionally rewritten to the dominant `"key" to NomiTranslation(` style
so the catalogue is uniform, with a comment saying why. The test now takes 0.07 s.

### 11.1 A real bug this found: `"Weight in lb"` was never translated

§3.4 claimed "Two new catalogue keys added: 'Weight in kg' and 'Weight in lb'". Only `Weight in kg`
was. `SupportingScreens.kt:89` calls `nomiString("Weight in lb")`, so **every non-English user saw
the Imperial weight label in English**. Added to `TranslationsCommon.kt` with all nine languages.
This is exactly the defect the coverage test exists to catch, and it caught it on its first
genuine execution.

Counts after the fix: **686 distinct catalogue keys, 0 duplicates, 0 call sites missing.** The
`>= 670` floor in the size test is satisfied for real.

---

## 8. Known risks, bugs and assumptions

- **`UnitFormatter.formatNumber` trailing-zero handling** was written defensively
  (`if (symbols.decimalSeparator == '.') formatted.trimEnd('.')`) because `DecimalFormatSymbols`
  exposes `decimalSeparator`, not `locale.decimalSeparator` (a `Char` on `Locale`) — using the
  latter does not compile. Worth a visual check in a comma locale. Note the formatter was
  deliberately **not** changed when §6.1 was resolved: 1 dp metric / 2 dp imperial is the intended
  design, and the test was wrong, not the formatter.
- **Backup data is not normalised for skew on write.** Only the validator was relaxed. An existing
  `updated < created` row therefore still round-trips unchanged. Harmless now; if a future sync
  ever needs LWW it would matter — and sync is out of scope.
- **Backup files are now gzip** despite the `.json` filename the SAF picker uses. Import sniffs the
  magic bytes, so it round-trips, but a user opening a backup in a text editor will see binary.
  Consider `.json.gz` in the suggested filename for a future release.
- **`AppViewModel.today` is a getter** returning `LocalDate.now(zoneId)`, so a value read across
  midnight can differ. Pre-existing; not addressed.
- **`AiProviderEditor.kt:184` and `NomiNotesTodayScreen.kt:1634`** are nested-call sites
  (`nomiFormat("{0}…", nomiString("…"))`). The `CatalogueCoverageTest` **call-site** scanner stops
  at the first non-literal so these are not misread as one long key. That scanner is correct and
  now provably runs; see §11 for what the *key* scanner got wrong.
- **The catalogue-key scanner in `CatalogueCoverageTest` is a backwards scan and has two traps**
  (off-by-one on the closing quote, and escaped quotes being met quote-first). Both were hit and
  fixed; if you rewrite it, re-derive them. §11 has the details.
- **Duplicate translation keys were removed, not merged.** The *losing* entry of each pair was
  deleted. Runtime behaviour is unchanged (the winner was already the one in effect), but the
  onboarding variants of "Nutrition label", "Female equation", "Male equation" and
  "Manual energy target" no longer exist in the catalogue. If a later change needs the onboarding
  wording specifically, it must be added back under a **distinct** key.
- **`TranslationsToday.kt:377/382` have an inverted `nl` value** in a currently-unused
  `'{0} left'` key (`nl = "{0} over"`). Pre-existing and masked because the key is dead; not fixed.
- **Not done, from the audit, deliberately:** `ProfileSettingsScreen` unit-awareness; the
  de/en-only vocabulary in `ui/format/QuantityDisplayFormatter.kt`; moving the error-message surface
  (`safeAiMessage` and friends, hard-coded English) onto the catalogue; widget locale following the
  in-app language; `mealAddingPage` dead flag; the orphaned `Routes.LABEL` / `Routes.BARCODE`
  destinations; `pruneAiDebugEvents` never being called; `TodayScreen.kt` (476 lines) and
  `FoodEntryDetailScreen` still being dead code.
- `third_party/whisper.cpp` is vendored (1,286 tracked files) with no upstream pin.
- Release is currently signed with the well-known Android **debug** keystore
  (`~/.android/debug.keystore`, password `android`), which is unbacked-up and machine-local. The
  owner has instructed that this be kept and **not** rotated. Do not "fix" it in this session.

---

## 9. Useful commands

```bash
export ANDROID_HOME=/home/david/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
cd /home/david/Transfer/Nomi/repo

./gradlew :app:testDebugUnitTest --console=plain          # JVM tests
./gradlew :app:compileDebugKotlin --console=plain          # fast main-source check
./gradlew :app:assembleDebug --console=plain               # for the emulator screenshot
./gradlew :app:lintVitalRelease :app:assembleRelease --console=plain
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 exec-out screencap -p > /tmp/shot.png

gh release list --limit 5
gh release download v2.4.0 -D /tmp/dl
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs <apk>
sha256sum <apk>

# fail-fast diff check: proves no CRLF churn is staged
git diff --ignore-cr-at-eol --stat
git status --short | grep -v third_party
```

Gotchas learned the hard way:
- `kotlinx.serialization` `Json` has no streaming encoder; `mark()` returns `Unit` in Kotlin.
- `Text.takeLast`/regex with nested alternations overflows the Java regex stack on large files.
- `git diff --numstat` shows symmetric insertions/deletions when the change is pure line endings.
- Tests locate the main source tree by walking up from `user.dir` looking for
  `app/src/main/java/com/nomi/app`; they fall back to `src/main/java/com/nomi/app`, so they work
  under Gradle and when run from the repo root.

---


## 10. HISTORY — DONE (wired, tested, suite green)

History is no longer unreachable. It is reachable from Today, its state and actions are connected,
its one genuine design gap is resolved, and it has 25 new tests. Nothing about it regressed
anything else: the suite is 616 / 616.

### 10.1 What was wrong

`HistoryScreen` (249 lines) existed with **zero callers**. `setHistoryQuery`, `setHistoryDate`,
`saveHistoryDayAsMeal` and `copyDayToToday` had **zero callers** each, and `NomiRepository.copyDay`
/ `copyMeal` were therefore dead. `Routes` had no `HISTORY` entry.

An earlier session had already started on `HistoryScreen.kt` / `HistoryModels.kt` and left a
`SaveDayAsMealDialog` plus new `HistoryDay` fields in the working tree. That work was reviewed,
kept, and finished rather than replaced — including its `defaultSavedMealName(…, prefix)` shape,
which is a better contract than a hard-coded English prefix, because the model layer cannot reach
the catalogue.

### 10.2 Route and navigation entry

- `Routes.HISTORY = "history"` added.
- `composable(Routes.HISTORY) { … }` added in `NomiRoot`, following the `PROFILE` / `PLAN` shape.
  It collects `viewModel.historyState` and passes `viewModel.currentDate`.

**Navigation entry — the decision §10.2 asked to be made deliberately.** `HistoryScreen` is a full
screen with its own `Scaffold` and `LargeFlexibleTopAppBar`, so it is a destination, not a tab.
Adding a 4th `MainDestination` would change the bottom navigation bar, which is a product decision
that was not delegated. History is therefore exposed from the **Today day-pager row** instead: an
`Icons.Default.History` button after the next-day arrow, labelled `nomiString("History")`.
`onOpenHistory` is threaded `NomiNotesTodayScreen` -> `NotesHeader` -> `MainNavigationSuite`. The
bottom bar, its `AnimatedContent` `contentKey = { it }`, and the `rememberSaveableStateHolder` are
all untouched.

A back arrow was added to the History app bar, matching `LibraryScreen`'s
`Icons.AutoMirrored.Filled.ArrowBack` + `nomiString("Back")`.

### 10.3 The `onCopyMeal` design gap — resolved

`HistoryScreen` declared `onCopyMeal: (HistoryDay) -> Unit` (a whole day) while
`NomiRepository.copyMeal` copies **one meal category**. Rather than guess which meal a day-wide
"Copy meal" meant, the contract was changed to carry the meal:

- `onCopyMeal: (HistoryDay, MealCategory) -> Unit`.
- `HistoryDay.mealCategories` (new, derived in `HistoryModels.kt`) returns the categories actually
  eaten, de-duplicated and in eating order. A day with breakfast and dinner shows two chips; a day
  with one meal shows one; **an empty day shows none**, so a chip that could not do anything is
  never rendered.
- `DayHeader` renders one chip per category, labelled `nomiFormat("Copy {0}", …)`, inside a
  `FlowRow` so four meals plus "Copy day" wrap instead of running off the edge on a long
  translation.
- New `AppViewModel.copyMealToToday(LocalDate, MealCategory)`.

The old day-wide `"Copy meal"` key is **removed** from the catalogue (superseded by `"Copy {0}"`).
New keys, all nine languages: `"Copy {0}"`, `"Save day as a meal"`, `"Meal name"`.

### 10.4 The name prompt the task required

`saveHistoryDayAsMeal` returns early on a blank name, so a silent default would look like a dead
chip. A `NomiDialog` + `NomiTextField` asks for it, **pre-filled** with
`defaultSavedMealName(day.date, locale, nomiString("Meal"))` so one tap accepts it. `confirmEnabled`
tracks non-blank, and `onConfirm` re-checks before firing.

`nomiString` is `@Composable` and `remember`'s calculation lambda is **not** a composable context,
so the read is hoisted:

```kotlin
val defaultName = defaultSavedMealName(day.date, locale, nomiString("Meal"))
var name by remember(day.date) { mutableStateOf(defaultName) }
```

### 10.5 Copy actions are hidden on today

`copyDayToToday` / `copyMealToToday` copy **into today**. On the day that *is* today that would just
duplicate the plate. Rather than change repository semantics, the copy chips are hidden for today
(`showCopyActions = !day.isSameDayAs(today)`), driven by the new `AppViewModel.currentDate` (the
pre-existing `today` was `private`). "Save meal" still shows, since it is not a copy.

### 10.6 Success feedback added

The three actions previously reported **only** failures, so a successful copy looked like nothing
happened. New keys, nine languages each: `"Nomi copied that day to today."`,
`"Nomi couldn't copy that meal."`, `"Nomi copied that {0} to today."`, `"Nomi saved that meal."`.
The meal name is looked up through a new private `AppViewModel.localizedMealName(MealCategory)` —
passing `MealCategory.displayName` would have leaked the English label into all nine languages.

### 10.7 The missing tests

`NomiRepository` cannot be constructed in a plain JVM unit test (it holds a Room database) and
Robolectric is not a dependency, and the task forbade adding one. So the interesting half of
`copyDay`, `copyMeal` and `saveLoggedMeal` was lifted into
**`data/repository/HistoryCopyRules.kt`** as `internal` top-level functions — the same trick
`externalWeightChanges(...)` already uses in that file — and both the DAO and the repository now
delegate to them. Behaviour is unchanged; it is now reachable from a test.

- `copiedDayLogs(...)` — the per-source-group id remap (the reason a copy does not weld itself onto
  the originals), the `single:<id>` fallback for ungrouped rows, timestamp/date/zone rewriting.
- `copiedMealLogs(...)` — one new group for the whole copied meal, category rewriting.
- `savedMealFromLogs(...)` — the eat-time-then-id ordering, the portion snapshot, the
  `normalizedName` fallback, and the refusals.
- `normalizeMealName(...)` — now the **single** definition; `NomiRepository.normalize` delegates to
  it so the search key of every name-shaped column cannot drift apart.

New tests: **`data/repository/HistoryCopyRulesTest.kt` (17)** and
**`ui/history/HistoryModelsTest.kt` (8)** — the latter covers `mealCategories` and
`defaultSavedMealName` across all ten shipping locales.

### 10.8 Known limitation, deliberately not fixed

`historyDate` is a `MutableStateFlow(today)` initialised when the ViewModel is constructed and is
never reset. If the app is left open across midnight, History opens on a window ending
**yesterday** until the user re-picks a date. It is benign and user-correctable, `setHistoryDate`
already clamps future input to today, and fixing it would mean re-deriving state the task told me
not to. Flagged rather than silently changed.

### 10.9 Not done

- `§7.2` the update-dialog screenshot, `§7.3` signing hardening, `§7.4-7.7` the v2.4.0 release.
- No commit was made. History is separable from the audit-fix set if you prefer two commits
  (the task's own item 10 suggested this).

---

## 12. The update-dialog screenshot — CAPTURED and INSPECTED

**These are real screenshots, captured from a real debug build on `emulator-5554` (API 34, x86_64,
1080x2400, density 420).** Committed as:

| File | What |
|---|---|
| `docs/screenshots/update-available.png` | the dialog in the light theme |
| `docs/screenshots/update-available-dark.png` | the same dialog with the system in dark mode |

### How the dialog was forced on

`app/src/debug/res/values/nomi_debug_overrides.xml` (new) overrides both resources to `true` and
`"99.0.0"`. The release values in `src/main/res/values/nomi_debug_overrides.xml` stay `false` and
`""`.

The read lives in `NomiRoot.rememberForcedUpdateAvailability()`, **not** in `AppViewModel`:
the ViewModel deliberately holds no `Context`, and giving it one for a debug affordance would be a
bad trade. `NomiRoot` already had a context via `LocalContext`.

The dialog is shown when either the forced value or the real `viewModel.update` is `Available`.

### Proof there is no backdoor in a shipped APK

Verified on the built release APK, not assumed:

```bash
aapt2 dump resources app-release.apk | grep -A2 nomi_debug_force_update_dialog
  resource 0x7f040002 bool/nomi_debug_force_update_dialog
    () false
aapt2 dump resources app-release.apk | grep -A2 nomi_debug_forced_update_version
  resource 0x7f0d0089 string/nomi_debug_forced_update_version
    () ""
unzip -p app-release.apk resources.arsc | strings | grep -q "99.0.0"   # -> not present
```

There is no preference, no intent extra and no `adb`-settable value that reaches the override,
because the release resource is a compile-time constant `false`.

### What the inspection found

Both themes were checked for clipping, typography, spacing, contrast and button hierarchy:

- Title `Nomi update available` and subtitle `Nomi 99.0.0 is available.` render without clipping
  at this width; neither wraps.
- The summary sits in its bounded 180 dp scroll area and wraps cleanly. The left-aligned body
  against the centred header is `NomiDialog`'s existing shape, not a regression.
- **Light:** dark text on the pale peach surface, filled orange `View update` against an outlined
  `Later` — correct hierarchy, the confirm action is the heaviest thing on the surface as intended.
- **Dark:** surface goes near-black and `hairlineOnPitchBlack()` gives the dialog a visible edge;
  the filled button inverts to light peach with dark text and keeps its contrast. Nothing is lost
  against the background.
- The `SystemUpdate` hero icon keeps its tonal circle in both themes.

No defects found, so nothing was changed in response to this inspection.

One thing the screenshot incidentally proves: the **History button added in §10.2 is visible** at
the right of the Today day-pager row, so that navigation entry is confirmed working on-device.

### Reaching the dialog on a fresh install

A clean install lands on onboarding, and `AppStartState.Main` requires
`profile.onboardingCompleted == true`, so the dialog is unreachable until onboarding is done. To
avoid driving 10 screens of taps, the profile row was inserted directly — the debug build is
debuggable, so `run-as` works:

```bash
adb -s emulator-5554 shell am force-stop com.nomi.app
adb -s emulator-5554 shell "run-as com.nomi.app sqlite3 /data/data/com.nomi.app/databases/nomi.db \
  \"INSERT INTO user_profiles (id,date_of_birth,energy_calculation_sex,height_cm,starting_weight_kg,\
   goal_type,target_weight_kg,activity_level,progression_rate,onboarding_completed,\
   created_at_epoch_millis,updated_at_epoch_millis) \
   VALUES (1,'1990-05-14','MALE',180.0,82.5,'LOSE',78.0,'MODERATE','STANDARD',1,$NOW,$NOW);\""
```

Also note: the emulator had a Nomi build signed with a **different** key
(`09e54c98e44e300bff311b17bf6285494a0ec21f318687ef639e64f4d163517d`), so
`adb install -r` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the app had to be
**uninstalled** first. That destroyed whatever food log the AVD had, which is irrelevant on a demo
emulator but worth knowing before repeating this.

---

## 13. Release build — BUILT and VERIFIED

```bash
./gradlew :app:lintVitalRelease :app:assembleRelease     # BUILD SUCCESSFUL
```

| Check | Result |
|---|---|
| `app/build/outputs/apk/release/app-release.apk` | 47,206,969 bytes |
| `apksigner verify` — signed? | **yes**, `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug` |
| Certificate SHA-256 | `9344cd48…67ee8bf` — **matches the published v2.3.0 exactly** |
| `versionCode` / `versionName` | `118` / `2.4.0` (was 117 / 2.3.0) |
| Debug override in release APK | `false` / `""`, and no `99.0.0` anywhere — **no backdoor** |

`lintVitalRelease` passed, so no fatal-issue lint blocked the build.

Note the hardened signing config works: the release APK is genuinely signed rather than silently
unsigned, which is the whole point of §7.3.

Still outstanding: §7.6 (commit, push, tag, GitHub Release) and §7.7 (post-upload verification).

---

## 14. v2.4.0 — RELEASED

https://github.com/david-x3d/nomi/releases/tag/v2.4.0

```bash
git push origin main          # f4be7fa..6b936c9
git tag -a v2.4.0 -m "Nomi v2.4.0" && git push origin v2.4.0
gh release create v2.4.0 Nomi-v2.4.0-release.apk --title "Nomi v2.4.0" --notes-file … --latest
```

One commit, `6b936c9`, carrying both the audit fixes and the History work.

### Post-upload verification (§7.7) — all passed

| Check | Result |
|---|---|
| Asset on the release | `Nomi-v2.4.0-release.apk`, 47,206,969 bytes |
| Release flags | `draft: false`, `prerelease: false`, marked **Latest** |
| Downloaded back with `gh release download` | **byte-for-byte identical** (`cmp` clean) |
| SHA-256, local vs downloaded | `102df3b2f864c8c870b06b56a5d22d60684d23b3d6d7d976a3370035539d987d` — same |
| Certificate on the **downloaded** copy | `9344cd48…67ee8bf` — matches published v2.3.0 |
| Signature scheme | v2 (v1/v3/v3.1/v4 off) |
| `dumpsys package` after install | `versionCode=118`, `versionName=2.4.0` |
| The published APK installs | `Success` on `emulator-5554` |

### The update checker verified against the live API

The one part of the dialog that cannot be screenshotted is its summary, because it comes from
GitHub. So the real endpoint was checked directly:

```
GET https://api.github.com/repos/david-x3d/nomi/releases/latest
  tag_name  = "v2.4.0"          html_url = "https://github.com/…/tag/v2.4.0"
  draft     = false             prerelease = false        body = present
```

Every field `@SerialName` in `GitHubRelease` is present in the live response, `html_url` is
`https://`, and the comparison of installed `2.4.0` against latest `2.4.0` yields **UpToDate**, so
no dialog — which is the correct behaviour for a user already on this build. Running the real
release body through `UpdateCheck.summarize`'s rules yields 221 characters of clean text with the
ellipsis truncation working, so the dialog's scroll area has realistic content to show.

Note `run-as` does **not** work on the release build (`package not debuggable`), which is correct
and is why the profile could only be seeded on the debug build in §12.

### Everything in §7 is now closed

1. Test suite green — **616 tests, 72 classes, 0 failures**
2. Update-dialog screenshot — **captured, both themes, committed**
3. Signing hardened — **a missing key fails the build**
4. Version bumped — **118 / 2.4.0**
5. `lintVitalRelease` + `assembleRelease` + `apksigner` — **all passed**
6. Committed, pushed, tagged, released — **done**
7. Post-upload verification — **done**

There is no outstanding release work. The only things deliberately left undone are the ones §3.8
and §4 list as out of scope, plus the two limitations named in §6.1 and §10.8.
