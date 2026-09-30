# Nomi v2.9.0

### A friendlier start

- The welcome screen says what Nomi does in one line: **Say what you ate. Nomi does the math.** The fox settles in with a small nod, and the button now reads **Set up my plan**.
- While you answer, Nomi tells you what your answers already mean: how far your target is from today, about how many calories a day keep you where you are, and roughly when the chosen pace gets you there. The numbers come from the same calculation as your plan.
- Picking an answer gives a light haptic tick, and the plan's calorie target lands with one.
- The fox also hands over the finished plan instead of a generic icon.

### Progress

- **Longest streak** on the Progress page: the best run of consecutive logged days you have ever had. A lapsed streak no longer makes your record disappear from view.

### Fixes

- Food logged after midnight in an app left open overnight now lands on the new day instead of yesterday. Progress, the streak and History follow the date change too.
- Right after midnight, steps and activity calories showed "Not synced yet" and disappeared from Today even though the sync worked. A day with no steps yet now reads 0, and the new day is read as soon as the date changes.
- The home-screen widget keeps updating live after midnight.
- "500 milliliters juice", "2 liters milk", "8 ounces steak", "1 lb chicken" and "12 floz cola" are understood as amounts. Before, spelled-out and US units were logged as that many pieces, or the entry failed.
- "vier", "fünf" and "sechs" are understood as counts, like "four" to "six".
- Correcting a food with "only 2" sets the count to 2 instead of doubling it. "halb" halves.
- A shared day with half a portion in it imports again. Amounts are sent with two decimals instead of being rounded to whole numbers.
- The message after importing a shared day shows the date in the app's language.

Install the attached APK over your existing Nomi installation to keep your data.
