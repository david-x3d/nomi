# Nomi v2.0.9

This release fixes the remaining "Nomi couldn't verify nutrition for every product" failures on ordinary meals, and replaces that one message with a diagnosis.

- A meal is no longer all-or-nothing. Foods that resolved stay resolved, and only the one that did not is searched and read again, on its own.
- When something still cannot be resolved, Nomi names the food and says what would fix it — a missing portion weight, a source about a different product, or no source at all — instead of one sentence for every cause.
- Foods whose name carries a preparation word, such as cooked rice or grilled chicken breast, are no longer rejected because the nutrition page omits that word.
- A generic reading is no longer blocked by the research model writing a word into the brand field; whether a product is branded is decided by what you asked for.
- A generic food may now use a printed per-serving table as well as a per-100 g or per-100 ml one, whichever the source actually publishes.
- Timeouts, rate limits and unavailable models are reported as themselves, and a retried rate limit no longer reads as an outage.
- An unverified value now reads as an estimate rather than as unknown.

Branded and packaged products are unchanged: their nutrition still has to come from one source that supports the whole reading, a package size claim still keeps a reading out of the generic path, and a failed branded match is never quietly answered with a generic number. Per-100 values are still never republished as whole-portion totals, and grams and millilitres are still never treated as interchangeable.

The APK is signed with the same certificate as prior stable releases.

SHA-256: `BADDA6D489C0A54F479708014E69FAB6FE38915BC8EF38F88BB8AD9C9309E63C`
