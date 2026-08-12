# Changelog

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
