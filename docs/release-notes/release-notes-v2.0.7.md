# Nomi v2.0.7

This release fixes nutrition totals so they consistently match the amount you enter.

- Your entered quantity remains authoritative from food parsing through the saved log and UI.
- Nutrition published per 100 g or per 100 ml is scaled to that quantity for calories, protein, carbohydrates, fat and all other supported nutrients.
- Per-serving nutrition remains per serving and is never reinterpreted as a per-100 value.
- Verified manufacturer and package nutrition takes priority over unrelated generic estimates.
- Ambiguous mass/volume conversions are rejected unless research supplies an explicit serving equivalence.
- Stale estimated barcode data is refreshed when better product-specific data is available.
- Generic regression coverage spans multiple quantities, units, nutrition bases and data sources.

The APK is signed with the same certificate as prior stable releases.

SHA-256: `F59FFF43748CB4C143700DB6935A5ED5E6BB6B7BB8A6C49E48A21A3EA082C5C4`
