# Nomi v2.11.1

### Wear OS stability and sharper launcher icon

- Fixed a watch app crash when displaying synced nutrition targets, including while the phone app is closed. The macro progress bars now use the supported Wear OS stroke width.
- The watch launcher uses density-specific fox artwork with filtered scaling for a sharper icon.
- Updated the APKs in this release (build 144) to optimize the watch's scrolling and touch handling with R8, remove per-frame card morphing, reuse number formatters, and keep favorite rows stable during updates. The version remains v2.11.1.

This release also includes **Send to Nomi** in the Android share sheet and the Wear OS app introduced in v2.11.0. The watch shows today's calories and macros, logs dictated meals through the paired phone, and offers favorites, a tile and a watch-face complication.

Install `Nomi-v2.11.1-release.apk` over the phone app to keep your data. Install `Nomi-v2.11.1-wear-release.apk` on the watch using Wi-Fi debugging (`adb install`) or a sideloading app such as Wear Installer. Both APKs use the existing signing key.
