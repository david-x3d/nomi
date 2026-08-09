# Nomi

Nomi is a native Android nutrition journal built around low-friction, natural-language food logging and official Material 3 Expressive components.

## Highlights

- Type, dictate, photograph, or scan a meal.
- Review researched calories and macros before saving.
- Deterministic portion scaling that preserves explicit user quantities.
- Quantity support for mg, g, kg, ml, l, US fl oz, tablespoons/Esslöffel, and teaspoons/Teelöffel.
- Germany-aware product sourcing and German/English UI support.
- Offline Room history, recents, favorites, saved meals, weight tracking, and progress.
- Configurable Perplexity, OpenRouter, OpenAI, and OpenAI-compatible providers.
- API keys stored with Android Keystore-backed encryption and excluded from backups.
- Light, dark, dynamic color, edge-to-edge layout, and Material 3 Expressive motion.

## Requirements

- Android Studio with JDK 17
- Android SDK 37
- Android 8.0 (API 26) or newer device

## Build

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## AI setup

Open **Settings → AI providers**, select a provider and model for each pipeline, enter the API key, then use **Test connection**. Credentials stay on-device and are never included in Room, DataStore backups, logs, or repository files.

Nomi defaults text and live nutrition research to OpenRouter's `openai/gpt-5.6-sol`. OpenRouter
research uses OpenRouter's Exa-backed web plugin and requires enough credit for model tokens and
search. The complete model identifier is preferred; Nomi also normalizes `gpt5.6sol` and
`gpt-5.6-sol` to `openai/gpt-5.6-sol`.

For OpenRouter-hosted Perplexity models, use the full model identifier, for example
`perplexity/sonar`.

## Privacy

Nutrition history is local-first. Nomi sends meal text or a user-selected image only to the provider configured for that action. Exported backups exclude API keys and diagnostic events.

## Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## License

No license has been granted. All rights reserved.
