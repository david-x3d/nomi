# Nomi v2.5.0

Two screens reworked: **History** now lets you pick exactly what you reuse, and **Progress** finally
shows a streak worth looking at.

## History: pick, don't just copy whole meals

History used to offer one "copy" chip per meal, so a four-meal day filled the screen with five chips
before you had read a single food. Those are gone. Each day now has **one overflow menu** with three
actions, and the day still reads as a day.

- **Copy day** — unchanged. The whole day onto today in one tap.
- **Add items to today** — pick the foods you actually want again.
- **Save meal** — pick the foods, then name them, and keep them in your library.

### Selection happens in place

Pressing either picking action turns History's own rows into a picker. There is no dialog with a
column of checkboxes:

- **Tap a row once to select it, tap again to deselect.** A single food costs a single tap.
- The **whole row** is the target, not a small tick in the corner.
- The bottom bar counts what you picked and the button says what it will do — *Add 1 item to today*,
  *Add 4 items to today*, *Save 3 items* — so a miscount is visible before you commit.
- Cancel is the ✕ in the bar, and the system back gesture does the same thing.

Tapping a food outside a picker still opens its nutrition detail, exactly as before. A meal logged as
one combined entry is picked as a whole, because half a meal is not a meal.

Copied foods keep the portions and macros you logged, and a picked meal stays a picked meal on your
plate. Breakfast is never quietly merged into dinner.

## Progress: a streak you can read at a glance

Progress had a percentage and a wavy line. It now leads with the number that actually matters.

- **Your real streak, large.** It never resets and it is never reduced by anything on this screen.
- **A ring of exactly 30 segments** — one per day of the current cycle — so you count days rather
  than estimate a fraction.
- **The milestone cycle shown correctly.** Day 30 reads *30 / 30*; day 31 starts fresh at *1 / 30*.
  Milestones run 30 → 60 → 90 → 120 → 150 and keep going, and no value gets stuck at the end of a
  cycle.
- **Your next milestone, and the distance to it.** A 67-day streak reads *67 day streak*, *7 / 30*,
  *Next milestone 90 days* — all true at once.
- **A milestone trail** of the last few rungs you reached and the next ones ahead, in plain words
  rather than badges.

Also reworked:

- **Logging consistency** keeps its percentage and gains a **30-column activity strip** showing *when*
  you logged across the selected range, not just how much. One column per day at 30 days, thirty even
  slices at a year.
- **Weight** now says how much it moved over the range ("1.4 kg down over this range"), in words
  rather than another bare number.
- **Daily averages** gained context ("Averaged over 12 logged days") and macros are now a row of
  figures you can compare instead of a list to read down.
- Tighter card rhythm, one label-above-value shape throughout, and support for reduced motion
  everywhere new.

## Languages

Every new string ships in all ten of Nomi's languages: English, German, Spanish, French, Italian,
Dutch, Portuguese, Albanian, Swedish and Turkish. Singular and plural button labels are separate
phrases in every one of them, so no language is shown "*Add 1 items*".

## Upgrading

Normal update — your log, saved meals, streak history and settings are untouched. The release is
signed with the same key as every previous Nomi, so it installs straight over v2.4.0.
