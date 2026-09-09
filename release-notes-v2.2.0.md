# Nomi v2.2.0

Meal photos now produce editable food-weight estimates using visible size references.

- The AI considers plate diameter, bowls, cutlery, food coverage, mound height, perspective and piece count when estimating each food's total edible weight.
- Assumed dimensions are identified as assumptions. Photos without a useful size reference receive a rougher estimate, or no weight when the image does not support one.
- Estimated grams appear directly in the photo review and are used to scale researched nutrition. You can correct them before continuing.
- The review shows the size cues and uncertainties behind the estimate. Piece counts are retained as context, and drinks keep their volume units.
- Nutrition still comes from the existing research pipeline. Photo portion assumptions remain attached even when a nutrition table is exact.
- The portion-estimate notice is translated into all ten supported interface languages.

Photo weights are estimates, not measurements.

### Verification

- All 481 unit tests passed across 61 classes, with no failures, errors or skips.
- Release compilation, lint-vital checks and APK assembly passed.
- The APK signature matches the previous stable release (v2.1.3).
- Photo recognition accuracy was not benchmarked against weighed meals; results remain estimates.

SHA-256: `C090295E48B8DE5BD46E95A134428D8E034D933916A2B7C5E69B66434872F44E`
