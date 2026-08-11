# Nomi

Nomi is a native Android nutrition journal built around low-friction, natural-language food logging and official Material 3 Expressive components.

## Highlights

- Type, dictate, photograph, or scan a meal.
- Review researched calories and macros before saving.
- Deterministic portion scaling that preserves explicit user quantities.
- Research finishes before a meal can be previewed or saved, so calories never change silently in the background.
- Quantity support for mg, g, kg, ml, l, US fl oz, tablespoons/Esslöffel, and teaspoons/Teelöffel.
- Germany-aware product sourcing and German/English UI support.
- Offline Room history, recents, favorites, saved meals, weight tracking, and progress.
- Configurable Sonar, Exa + Gemini, Perplexity, OpenRouter, OpenAI, and OpenAI-compatible providers.
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

Nomi's existing default research provider remains OpenRouter `perplexity/sonar`. Food research
can instead select **Exa + Gemini**, which makes one request to Exa's official Search API and then
one structured-extraction request directly to Google's Gemini API. Configure both fields in that
provider dialog: the primary key is a Google Gemini API key and the second key is an Exa API key.
The default direct Google model identifier is `gemini-2.5-flash`, a stable structured-output model
suited to extraction. Transient 429/5xx responses from Exa or Google are retried with bounded
exponential backoff. A configured `gemini-3.6-flash` call that remains unavailable falls back to
`gemini-2.5-flash` before Nomi uses the separately configured smart fallback.

Exa receives a simple query built from the original food text. Gemini receives only that exact
input, Nomi's parsed quantity context, and the returned Exa documents. It selects opaque Exa source
IDs; Nomi maps those IDs back to retrieved URLs, verifies that the selected document supports the
product and values, then runs the existing Kotlin quantity reconciliation, serving normalizer,
all-zero rejection, and source-integrity checks. A validation failure can use the separately
configured smart fallback (normally Sonar). Only transient transport failures are retried; Nomi
does not retry rejected or unsupported nutrition results.

While Exa research is running, Nomi shows three restrained shimmering source slots. As soon as Exa
returns pages, those slots become the real website favicons while Gemini checks the product,
serving basis, and requested amount. The preview appears only after that pipeline has settled.

For OpenRouter-hosted Perplexity models, use the full model identifier, for example
`perplexity/sonar`.

## Privacy

Nutrition history is local-first. Nomi sends meal text or a user-selected image only to the provider configured for that action. Exported backups exclude API keys and diagnostic events.

## Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

The nutrition benchmark validates all cases and writes machine-readable preflight results without
spending API credits:

```powershell
python eval/run_eval.py
```

To classify and rescore already-saved full raw runs without API calls:

```powershell
python eval/run_eval.py --score-existing
```

For live evaluation, put `OPENROUTER_API_KEY`, `GEMINI_API_KEY`, and `EXA_API_KEY` in `eval/.env`
(which is ignored by Git). OpenRouter is needed only for the separate Sonar comparison; the Exa +
Gemini provider calls Exa and Google directly. Run the isolated, fresh-cache 10-case smoke first:

```powershell
python eval/run_eval.py --smoke
```

The full isolated Sonar and Exa + Gemini run is accepted only after the latest smoke gate passes:

```powershell
python eval/run_eval.py --live
```

Results are written under `eval/results/`. Provider responses currently do not expose reliable
per-request cost data, so cost stays null with an explanatory note rather than being guessed.

## License

No license has been granted. All rights reserved.
