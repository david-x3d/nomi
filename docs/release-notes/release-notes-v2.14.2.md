# Nomi v2.14.2

### Historical AI usage backfill & recent requests

- **Fixed historical AI usage backfill (`NomiDatabase` v8 → v9, `MIGRATION_8_9`):** Pre-update food logs (`input_method = 'ai'`, `recent`, `favorite`, `saved_meal`, `copied_*`, `shared`, Open Food Facts/library cache, and localized nutrition label photos) are now accurately backfilled without double-counting live events.
- **Recent requests list in Usage & cost:** Added a **Recent requests** (`Vorherige Anfragen`) list to **Usage & cost** with localized model labels and full 10-language translations.

Install `Nomi-v2.14.2-release.apk` over the phone app to keep your data. Install `Nomi-v2.14.2-wear-release.apk` on the watch using Wi-Fi debugging (`adb install`) or a sideloading app such as Wear Installer. Both APKs use the existing signing key.
