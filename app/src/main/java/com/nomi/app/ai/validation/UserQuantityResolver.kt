package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityOrigin
import com.nomi.app.ai.model.QuantityResolutionMetadata
import com.nomi.app.ai.model.QuantitySemantic
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Reconciles quantities outside the language model.
 *
 * Precedence is deliberately fixed: explicit user quantity, then a locale-specific default,
 * then provider/source inference. Nutrition sources can describe a different pack or serving,
 * but those values never replace the resolved logged amount.
 */
object UserQuantityResolver {
    private const val US_FLUID_OUNCE_ML = 29.5735295625
    private const val MATCH_TOLERANCE = 1e-6

    private val amountUnit =
        "(mg|milligrams?|milligramm|kg|kilograms?|kilogramm|g|grams?|gramm|" +
            "(?:essl(?:\\u00f6|oe|o)ffel|el|tbsp|tbs|tablespoons?)|" +
            "(?:teel(?:\\u00f6|oe|o)ffel|tl|tsp|teaspoons?)|" +
            "(?:l(?:ö|oe|o)ffel|spoons?)|" +
            "ml|cl|l|liter|litre|(?:us\\s*)?fl\\.?\\s*oz|oz)"
    private val decimal = "(\\d+(?:[.,]\\d+)?)"

    private val percentagePackagePattern = Regex(
        """(?iu)$decimal\s*(?:%|prozent)\s*(?:(?:of|von)\s+)?""" +
            """(?:(?:a|an|the|einer|einem|einen|eine|der|dem|den)\s+)?""" +
            """$decimal\s*[-–—]?\s*$amountUnit\b""",
    )

    private val fractionPackagePattern = Regex(
        """(?iu)(½|⅓|⅔|1\s*/\s*2|1\s*/\s*3|2\s*/\s*3|""" +
            """one\s+half|half|one\s+third|two\s+thirds?|""" +
            """die\s+h(?:ä|ae)lfte|eine[nrms]?\s+halbe[nrms]?|halb(?:e[nrms]?)?|""" +
            """ein(?:e[nrms]?)?\s+drittel|zwei\s+drittel)\s*""" +
            """(?:(?:of|von)\s+)?(?:(?:a|an|the|einer|einem|einen|eine|der|dem|den)\s+)?""" +
            """$decimal\s*[-–—]?\s*$amountUnit\b""",
    )

    private val directAmountPattern = Regex(
        """(?iu)$decimal\s*[-–—]?\s*$amountUnit\b""",
    )

    fun reconcileParsedIntent(
        userText: String,
        parsed: ParsedFoodIntent,
        localeCountry: String? = null,
    ): ParsedFoodIntent {
        val cleanText = userText.trim()
        val explicit = detectExplicitQuantities(cleanText)
        val assignments = assignDetections(cleanText, parsed.items, explicit)
        val localeIsGermany = localeCountry.equals("DE", ignoreCase = true)

        val items = parsed.items.mapIndexed { index, item ->
            val resolution = assignments[index]
                ?: germanRedBullDefault(cleanText, item, parsed.items.size, localeIsGermany)
            if (resolution == null) {
                // Clear any provider-forged resolution metadata.
                item.copy(quantityResolution = null)
            } else {
                item.withResolution(resolution)
            }
        }
        return parsed.copy(originalText = cleanText, items = items)
    }

    fun reconcileIntent(
        intent: ParsedFoodIntent,
        localeCountry: String? = null,
    ): ParsedFoodIntent = reconcileParsedIntent(intent.originalText, intent, localeCountry)

    /**
     * Forces provider output back to the deterministic intent before serving normalization.
     * Source nutrition and source-serving fields are intentionally left unchanged.
     */
    fun reconcileAnalysis(
        intent: ParsedFoodIntent,
        providerResult: FoodAnalysis,
    ): FoodAnalysis {
        if (intent.items.size != providerResult.items.size) {
            throw AiValidationException(
                "Nutrition research must return exactly one result for each logged item",
            )
        }
        return providerResult.copy(
            items = providerResult.items.mapIndexed { index, result ->
                reconcileAnalyzedItem(intent.items[index], result)
            },
        )
    }

    private fun reconcileAnalyzedItem(
        requested: ParsedFoodItem,
        result: AnalyzedFoodItem,
    ): AnalyzedFoodItem {
        val resolution = requested.quantityResolution
        val reconciledResolution = resolution?.withSourcePackage(
            result.sourcePackageQuantity,
            result.sourcePackageUnit,
        )
        return result.copy(
            quantity = requested.quantity ?: result.quantity,
            unit = requested.unit?.takeIf(String::isNotBlank) ?: result.unit,
            gramsEquivalent = requested.gramsEquivalent ?: result.gramsEquivalent,
            servingValidation = null,
            requiresServingValidation = false,
            quantityResolution = reconciledResolution,
        )
    }

    private fun ParsedFoodItem.withResolution(
        resolution: QuantityResolutionMetadata,
    ): ParsedFoodItem = copy(
        quantity = resolution.canonicalQuantity,
        unit = resolution.canonicalUnit,
        gramsEquivalent = resolution.canonicalQuantity.takeIf {
            resolution.canonicalUnit == "g"
        },
        quantityResolution = resolution,
        assumptions = (assumptions + when (resolution.origin) {
            QuantityOrigin.USER_EXPLICIT ->
                "The user's explicit quantity was preserved by deterministic app logic."
            QuantityOrigin.GERMAN_LOCAL_DEFAULT ->
                "German Red Bull can default: 250 ml because no explicit size was provided."
            QuantityOrigin.SOURCE_OR_INFERRED ->
                "Quantity came from source or provider inference."
        }).distinct().takeLast(12),
    )

    private fun detectExplicitQuantities(text: String): List<DetectedResolution> {
        val packageDetections = buildList {
            percentagePackagePattern.findAll(text).forEach { match ->
                val percentage = match.groupValues[1].number()
                val packageQuantity = match.groupValues[2].number()
                val packageUnit = match.groupValues[3]
                add(
                    DetectedResolution(
                        range = match.range,
                        metadata = packageResolution(
                            packageQuantity = packageQuantity,
                            packageUnit = packageUnit,
                            multiplier = percentage / 100.0,
                            semantic = QuantitySemantic.PACKAGE_PERCENT,
                            percentage = percentage,
                        ),
                    ),
                )
            }
            fractionPackagePattern.findAll(text).forEach { match ->
                val (numerator, denominator) = fraction(match.groupValues[1])
                val packageQuantity = match.groupValues[2].number()
                val packageUnit = match.groupValues[3]
                add(
                    DetectedResolution(
                        range = match.range,
                        metadata = packageResolution(
                            packageQuantity = packageQuantity,
                            packageUnit = packageUnit,
                            multiplier = numerator.toDouble() / denominator,
                            semantic = QuantitySemantic.PACKAGE_FRACTION,
                            fractionNumerator = numerator,
                            fractionDenominator = denominator,
                        ),
                    ),
                )
            }
        }.sortedBy { it.range.first }

        val directDetections = directAmountPattern.findAll(text)
            .filter { direct -> packageDetections.none { direct.range.overlaps(it.range) } }
            .map { match ->
                val enteredQuantity = match.groupValues[1].number()
                val enteredUnit = match.groupValues[2]
                val amount = canonicalMeasure(enteredQuantity, enteredUnit)
                DetectedResolution(
                    range = match.range,
                    metadata = QuantityResolutionMetadata(
                        origin = QuantityOrigin.USER_EXPLICIT,
                        semantic = QuantitySemantic.DIRECT_AMOUNT,
                        canonicalQuantity = amount.quantity,
                        canonicalUnit = amount.unit,
                        enteredQuantity = enteredQuantity,
                        enteredUnit = enteredUnit,
                        isApproximate = enteredUnit.normalizedUnit() in
                            setOf("loffel", "loeffel", "spoon", "spoons"),
                    ),
                )
            }
        return (packageDetections + directDetections).sortedBy { it.range.first }
    }

    private fun packageResolution(
        packageQuantity: Double,
        packageUnit: String,
        multiplier: Double,
        semantic: QuantitySemantic,
        fractionNumerator: Int? = null,
        fractionDenominator: Int? = null,
        percentage: Double? = null,
    ): QuantityResolutionMetadata {
        requireFinitePositive(packageQuantity, "package amount")
        requireFinitePositive(multiplier, "package fraction")
        if (semantic == QuantitySemantic.PACKAGE_PERCENT && multiplier > 1.0) {
            throw AiValidationException("Package percentage cannot exceed 100%")
        }
        val packageBase = canonicalMeasure(packageQuantity, packageUnit)
        val consumed = packageBase.quantity * multiplier
        return QuantityResolutionMetadata(
            origin = QuantityOrigin.USER_EXPLICIT,
            semantic = semantic,
            canonicalQuantity = consumed,
            canonicalUnit = packageBase.unit,
            packageQuantity = packageQuantity,
            packageUnit = packageUnit.normalizedUnit(),
            fractionNumerator = fractionNumerator,
            fractionDenominator = fractionDenominator,
            percentage = percentage,
            isApproximate = semantic == QuantitySemantic.PACKAGE_FRACTION &&
                abs(consumed - round(consumed)) > MATCH_TOLERANCE,
        )
    }

    private fun assignDetections(
        text: String,
        items: List<ParsedFoodItem>,
        detections: List<DetectedResolution>,
    ): Map<Int, QuantityResolutionMetadata> {
        if (detections.isEmpty() || items.isEmpty()) return emptyMap()
        if (detections.size == 1 && items.size == 1) return mapOf(0 to detections.single().metadata)

        val available = items.indices.toMutableSet()
        val result = mutableMapOf<Int, QuantityResolutionMetadata>()
        detections.forEachIndexed { order, detection ->
            val target = available.minByOrNull { index ->
                distanceFromDetection(text, detection.range, items[index], index, order)
            } ?: return@forEachIndexed
            result[target] = detection.metadata
            available -= target
        }
        return result
    }

    private fun distanceFromDetection(
        text: String,
        range: IntRange,
        item: ParsedFoodItem,
        itemIndex: Int,
        detectionIndex: Int,
    ): Int {
        val haystack = text.lowercase(Locale.ROOT)
        val tokens = sequenceOf(item.brand, item.name)
            .filterNotNull()
            .flatMap {
                it.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).asSequence()
            }
            .filter { it.length >= 3 }
            .distinct()
            .toList()
        val closest = tokens.mapNotNull { token ->
            val after = haystack.indexOf(token, startIndex = range.last + 1)
            if (after >= 0) after - range.last else null
        }.minOrNull()
        return closest ?: (10_000 + abs(itemIndex - detectionIndex))
    }

    private fun germanRedBullDefault(
        text: String,
        item: ParsedFoodItem,
        itemCount: Int,
        localeIsGermany: Boolean,
    ): QuantityResolutionMetadata? {
        if (!localeIsGermany) return null
        val itemText = listOfNotNull(item.brand, item.name).joinToString(" ")
        val isRedBull = itemText.contains("red bull", ignoreCase = true) ||
            (itemCount == 1 && text.contains("red bull", ignoreCase = true))
        if (!isRedBull) return null
        return QuantityResolutionMetadata(
            origin = QuantityOrigin.GERMAN_LOCAL_DEFAULT,
            semantic = QuantitySemantic.LOCAL_CAN_DEFAULT,
            canonicalQuantity = 250.0,
            canonicalUnit = "ml",
        )
    }

    private fun QuantityResolutionMetadata.withSourcePackage(
        sourceQuantity: Double?,
        sourceUnit: String?,
    ): QuantityResolutionMetadata {
        if (sourceQuantity == null || sourceUnit.isNullOrBlank()) return copy(
            sourcePackageQuantity = null,
            sourcePackageUnit = null,
            sourcePackageConflict = false,
        )
        requireFinitePositive(sourceQuantity, "source package amount")
        val conflict = packageQuantity != null && packageUnit != null &&
            !equivalentPackage(packageQuantity, packageUnit, sourceQuantity, sourceUnit)
        return copy(
            sourcePackageQuantity = sourceQuantity,
            sourcePackageUnit = sourceUnit.normalizedUnit(),
            sourcePackageConflict = conflict,
        )
    }

    private fun equivalentPackage(
        firstQuantity: Double,
        firstUnit: String,
        secondQuantity: Double,
        secondUnit: String,
    ): Boolean = runCatching {
        val first = canonicalMeasure(firstQuantity, firstUnit)
        val second = canonicalMeasure(secondQuantity, secondUnit)
        first.unit == second.unit && relativeDifference(first.quantity, second.quantity) <= MATCH_TOLERANCE
    }.getOrDefault(false)

    private fun canonicalMeasure(quantity: Double, rawUnit: String): CanonicalMeasure {
        requireFinitePositive(quantity, "quantity")
        return when (val unit = rawUnit.normalizedUnit()) {
            "mg", "milligram", "milligrams", "milligramm" ->
                CanonicalMeasure(quantity * 0.001, "g")
            "g", "gram", "grams", "gramm" -> CanonicalMeasure(quantity, "g")
            "kg", "kilogram", "kilograms", "kilogramm" ->
                CanonicalMeasure(quantity * 1_000.0, "g")
            "oz" -> CanonicalMeasure(quantity * 28.349523125, "g")
            "tbsp", "tbs", "tablespoon", "tablespoons", "el", "essloffel", "essloeffel" ->
                CanonicalMeasure(quantity * 15.0, "ml")
            "tsp", "teaspoon", "teaspoons", "tl", "teeloffel", "teeloeffel" ->
                CanonicalMeasure(quantity * 5.0, "ml")
            "loffel", "loeffel", "spoon", "spoons" ->
                CanonicalMeasure(quantity * 15.0, "ml")
            "ml" -> CanonicalMeasure(quantity, "ml")
            "cl" -> CanonicalMeasure(quantity * 10.0, "ml")
            "l", "liter", "liters", "litre", "litres" -> CanonicalMeasure(quantity * 1_000.0, "ml")
            "fl oz", "us fl oz" -> CanonicalMeasure(quantity * US_FLUID_OUNCE_ML, "ml")
            else -> throw AiValidationException("Unsupported explicit quantity unit: $rawUnit")
        }
    }

    private fun fraction(raw: String): Pair<Int, Int> {
        val token = raw.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        return when {
            token == "⅔" || token.replace(" ", "") == "2/3" ||
                token.startsWith("two third") || token.startsWith("zwei drittel") -> 2 to 3
            token == "⅓" || token.replace(" ", "") == "1/3" ||
                token.startsWith("one third") || token.contains("drittel") -> 1 to 3
            else -> 1 to 2
        }
    }

    private fun String.number(): Double = replace(',', '.').toDouble().also {
        requireFinitePositive(it, "quantity")
    }

    private fun String.normalizedUnit(): String = trim()
        .lowercase(Locale.ROOT)
        .replace("fl. oz", "fl oz")
        .replace('\u00e4', 'a')
        .replace('\u00f6', 'o')
        .replace('\u00fc', 'u')
        .replace("\u00df", "ss")
        .replace(Regex("[._-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun requireFinitePositive(value: Double, label: String) {
        if (!value.isFinite() || value <= 0.0) throw AiValidationException(
            "$label must be finite and greater than zero",
        )
    }

    private fun relativeDifference(first: Double, second: Double): Double =
        abs(first - second) / maxOf(abs(first), abs(second), 1.0)

    private data class DetectedResolution(
        val range: IntRange,
        val metadata: QuantityResolutionMetadata,
    )

    private data class CanonicalMeasure(val quantity: Double, val unit: String)
}
