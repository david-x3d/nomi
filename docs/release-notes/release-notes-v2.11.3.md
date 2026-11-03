# Nomi v2.11.3

### More reliable Wear OS day and sync updates

- The open watch screen now checks the date every minute, clearing yesterday's nutrition totals after midnight even without a new phone update.
- Fixed a startup race that could miss a phone update while the watch was loading its initial snapshot.
- Removing the synced day now clears the watch screen instead of leaving stale totals visible.

Install `Nomi-v2.11.3-release.apk` over the phone app to keep your data. Install `Nomi-v2.11.3-wear-release.apk` on the watch using Wi-Fi debugging (`adb install`) or a sideloading app such as Wear Installer. Both APKs use the existing signing key.
