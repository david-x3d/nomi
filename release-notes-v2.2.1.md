# Nomi v2.2.1

- Count-based quantities such as “1 Oreo”, “3 eggs” and “half a pizza” no longer require grams.
- Preserve the user's quantity and unit in parsing, nutrition research, display and saved entries.
- Keep optional resolved grams and millilitres separate for nutrition calculations; preserve volume and conversion sources in saved meals and backups.
- Prefer manufacturer/product unit weights and volumes. Source package sizes cannot replace the requested count; unknown conversions remain unknown and estimates are labelled.
- Add regression coverage for counts, containers, fractions, manufacturer weights, source precedence, display, backups, and existing gram/ml inputs.
