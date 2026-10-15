# Nomi v2.9.3

### Fixes

**Amounts**

- Correcting a food to "12 pieces" sets it to 12 pieces. Before, two-digit counts were read as a fraction ("1 of 2 pieces") and the food was halved.
- A fat percentage is part of the product name: "Milch 1,5% 250 ml" logs 250 ml, not 1.5 % of it. "50% of a 200 g bag" still logs half the bag.
- Chia seeds, almonds, flaxseed, bran and cocoa powder are no longer refused as "not possible per 100 g". Fibre was being counted twice for sources that already include it in the carbohydrates.
- With the calorie estimate set to overestimate, oil, butter and sugar can be logged again. The setting now also applies when Food research runs on Exa + Gemini.

**Your entries**

- Duplicating an entry creates a separate entry. Before, the copy was merged into the original's row and deleting it removed both. Duplicating a meal row copies the whole meal.
- Starting a new entry by voice, photo or barcode while a logged line is open for rewriting no longer deletes that line.
- Changing the amount in the preview's edit dialog no longer makes the meal impossible to save, and an impossible correction shows a message instead of closing the app. Tapping Apply without changing anything leaves a verified entry verified.
- A food logged onto an earlier day is written to Health Connect on that day instead of on today.
- A label photo or a re-scanned barcode no longer shows the web sources of the meal researched before it.

**Barcodes and backups**

- A barcode Open Food Facts does not know is researched on the web, as intended, instead of showing a provider error.
- One entry can no longer block every backup: a manual entry saved without a unit, or a very long typed entry, used to make each later export fail. Manual entries now need a unit.
- A failed backup import puts back your tracked nutrients, estimate setting and goals card, not only the other settings.

**Smaller things**

- After a time-zone change, "today" and new entries follow the new zone without restarting Nomi.
- The update check orders pre-releases correctly (beta.10 is newer than beta.2).
- A shared day from a phone whose date is ahead lands on today instead of on a day you cannot open.

Install the attached APK over your existing Nomi installation to keep your data.
