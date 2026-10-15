# Nomi v2.11.2

### More reliable barcode scanning

- Barcode products that list their energy only in kilojoules now show calories instead of an empty value. Nomi converts kJ to kcal.
- Products that list only salt now show sodium, converted from the salt value.
- A throttled Open Food Facts request (HTTP 429) is retried instead of showing an error.
- One malformed number in a product's data, such as a quoted value, no longer makes the whole product look unknown.

Install `Nomi-v2.11.2-release.apk` over the phone app to keep your data. Install `Nomi-v2.11.2-wear-release.apk` on the watch using Wi-Fi debugging (`adb install`) or a sideloading app such as Wear Installer. Both APKs use the existing signing key.
