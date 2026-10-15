# Nomi v2.0.8

This release fixes generic foods being rejected after the v2.0.7 scaling work.

- Everyday foods such as steak, rice or chicken breast log again at any amount, not only at exactly 100 g.
- A generic food may use a reputable generic nutrition source, without needing an exact manufacturer product.
- Branded and packaged products keep the stricter rule: their nutrition still has to come from one source that supports the whole reading.
- Nutrition published per 100 g or per 100 ml stays a per-100 value and is scaled to the amount you entered, never republished as a whole-portion total.
- A generic result that no single page could confirm is shown as an estimate rather than as verified.
- Conflicting sources are still never merged: one coherent source is chosen, or the result is an estimate.

The APK is signed with the same certificate as prior stable releases.

SHA-256: `8F7E7B8D674D49891B36257C82175E17E43DE075B46C5D47B014674644A9FF32`
