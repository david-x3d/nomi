# Nomi v2.6.2

Fixes the sharing window immediately disappearing after tapping **Tap to share**.

- Closing the food menu no longer cancels an active NFC share or receive session.
- The sender explicitly prefers Nomi’s card service. On Android 15 and newer it disables its own tag polling while offering a day.
- Brief link loss no longer deletes the offered day. Offers end on cancellation, leaving the sharing screen, or after two minutes.
- The receiver stays in reader mode after failed contact and while showing the received preview, avoiding a premature return to Android’s generic tag discovery. Separate the phones and tap again to retry.
- An empty offer is no longer misreported as an incompatible Nomi version.
- Duplicate and late NFC callbacks cannot overwrite a cancelled session.
- The button that keeps a day which arrived over NFC now reads **Add** instead of **Add to my diary**. The dialog already says which day it is, so the shorter label says the same thing and fits the button in every language.

Update both phones. On one phone choose **Share → Tap to share**; on the other choose **Receive a shared day** before bringing the phones together. Keep Nomi open on both phones.

Validation: application unit tests, signed release build, and emulator UI checks. NFC radio behaviour on Xiaomi 17 Ultra / HyperOS 3 still requires verification on physical devices.
