# Nomi v2.0.5

This release fixes incorrect portion totals when research returns nutrition per 100 g or 100 ml.

- Per-100 nutrition is now scaled to the user's actual logged amount for calories, macros, and all supported optional nutrients.
- The research basis is kept separate from the requested portion and, in the Exa/Gemini path, verified against exact source text.
- Values genuinely published for a complete serving remain unchanged, preventing double scaling.
- Older cached research using the previous basis contract is invalidated; reusable per-100 data is recalculated for each new portion.
- Regression coverage includes 50 g, 100 g, 400 g, full-serving values, cached per-100 data, micronutrients, and multiple provider response formats.

The APK is signed with the same certificate as prior stable releases.

SHA-256: `6036A779DAD5A8899DFA630BD90D364D3C6A5EED0C704947C5838E20F7B76E7E`
