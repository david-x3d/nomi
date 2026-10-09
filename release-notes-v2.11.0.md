# Nomi v2.11.0

### Send to Nomi and Nomi on Wear OS

#### Send to Nomi

- Nomi now appears in the Android share sheet as **Log in Nomi**.
- Shared text, such as a recipe title, a delivery order or a message, opens on the Today page as a draft. Read or trim it, then send it as you would a typed meal. Nothing goes to your AI provider until you send it.
- A shared photo goes through the normal photo flow and stops at its review step before anything is saved.

#### Nomi on your watch

- New Wear OS app, attached to this release as `Nomi-v2.11.0-wear-release.apk`. It needs Nomi on the phone and logs everything through it, using the phone's AI providers, keys and food log.
- The watch shows the calories left today, with protein, carbs and fat against your targets.
- **Log a meal** opens dictation on the watch. The phone researches the meal and saves it just like a meal dictated on the phone. The watch then confirms how many kcal were added, or shows why it could not log the meal.
- **Favorites** lists your favorite foods and saved meals. Tap one to log it at once, with no provider request.
- A tile shows a calorie ring and a button that goes straight to dictation. A watch-face complication shows the calories left, as a number or a ring.
- The watch uses the language chosen in Nomi on the phone.

#### Installing the watch app

Wear OS cannot install an APK from a phone browser. Install `Nomi-v2.11.0-wear-release.apk` on the watch with `adb install` over Wi-Fi debugging, or with a sideloading app such as Wear Installer. It is signed with the same key as the phone app, which the watch needs to reach the phone.

Install the phone APK over your existing Nomi installation to keep your data.
