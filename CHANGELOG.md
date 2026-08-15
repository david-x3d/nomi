# Changelog

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
