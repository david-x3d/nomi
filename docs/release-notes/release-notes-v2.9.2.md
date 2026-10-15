# Nomi v2.9.2

### Fixes

- Restaurant items such as a McDonald's Hamburger no longer fail with "Nomi found nutrition for … but could not resolve that serving" when the page gives the nutrition per portion and you logged a count. A portion with its printed weight (for example "per portion (105 g)") is now matched to the burgers you logged through that weight, and a serving the page names after the food itself ("per hamburger") counts as one piece. A weight the source does not print is left out instead of failing an item that did not need it.
- A portion that holds several pieces, such as six nuggets, is still never read as one piece, and a per-100 g value still needs a printed weight before it is scaled to a count.

Install the attached APK over your existing Nomi installation to keep your data.
