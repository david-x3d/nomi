# Nomi v2.6.1

Fixes NFC sharing failing with “This phone is not a Nomi this version can read”.

- Corrected the NFC application identifier so Android can route taps to Nomi.
- Receiving now skips Android's NDEF probe and connects directly to Nomi's card service.
- Corrected SELECT command parsing, including requests with no optional response-length byte.
- Centred the upper-left fox logo inside its circle across all animated moods.

**Update Nomi on both phones before sharing.** Version 2.6.0 used an invalid NFC identifier and cannot communicate with the corrected version.

On the sending phone, choose **Share**, select foods, then **Tap to share**. On the other phone, choose **Receive a shared day**. Keep both phones unlocked, NFC enabled, and hold their NFC areas together.

Validation: automated NFC protocol and application unit tests, plus a signed release build. A transfer between two physical phones still needs hardware verification.
