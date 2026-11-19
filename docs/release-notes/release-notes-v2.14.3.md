# Nomi v2.14.3

### Reliability, performance, swipe-to-duplicate & card polish

- **Offline & background logging queue reliability:** Added exponential backoff and a retry limit for transient network errors, a persisted `JobService` so offline-queued meals drain even after process death, headless preview auto-confirmation, and exact added-calorie notification reporting.
- **Adaptive TDEE accuracy:** Switched to bidirectional, time-aware EWMA endpoints to dampen early weigh-in spikes and handle irregular weigh-in intervals, and aligned intake averaging with the observed weight span.
- **Complete backups:** Backups now preserve Adaptive TDEE state, AI usage settings, advanced AI toggles, and `ai_usage_events` while remaining backward-compatible with older backups.
- **Swipe-to-duplicate on Today:** Swipe a logged food right to log it again immediately with haptic feedback (alongside swipe-left to delete).
- **Consistent card styling:** Updated **Nutrition details** and **Where do the calories come from?** to match the flat `surfaceBright` grouped tile style used in Settings.
- **Performance optimizations:** Cached `NumberFormat` / `DateTimeFormatter` instances and hot-path regexes, moved AI usage and Adaptive TDEE aggregations off the main thread (`Dispatchers.Default`), and moved all-time Exa spend calculation into a direct SQL `SUM` query.

Install `Nomi-v2.14.3-release.apk` over the phone app to keep your data. Install `Nomi-v2.14.3-wear-release.apk` on the watch using Wi-Fi debugging (`adb install`) or a sideloading app such as Wear Installer. Both APKs use the existing signing key.
