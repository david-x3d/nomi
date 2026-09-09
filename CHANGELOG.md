# Changelog

## Nomi v2.2.0 — 2026-09-09

### Photo weight estimates

- Uses visible plate size, bowls, cutlery, food height, coverage and perspective to estimate total edible food weight.
- Makes estimated grams editable in photo review and retains visible piece counts as context.
- Shows the AI's scale assumptions and uncertainty in both photo-review interfaces, with a translated estimate notice in all ten languages.
- Keeps photo-derived portion notes with researched nutrition and preserves drink volumes separately from mass.
- Adds regression coverage for portion scaling, user corrections, piece totals, unknown weights, volume units and compatible vision responses.

### Verification

- All 481 unit tests passed across 61 classes, with no failures, errors or skips.
- Release compilation, lint-vital checks and APK assembly passed.
- The APK signature matches the previous stable release (v2.1.3).
- Photo recognition accuracy was not benchmarked against weighed meals; results remain estimates.

SHA-256: `C090295E48B8DE5BD46E95A134428D8E034D933916A2B7C5E69B66434872F44E`

## Nomi v2.1.3 — 2026-08-30

### Remove Gemini and Assistant logging

- Drops App Functions, deep links, shared-text logging and spoken calorie replies. Gemini cannot call third-party apps from the Connected Apps list, so that path is gone.
- Keeps the launcher long-press shortcuts: photograph a meal, or scan a restaurant menu.

### Verification

- The full Gradle unit test suite ran and passed: 473 tests across 60 classes, with no failures, errors or skips.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `DE135F8E5EB20D6D8AB6D43018A17690014ECA78AFBF79DDC9930604E30AF7FD`

## Nomi v2.1.2 — 2026-08-30

### Gemini can log food in Nomi instead of Google Keep

- Exposes Nomi to Gemini through Android App Functions (`logFood` and `remainingCalories`), so “add a banana in Nomi” is handled by Nomi rather than Keep/Notes.
- Gemini receives the calories left today as the function result after the food is researched and saved.
- Requires Android 16 or newer for Gemini to call the functions. Older versions keep the existing deep links and launcher shortcuts.

### Verification

- The full Gradle unit test suite ran and passed: 487 tests across 63 classes, with no failures, errors or skips.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `51BD4FCA4C0CFBA5F1E94AA5D041A7E750A18E2EE7837DA44EE270447154A85E`

## Nomi v2.1.1 — 2026-08-30

### Log food from Assistant and long-press shortcuts

- Accepts “add a banana in Nomi” (and the same idea in German and the other interface languages) through Assistant App Actions, a `nomi://log` link, shared text, or a Google search action, then runs the existing food-logging pipeline.
- After a voice-originated log saves, Nomi speaks and shows how many calories remain today, or how far over the target the day is.
- Long-pressing the launcher icon offers two shortcuts: photograph a meal, or scan a restaurant menu.
- “How many calories left in Nomi” reads today’s remaining calories without logging anything.

### Verification

- The full Gradle unit test suite ran and passed: 487 tests across 63 classes, with no failures, errors or skips.
- Adds tests covering spoken meal commands, Assistant and deep-link parsing, and remaining-calorie wording.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `A28651F507C7C8B8AB70B3BBC371EAEDE125FF5FE2D1DDF84B3FE64E0A4B8F5F`

## Nomi v2.1 — 2026-08-30

### Export each day's foods, calories, protein and carbs as JSON

- Adds **Settings → Your data → Export diary**, which writes a pretty-printed JSON file of every logged day.
- Each day lists the date, the foods eaten that day with name, brand, amount, unit and meal, and that day's calorie, protein and carbohydrate totals.
- Each food row carries its own kcal, protein and carbohydrate so the day totals can be checked against the items.
- Days with no logs are omitted. Days are ordered by date; foods keep the order they were logged.
- The diary is a readable export, not a restoreable backup: it does not contain API keys, profile data, plans, weights or catalog rows. The existing backup export is unchanged.

### Verification

- The full Gradle unit test suite ran and passed: 473 tests across 60 classes, with no failures, errors or skips.
- Adds three tests covering an empty diary, grouping and summing foods across two days, and a JSON round-trip of the envelope.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `33B30B41EA79393EBBA392A9FFA66E011911745B17A0BC8555001EE0FBFD60D2`

## Nomi v2.0.9 — 2026-08-28

### One failed food no longer fails the whole meal, and says why

- Resolves each logged item independently through grounding, quantity reconciliation, normalization and source verification, instead of aborting the entire analysis at the first item that could not be grounded.
- Retries only the items that failed, once, with their own retrieval and a prompt that names what the first pass was missing; items that already resolved keep their first-pass reading.
- Adds a typed failure cause carried with the failure: no suitable source, source/product identity mismatch, unsupported nutrition values, invalid nutrition basis, missing portion weight, parsing failure, provider timeout, rate limit, unavailable model, and unreachable provider.
- Reports the failing food by name with a cause-specific sentence, translated into every supported language, in place of the single "Nomi couldn't verify nutrition for every product" message.
- Records the typed cause and the per-item detail in the AI debug log, so one bad item in a meal is diagnosable.

### Ordinary foods that could not be logged

- Stops requiring two words of the requested food name to appear in one retrieved document. A preparation word such as "gekocht" or "gebraten" is frequently absent from the nutrition page for the same food, which rejected the request outright and blocked the generic fallback with it. A majority of the request's words is now enough, while the claimed product title, the exact calorie and macro figures, and any requested brand must still match.
- Decides branded versus generic from the request and from any package size the reading claims, not from the research model's brand field. A model writing a word such as "Generic" into that field no longer blocks the generic path, and its unverified brand and package claims are dropped rather than published.
- Accepts a generic reading on a printed per-serving basis as well as per 100 g and per 100 ml, provided the reading is one the deterministic normalizer can scale.
- Refuses any generic reading whose own basis text contradicts the basis it declared, so a per-100 table can never be republished as a whole-portion total through the estimate path.
- Asks research for the weight of a counted portion, or for a source whose own basis is per item, when the logged unit is a count and the basis is per 100 g or per 100 ml.

### Clearer provider and estimate reporting

- Distinguishes a retried rate limit from an outage, which previously shared one "temporarily unavailable" sentence.
- Reads an explicitly labelled estimate as ESTIMATED rather than UNKNOWN. UNKNOWN now means a reading that claims to be verified and cites nothing.
- Splits an incompatible serving into two causes: a counted logged amount with no weight, which entering grams settles, and a source basis that cannot be converted into a real mass or volume.
- Extracts the catalogue upgrade rule into one tested decision: verified data replaces a stored estimate, a fresh estimate never overwrites anything, and a food the user created is never rewritten by research.

### Verification

- The full Gradle unit test suite ran and passed: 470 tests across 59 classes, with no failures, errors or skips.
- Adds 27 regression tests covering a generic single food at arbitrary gram amounts, a generic multi-item meal, a branded product with valid manufacturer evidence, a branded product with mismatching evidence, one failed item among several valid ones with its narrowed retry, search and extraction timeouts, per-100 scaling at 1 g, 7.5 g, 42 g, 137 g, 250 g and 999 g, per-100-ml scaling that never becomes grams, and the cache rules that keep a stale estimate from outliving verified product data.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `BADDA6D489C0A54F479708014E69FAB6FE38915BC8EF38F88BB8AD9C9309E63C`

## Nomi v2.0.8 — 2026-08-28

### Generic foods log again at any amount

- Restores a research path for foods with no brand, package or barcode identity, which v2.0.7 removed when it gated the last estimate fallback behind a whole-serving basis equal to the logged amount.
- A generic food logged at any amount other than exactly 100 g failed outright, because at 100 g the per-100 table is the answer and grounding succeeds without a fallback.
- Accepts a generic reading as an explicit estimate only when it claims no brand and no source package size, declares a genuine self-contained per-100 basis, and at least one retrieved document is a nutrition page about the food that was requested.
- Keeps the researched per-100 basis untouched on that path so the deterministic normalizer scales it to the logged amount; it is never rewritten into a whole-portion total.
- Drops the citation and product name for an ungrounded generic result and marks it an estimate, so nothing unverified is presented as verified.
- Leaves branded, packaged and barcode products under the existing rule that one source must support the complete reading.
- Adds the matching generic-food instruction to the research prompt, which previously only asked for an exact product identity.

### Verification

- The full Gradle unit test suite ran and passed: 443 tests across 56 classes.
- Adds eight regression scenarios covering generic foods at arbitrary gram amounts, per-100 basis preservation, source citation, physical plausibility, and continued rejection of unsupported branded and package claims.
- Scaling is pinned across 1 g, 37 g, 99 g, 100 g, 276 g, 501.5 g and 1234 g.
- Release compilation, lint-vital analysis, signing and APK assembly passed.
- APK SHA-256: `8F7E7B8D674D49891B36257C82175E17E43DE075B46C5D47B014674644A9FF32`

## Nomi v2.0.7 — 2026-08-28

### Reliable nutrition quantity scaling

- Makes the quantity entered by the user authoritative throughout parsing, research, normalization, persistence and display.
- Keeps per-100-g, per-100-ml and per-serving nutrition bases distinct, scaling calories, macros and optional nutrients through one shared normalization path.
- Removes food-, brand- and quantity-specific fallbacks, including implicit mass/volume conversions that lacked an explicit serving equivalence.
- Prioritizes grounded manufacturer or package nutrition over unrelated generic estimates and prevents evidence from separate documents from being combined into a verified result.
- Refreshes estimated barcode cache entries when product-specific data is available and records verification metadata with persisted nutrition sources.
- Invalidates cached research from the previous normalization contract.

### Verification

- All 100 focused regression tests passed across normalization, quantity resolution, prompting and both research-provider paths.
- Coverage includes different quantities, mass and volume units, serving counts, all nutrition fields, ambiguous unit bridges and manufacturer-versus-generic evidence.
- Release compilation, lint-vital analysis, signing and APK assembly passed. The full Gradle test task was not run for this release. The reason recorded here originally, that this host blocks the loopback sockets used by Gradle test workers, was a misdiagnosis: the host's default temp directory rejects the AF_UNIX socket the JDK opens for NIO selector pipes. Pointing `jdk.net.unixdomain.tmpdir` at a directory that accepts it lets the task run normally, for the daemon and for forked test workers alike.
- APK SHA-256: `F59FFF43748CB4C143700DB6935A5ED5E6BB6B7BB8A6C49E48A21A3EA082C5C4`

## Nomi v2.0.6 — 2026-08-26

### Home-screen widgets

- Adds two home-screen widgets: a 2x2 showing today's calories against the target, and a 4x2 adding protein, carbohydrate and fat progress.
- Reads today's totals and the active nutrition plan from the local database; both widgets open Nomi when tapped and show a setup hint until onboarding finishes.
- Refreshes on data changes while the app runs, on the system widget update broadcast, on an inexact midnight alarm, and on date, time-zone, boot and package-replaced broadcasts. updatePeriodMillis stays 0 so the OS never wakes the process to redraw unchanged numbers.
- Skips all work when no plan exists and no widget is placed, arming only the midnight rollover.
- Localizes every widget string across all nine supported languages, and names the two widgets separately in the picker instead of listing "Nomi" twice.

### Widget layout corrections

- Fixes the calorie figure being cut off along its lower edge: autosizing text inside a wrap_content height measures the view for the previous text size, so grouped numbers such as "1,240" lost their descenders. Both hero numerals now use a fixed height.
- Fixes the fat bar missing from the 4x2 widget: stacked macro rows needed roughly 190dp while a 4x2 cell offers about 140dp. Protein, carbohydrates and fat now sit in three side-by-side columns that fit, and the content is centred vertically.
- Applies Material 3 Expressive weighting: a heavier hero numeral, a larger 28dp surface radius, and thicker fully rounded progress bars.

### Verification

- All 437 unit tests, Android lint, the release build, APK version, and APK v2 signature verification passed.
- Both widgets were placed and confirmed on a Pixel 10 Pro XL emulator running API 37.1.
- APK SHA-256: `AECD5481D8D434ED5627EA0AEB851ECA65E74C5AE0641AD17F77B0923AA31307`

## Nomi v2.0.5 — 2026-08-25

### Correct nutrition totals for every logged portion

- Scales per-100-g and per-100-ml research values to the user's authoritative logged amount across calories, macros, and optional nutrients.
- Separates the research basis from the requested portion so provider responses cannot silently label per-100 values as full-portion totals.
- Grounds the Exa/Gemini basis against exact text from the selected source and rejects contradictory basis classifications instead of reusing them as estimates.
- Preserves values that a source genuinely publishes for the complete serving, preventing double scaling.
- Invalidates older persistent research-cache entries from the previous basis contract and deterministically rescales reusable per-100 food data for new portions.
- Adds developer diagnostics for requested amount, research basis, raw values, normalized per-100 values, factor, and final stored portion values, including cache hits.
- Adds regressions for 50 g, 100 g, 400 g, complete-portion values, cached per-100 data, provider response variants, micronutrients, and basis contradictions.

### Verification

- All 430 unit tests, Android lint, the release build, APK version, and APK v2 signature verification passed.
- APK SHA-256: `6036A779DAD5A8899DFA630BD90D364D3C6A5EED0C704947C5838E20F7B76E7E`

## Nomi v2.0.4 — 2026-08-20

### Health Connect sync restored and backfilled

- Syncs every granted Health Connect category independently, so a missing nutrition or weight permission no longer blocks steps and weight reads.
- Starts Health Connect reads only while Nomi is in the foreground and coalesces overlapping refresh requests instead of dropping them.
- Imports all accessible weight history with paginated reads and requests extended-history access on supported providers.
- Retries older local and onboarding weights with stable record IDs, and imports corrections to existing Health Connect weights idempotently.
- Backfills the complete food journal, safely repairs missing remote nutrition records, and retries interrupted rewrites without losing deletion state.
- Preserves valid same-day activity after a transient provider error without showing yesterday's values after midnight.
- Keeps Sync now available with partial access and lets users request missing optional permissions later.

### Verification

- Unit tests, Android lint, the release build, APK version, and APK v2 signature verification passed.
- APK SHA-256: `6DB7941C405944177BE24E42E0572BAC10C7255C17723BB0CB608EE6C00B859B`

## Nomi v2.0.3 — 2026-08-16

### The burned calories are no longer cut off

- Writes "kcal" once for the pair in the Today action bar instead of after each number, so the burned figure fits beside the eaten one on a normal phone screen.
- Drops the approximation sign from the walking estimate in the bar and sets both numbers in the same type and colour, so eaten and burned read as one pair.
- Lets the pair wrap onto a second line on narrow screens or at large font sizes, so the burned calories move instead of being clipped to an ellipsis.
- Keeps "kcal" on the eaten number when there is no activity figure beside it.
- Gives both icons the same size and one shared centre line.
- Leaves the goals sheet and the Health Connect screen unchanged, where the estimate keeps its "≈" and its "from steps" wording.
- Names the eaten figure for screen readers in all ten supported interface languages.

## Nomi v2.0.2 — 2026-08-16

### Polished Material UI and personal step calories

- Includes the complete Beta 1 UI refresh: the warmer Material palette, clearer typography, crisper cards, quieter outlines and smoother motion remain intact.
- Estimates active walking calories from today's steps, the latest logged weight and the saved height when available.
- Uses the newest weight entry first and falls back to the onboarding weight, so the estimate follows the user's current profile without changing stored goals.
- Shows the estimate as an approximate value in the existing Today calorie pill, both goal-card styles and the Health Connect detail screen.
- Keeps Health Connect total active calories separate and never adds the two figures together or changes calories left for the day.
- Preserves a missing Health Connect calorie record as missing instead of silently treating it as a real zero.
- Keeps the calculation local and labels the estimate across all ten supported interface languages.

### Accuracy note

- This release combines the visual refresh with the new step-calorie estimate. Walking pace, incline, running, terrain and step-count accuracy can make actual energy use differ.

### Verification

- The signed stable APK assembled successfully and its APK v2 signature was verified.
- APK SHA-256: `8D9A9AC053FDF2002FB04ACA7F9C409FDADEE97C6AFECF6E1F90F674A3C4784B`

## Nomi v2.0.2 Beta 1 — 2026-08-16

### A calmer, more cohesive Material finish

- Refined Nomi's fallback palette around warm fox orange, soft sage and muted gold while preserving Android dynamic color.
- Strengthened the Material type hierarchy without replacing Android's system font or changing screen layouts.
- Made cards crisp and opaque across light, dark and pitch-black themes, with quieter outlines and one shared surface treatment.
- Added subtle destination tones to Progress, Settings and the food library while keeping every control in its familiar place.
- Replaced bouncy press and navigation springs with the active standard Material motion scheme and added directional date movement.
- Smoothed the complete input → research → preview flow on Today and tied its completion shimmer to the active Material palette.
- Polished the weight chart with a restrained animated area fill and a clearer dashed goal line.
- Made food-library categories visibly selectable and horizontally scrollable for long translations.
- Kept status- and navigation-bar icon contrast in sync with Nomi's own Light, Dark and System setting.
- Corrected picker error colors and preserved crisp field boundaries on true-black OLED themes.

### Beta note

- This prerelease is a visual and interaction preview. It does not migrate or change saved nutrition data, AI providers or research behaviour.

### Verification

- 397 unit tests passed with no failures, errors or skipped tests.
- Android lint and the signed prerelease build passed.
- APK SHA-256: `4EE7E47FD1E6219217318E36F2F7AE0E30E51E162FE527EE968FF922F9F8C153`

## Nomi v2.0.1 — 2026-08-15

### Rewrite complete grouped meals

- Tapping the name of a grouped meal now opens it for inline editing, just like a single food; calories still open nutrition details.
- Nomi stores the exact typed or dictated sentence behind new entries, so a meal such as `Protein Wrap, Thunfisch, Mozzarella und Tomate, Frito` reopens with those words intact.
- Older grouped meals remain editable by rebuilding a sentence from every stored item and amount.
- Saving a rewritten group replaces all of its previous items, preventing stale ingredients from remaining in the day.
- The original meal category is preserved while rewriting.
- Backups now carry the original input, and database migration 5→6 preserves all existing history.

### Verification

- 397 unit tests passed with no failures, errors or skipped tests.
- Android lint and the signed stable build passed.
- APK SHA-256: `895E7E6A7E5B4A4B20DF88D7211BE0B38174801263F7EE0854D3D44FA31E9588`

## Nomi v2.0 — 2026-08-15

### A more responsive, polished Nomi

- Promoted the complete Nomi 1.9 beta experience to the stable 2.0 release.
- Refreshed Today with a soft dynamic-colour header, a subtle fox halo and an elevated floating action dock.
- Food quick actions now float beside the entry that was held instead of against the screen edge.
- Added meaningful haptic feedback and press animations across the app's most important interactions.
- Multi-item inputs name the actual researched foods: `250g tenderloin 120g pommes und ein red bull` appears as `Tenderloin mit Pommes und Red Bull`.
- Corrected item names, localized conjunctions and punctuation make grouped meals easier to read while preserving individual amounts and nutrition details.

### Verification

- 395 unit tests passed with no failures, errors or skipped tests.
- Android lint and the signed stable build passed.
- APK SHA-256: `D5FBC66B31B8D22FFB093104105FEB60D8AE5A1BBA40B7951410E3D9EBD5E9AE`

## Nomi v1.9 Beta 2 — 2026-08-15

### Meal titles say what you logged

- Multi-item inputs now keep the actual researched foods in the Today title instead of collapsing them into a generic menu name.
- For example, `250g tenderloin 120g pommes und ein red bull` appears as `Tenderloin mit Pommes und Red Bull`.
- Item names use the provider's corrected spelling and are joined with localized words and punctuation.
- Amounts, nutrition and sources remain attached to each individual item in the meal details.
- The visual and interaction refinements from Beta 1 remain included.

### Verification

- 395 unit tests passed with no failures, errors or skipped tests.
- Android lint and the signed prerelease build passed.
- APK SHA-256: `69641F93F12B33DC25902A792B8F0567E51469DFA95A96D618A5332BCA480DD4`

## Nomi v1.9 Beta 1 — 2026-08-15

### A calmer, more distinct Today page

- Refreshed the Today header with a soft dynamic-colour wash that follows Nomi's current state without judging the day.
- Gave the Nomi fox a subtle matching halo and strengthened the wordmark while keeping the header compact.
- Turned the bottom actions into one elevated floating dock, preserving the familiar calorie, voice, camera and library controls.
- Moved food quick actions away from the screen's left edge: the rounded menu now floats beside the entry that was held.
- Kept the existing notes-first layout, gestures, source states, dynamic colour, dark theme and pitch-black adaptation intact.

### Beta note

- This prerelease is intentionally a visual and interaction preview. It does not change saved nutrition data or provider behaviour.

### Verification

- 393 unit tests, Android lint and the signed prerelease build passed.
- APK SHA-256: `CDA2E2E5B1C0B10A52B86782A3F43DEDA246B88871DD930DDA63C506862430F7`

## Nomi v1.8 — 2026-08-15

### Nomi feels more responsive

- Added meaningful haptic feedback across navigation, date changes, settings, capture actions, submitting, saving, errors, swipe-to-delete, Undo and quick actions.
- Frequently used circular actions now press inward and spring back, while the selected destination icon responds with a small expressive lift.
- Barcode recognition now confirms itself with haptic feedback and a green scan frame that remains visible briefly before the amount sheet opens.
- Holding a food on Today opens quick actions to duplicate it, change its amount, save it as a favorite or delete it.
- Food analysis now shows the current step — understanding the meal, finding nutrition, checking portions or putting the result together — with live progress and source icons.
- Compact Today actions now explain themselves with tooltips.
- Empty days now offer a friendly, concrete logging example in every supported language.

### Verification

- 393 unit tests passed with no failures, errors or skipped tests.
- Android lint and the signed release build passed.
- APK SHA-256: `D619B6A40750C3FD7606433DBB5808E27A3CE5B66C324EA9940A83F8CAE9A179`

## Nomi v1.7.1 — 2026-08-13

### "Complete permissions" works again

- The button asked Health Connect only for the four categories Nomi wanted before v1.7, all of which were already granted — so Health Connect returned at once and nothing appeared to happen, while the missing nutrition permission kept the connection incomplete.
- The request now always asks for exactly the categories the connection status is judged against, so the two cannot drift apart again.

## Nomi v1.7 — 2026-08-13

### Your food reaches Health Connect

- Nomi now writes what you eat to Health Connect: the calories, protein, carbohydrates and fat of every logged portion.
- Fibre, sugar, saturated fat and sodium travel with the entry whenever the food reports them.
- Each entry keeps its name, its brand and its meal, so breakfast arrives as breakfast.
- Correcting a portion updates the matching Health Connect entry, and deleting food removes it.
- The last 30 days are covered, which is the same window Nomi already reads weights from.
- Only what changed is sent, so opening Nomi with nothing new logged asks Health Connect for nothing.
- Entries keep the time zone they were logged in, so a travel day reads the same in both apps.
- The Health Connect page counts the food entries Nomi is currently sharing.

### Note

- Health Connect asks for one new permission, "write nutrition". Until you approve it, Nomi reports the connection as incomplete and shares nothing new.

## Nomi v1.6.1 — 2026-08-12

### The keyboard steps aside for the camera

- Opening the camera now puts the keyboard away instead of leaving it in front of the viewfinder.
- This covers every way in: the Today action bar, the barcode scanner, the nutrition-label shot, and "Add another page" while searching a scanned menu.
- Typed text is kept — only the caret and the keyboard go, and the entry waits below the camera.
- After the camera closes, the page stays with the shot instead of jumping back to where writing left off.

## Nomi v1.6 — 2026-08-12

### Calories burned

- The Today action bar now shows the calories movement burned today beside the calories eaten.
- The Goals sheet gained a second bar for burned calories, in both the ring and the bar layout.
- The burned bar shares the calorie target as its scale, so equal lengths mean equal calories.
- Steps are shown underneath the burned figure.
- Nothing is subtracted from the day's intake; the eaten figure stays the eaten figure.
- Burned calories and steps appear only on today, and only once Health Connect has reported them.

### Drawn icons

- Added Nomi's own flame and running-figure icons, replacing the stock ones.
- Both are single-colour shapes that take their colour from the active scheme, including dynamic colour.

### Fixes

- The barcode scanner's "Scanning…" label no longer renders as mojibake and is translated again.
- The calorie estimate bias slider now saves the stop it was released on instead of the previous one.

## Nomi v1.5.3 — 2026-08-12

- Shortened the swipe-to-delete Undo window from three seconds to two seconds.
- Kept the red inline Undo row exclusively for right-to-left swipe deletion.
- Clearing all text from an opened food entry now deletes it immediately without showing Undo.

## Nomi v1.5.2 — 2026-08-12

- Removed the send button from typed food logging and its keyboard action.
- Moved the animated typing dots into the food line, where the finished entry shows its calories.
- Kept the calorie summary and add-method action bar stable while typing.
- Food research still starts automatically after 1.5 seconds without further input.

## Nomi v1.5.1 — 2026-08-12

- Food descriptions now start researching automatically after 1.5 seconds without further typing.
- Added a three-dot typing animation in place of the calorie total while composing.
- Logged entries can now be deleted like a line in a notes app by opening and clearing their text.
- Kept swipe-to-delete and its inline Undo flow alongside keyboard deletion.
- Added more tactile feedback when opening, editing, submitting, and deleting entries or choosing capture actions.

## Nomi v1.5 — 2026-08-12

- Added a compact Today action bar with calories consumed, microphone, camera, and add actions.
- Added small animated Material menus for photo capture, barcode scanning, menu scanning, gallery import, recent foods, favorites, and saved meals.
- Photo, nutrition-label, barcode, and restaurant-menu capture now open inline instead of taking over the entire app.
- Redesigned scanned-menu results with a centered action bar, search, grouped dishes, compact selection cards, and a live selected-item count.
- Reworked page, date, onboarding, and progress transitions with shorter non-bouncy motion for smoother navigation.
- Preserved destination state while switching between Today, Progress, and Settings.

## Nomi 1.4.5 — 2026-08-12

- The goals sheet header and content now use one continuous Material You background color.
- Removed the remaining grey outlines and elevation frames from goal and Settings cards.
- Completed localization of onboarding, plan results, provider credentials, backups, and app messages in all ten supported languages.
- Added UTF-8 safeguards so umlauts and accented language names render correctly without mojibake.

## Nomi 1.4.4 — 2026-08-12

- Removed the grey outlines and shadows around bar-style goal cards.
- Goal cards now use clean, flat grey-white surfaces without a surrounding frame.

## Nomi 1.4.3 — 2026-08-12

- The Settings header and content now share one consistent Material You background color.
- Bar-style goal cards now use uniform opaque grey-white Nomi surfaces.
- Removed the contrasting inner-panel effect from calorie, macro, and micronutrient goal cards.

## Nomi 1.4.2 — 2026-08-12

- Meal and item detail cards now respect the micronutrient tracking preferences.
- Disabled micronutrients, such as sodium, are no longer shown in nutrition grids.
- Macronutrients remain visible regardless of micronutrient tracking choices.

## Nomi 1.4.1 — 2026-08-12

- Grouped meal items now expand directly inside the meal details page.
- Each item shows its calories, portion, macros, and available micronutrients in a compact Nomi-style card.
- Opening an item no longer navigates to a separate detail page.

## Nomi v1.4 — 2026-08-12

- Multiple dictated foods are grouped into one meal with a combined calorie total.
- The meal detail page lists every item and opens its full nutrition details individually.
- Protein, carbohydrates, fat, sugar, and other available micronutrients are displayed.
- The AI provides a short, clear explanation of the main calorie sources for every product.
- Meal groups remain intact when deleting an entry or undoing a deletion.
- On-device dictation now follows the language selected in Nomi, including German recognition.
- Added the PolyForm Noncommercial license and GitHub release badge.
