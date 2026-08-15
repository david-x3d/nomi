## Nomi 1.9 Beta 2

This beta makes grouped meal titles read like the food you actually entered.

### What changed

- Multi-item inputs now name the researched foods instead of becoming a generic menu label.
- For example, `250g tenderloin 120g pommes und ein red bull` appears as `Tenderloin mit Pommes und Red Bull`.
- Nomi uses the corrected analyzed item names and joins them with language-aware words and punctuation.
- Quantities, nutrition and sources remain available for each individual item in the meal details.
- All visual and interaction improvements from Beta 1 are still included.

### Verification

- 395 unit tests passed with no failures, errors or skipped tests.
- Android lint passed.
- The signed prerelease APK assembled successfully for Android 8.0 and newer.
- APK SHA-256: `69641F93F12B33DC25902A792B8F0567E51469DFA95A96D618A5332BCA480DD4`
