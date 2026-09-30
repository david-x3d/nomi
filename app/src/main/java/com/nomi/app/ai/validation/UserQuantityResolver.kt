package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityOrigin
import com.nomi.app.ai.model.QuantityResolutionMetadata
import com.nomi.app.ai.model.QuantityUnits
import com.nomi.app.ai.model.QuantitySemantic
import com.nomi.app.ai.parsing.GermanProductResolver
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
            // Spelled-out and US units sit beside their abbreviations: without them "500 milliliters
            // juice" or "1 lb chicken" fell through to the count pattern and logged 500 pieces.
            "ml|milliliters?|millilitres?|cl|l|liters?|litres?|" +
            "(?:us\\s*)?fl\\.?\\s*oz|oz|ounces?|lbs?|pounds?)"
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

    private val countNumber = "(?:\\d+(?:[.,]\\d+)?|one|two|three|four|five|six|a|an|ein(?:e[nrms]?)?|zwei|drei|vier|f(?:ü|ue)nf|sechs|half|halb(?:e[nrms]?)?|½)"
    private val countUnit = "(?:pieces?|items?|pcs?|servings?|portions?|portionen|packs?|packages?|packets?|packungen?|bars?|riegel|slices?|scheiben?|bottles?|flaschen?|cans?|dosen?|cups?|tassen?|st(?:ü|ue|u)cke?)"
    private val countPattern = Regex(
        """(?iu)(?<![\p{L}\p{N}.,/])($countNumber)\s+(?:(?:of\s+)?(?:a|an|eine)\s+)?(?:($countUnit)\b\s*(?:of\s+)?)?(?=[\p{L}])""",
    )
    private val sizedContainerPattern = Regex(
        """(?iu)(?<![\p{L}\p{N}.,])($countNumber)(?:\s*(x|×)\s*|\s+)(\d+(?:[.,]\d+)?)\s*[-–]?\s*(ml|l|g|kg)\b\s*[-–]?\s*($countUnit)?\b""",
    )

    /** Local fast path shares exactly the same count detection as provider reconciliation. */
    fun parseCountIntentOrNull(text: String): ParsedFoodIntent? {
        val detection = detectExplicitQuantities(text).singleOrNull() ?: return null
        if (detection.range.first != 0 || detection.metadata.semantic != QuantitySemantic.DIRECT_AMOUNT) return null
        val food = text.substring(detection.range.last + 1).trim()
        if (!Regex("""[\p{L}][\p{L}'’.-]*(?:\s+[\p{L}][\p{L}'’.-]*){0,5}""").matches(food)) return null
        if (food.split(' ').any { it.lowercase(Locale.ROOT) in setOf("and", "und", "with", "mit", "plus", "from", "von") }) return null
        return ParsedFoodIntent(text, items = listOf(ParsedFoodItem(name = food).withResolution(detection.metadata)))
    }

    private fun countValue(raw: String): Double = when (raw.lowercase(Locale.ROOT)) {
        "two", "zwei" -> 2.0
        "three", "drei" -> 3.0
        "four", "vier" -> 4.0
        "five", "fünf", "fuenf" -> 5.0
        "six", "sechs" -> 6.0
        "half", "½", "halb", "halbe", "halben", "halber", "halbes" -> 0.5
        else -> raw.replace(',', '.').toDoubleOrNull() ?: 1.0
    }

    private val menuHouseholdServingPattern = Regex(
        """(?iu)(\d+(?:[.,]\d+)?)\s*(st(?:\u00fc|ue|u)ck(?:e)?|pieces?|kugel(?:n)?|scoops?)\b""",
    )

    /**
     * Applies quantities read from a selected menu after interpretation but before research.
     * MENU_EXPLICIT prevents later provider reconciliation from replacing the printed serving.
     */
    fun applyMenuQuantities(
        menuItems: List<MenuDish>,
        parsed: ParsedFoodIntent,
    ): ParsedFoodIntent {
        if (menuItems.isEmpty() || parsed.items.isEmpty()) return parsed
        val unusedMenuIndexes = menuItems.indices.toMutableSet()
        val items = parsed.items.mapIndexed { parsedIndex, item ->
            val normalizedName = item.name.menuIdentity()
            val menuIndex = unusedMenuIndexes.firstOrNull {
                menuItems[it].name.menuIdentity() == normalizedName
            } ?: parsedIndex.takeIf { it in unusedMenuIndexes }
            val dish = menuIndex?.let(menuItems::get) ?: return@mapIndexed item
            unusedMenuIndexes -= menuIndex
            resolveMenuQuantity(dish)?.let { resolution -> item.withResolution(resolution) } ?: item
        }
        return parsed.copy(items = items)
    }

    fun reconcileParsedIntent(
        userText: String,
        parsed: ParsedFoodIntent,
        localeCountry: String? = null,
    ): ParsedFoodIntent {
        val cleanText = userText.trim()
        val enrichedItems = parsed.items.map { item ->
            GermanProductResolver.enrich(cleanText, item, parsed.items.size, localeCountry)
        }
        val explicit = detectExplicitQuantities(cleanText)
        val assignments = assignDetections(cleanText, enrichedItems, explicit)
        val items = enrichedItems.mapIndexed { index, item ->
            val resolution = item.quantityResolution
                ?.takeIf { it.origin == QuantityOrigin.MENU_EXPLICIT }
                ?: assignments[index]
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
        val quantity = requested.quantity ?: result.quantity
        val unit = requested.unit?.takeIf(String::isNotBlank) ?: result.unit
        val sameAmount = (abs(result.quantity - quantity) < MATCH_TOLERANCE &&
            QuantityUnits.normalize(result.unit) == QuantityUnits.normalize(unit)) ||
            equivalentPackage(result.quantity, result.unit, quantity, unit)
        val sourceUnitMatches = result.sourceUnit?.let(QuantityUnits::normalize) == QuantityUnits.normalize(unit)
        val packageSize = if (QuantityUnits.normalize(unit) == "pack" && result.sourcePackageQuantity != null && result.sourcePackageUnit != null) {
            runCatching { canonicalMeasure(result.sourcePackageQuantity, result.sourcePackageUnit) }.getOrNull()
        } else null
        val weight = requested.gramsEquivalent
            ?: result.sourceUnitWeightGrams?.times(quantity)?.takeIf { sourceUnitMatches }
            ?: packageSize?.quantity?.times(quantity)?.takeIf { packageSize.unit == "g" }
            ?: result.gramsEquivalent.takeIf { sameAmount }
        val volume = requested.resolvedVolumeMl
            ?: result.sourceUnitVolumeMl?.times(quantity)?.takeIf { sourceUnitMatches }
            ?: packageSize?.quantity?.times(quantity)?.takeIf { packageSize.unit == "ml" }
            ?: result.resolvedVolumeMl.takeIf { sameAmount }
        val source = resolution?.resolutionSource
            ?: result.resolutionSource
            ?: (if (result.isEstimate) "estimated serving" else result.sourceUrl ?: result.sourceName)
        return result.copy(
            quantity = requested.quantity ?: result.quantity,
            unit = requested.unit?.takeIf(String::isNotBlank) ?: result.unit,
            gramsEquivalent = weight,
            resolvedVolumeMl = volume,
            resolutionSource = source,
            servingValidation = null,
            requiresServingValidation = false,
            quantityResolution = reconciledResolution?.copy(
                resolvedWeightGrams = weight, resolvedVolumeMl = volume,
                resolutionSource = source, isEstimated = result.isEstimate,
            ),
        )
    }

    private fun ParsedFoodItem.withResolution(
        resolution: QuantityResolutionMetadata,
    ): ParsedFoodItem = copy(
        quantity = resolution.canonicalQuantity,
        unit = resolution.canonicalUnit,
        gramsEquivalent = resolution.resolvedWeightGrams ?: resolution.canonicalQuantity.takeIf {
            resolution.canonicalUnit == "g"
        },
        resolvedVolumeMl = resolution.resolvedVolumeMl ?: resolution.canonicalQuantity.takeIf {
            resolution.canonicalUnit == "ml"
        },
        quantityResolution = resolution,
        assumptions = (assumptions + when (resolution.origin) {
            QuantityOrigin.USER_EXPLICIT ->
                "The user's explicit quantity was preserved by deterministic app logic."
            QuantityOrigin.MENU_EXPLICIT ->
                "The menu's explicit serving quantity was preserved by deterministic app logic."
            QuantityOrigin.GERMAN_LOCAL_DEFAULT ->
                "A locale-specific default quantity was preserved by deterministic app logic."
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

        val containers = sizedContainerPattern.findAll(text)
            .filter { match -> packageDetections.none { match.range.overlaps(it.range) } }
            .mapNotNull { match ->
                val quantity = countValue(match.groupValues[1])
                val size = canonicalMeasure(match.groupValues[3].number(), match.groupValues[4])
                val explicitUnit = match.groupValues[5].takeIf(String::isNotBlank)
                // Without x or a container word this is two separate amounts, not a pack size.
                if (explicitUnit == null && match.groupValues[2].isBlank()) return@mapNotNull null
                val unit = explicitUnit?.let(QuantityUnits::normalize)
                    ?: if (size.unit == "ml" && text.substring(match.range.last + 1).trim().startsWith("Red Bull", true)) "can" else "serving"
                DetectedResolution(match.range, QuantityResolutionMetadata(
                    origin = QuantityOrigin.USER_EXPLICIT,
                    semantic = QuantitySemantic.DIRECT_AMOUNT,
                    canonicalQuantity = quantity, canonicalUnit = unit,
                    enteredQuantity = quantity, enteredUnit = unit,
                    resolvedWeightGrams = (size.quantity * quantity).takeIf { size.unit == "g" },
                    resolvedVolumeMl = (size.quantity * quantity).takeIf { size.unit == "ml" },
                    resolutionSource = "user-stated container size",
                ))
            }.toList()

        val directDetections = directAmountPattern.findAll(text)
            .filter { direct -> (packageDetections + containers).none { direct.range.overlaps(it.range) } }
            .map { match ->
                val enteredQuantity = match.groupValues[1].number()
                val enteredUnit = match.groupValues[2]
                val measure = canonicalMeasure(enteredQuantity, enteredUnit)
                val semanticUnit = QuantityUnits.normalize(enteredUnit)
                val preserveUnit = semanticUnit in setOf("tbsp", "tsp")
                val amount = if (preserveUnit) CanonicalMeasure(enteredQuantity, semanticUnit) else measure
                DetectedResolution(
                    range = match.range,
                    metadata = QuantityResolutionMetadata(
                        origin = QuantityOrigin.USER_EXPLICIT,
                        semantic = QuantitySemantic.DIRECT_AMOUNT,
                        canonicalQuantity = amount.quantity,
                        canonicalUnit = amount.unit,
                        enteredQuantity = enteredQuantity,
                        enteredUnit = enteredUnit,
                        resolvedWeightGrams = measure.quantity.takeIf { measure.unit == "g" },
                        resolvedVolumeMl = measure.quantity.takeIf { measure.unit == "ml" },
                        resolutionSource = "user quantity / unit conversion",
                        isApproximate = enteredUnit.normalizedUnit() in
                            setOf("loffel", "loeffel", "spoon", "spoons"),
                    ),
                )
            }
        val measures = packageDetections + containers + directDetections.toList()
        val counts = countPattern.findAll(text)
            .filter { match -> measures.none { match.range.overlaps(it.range) } }
            .map { match ->
                val quantity = countValue(match.groupValues[1])
                requireFinitePositive(quantity, "quantity")
                val unit = QuantityUnits.normalize(match.groupValues[2].ifBlank { "piece" })
                DetectedResolution(match.range, QuantityResolutionMetadata(
                    origin = QuantityOrigin.USER_EXPLICIT,
                    semantic = QuantitySemantic.DIRECT_AMOUNT,
                    canonicalQuantity = quantity, canonicalUnit = unit,
                    enteredQuantity = quantity, enteredUnit = unit,
                ))
            }
        return (measures + counts).sortedBy { it.range.first }
    }

    private fun resolveMenuQuantity(dish: MenuDish): QuantityResolutionMetadata? {
        val structured = dish.quantityText?.trim()?.takeIf(String::isNotBlank)
        val raw = structured ?: dish.description?.trim()?.takeIf(String::isNotBlank) ?: return null
        val household = menuHouseholdServingPattern.find(raw)
            ?.takeIf { structured != null || it.range.first <= MENU_LEADING_QUANTITY_MAX_OFFSET }
        if (household != null) {
            val servingQuantity = household.groupValues[1].number()
            val servingUnit = household.groupValues[2]
            val slashIndex = raw.indexOf('/', startIndex = household.range.last + 1)
            val exactWeight = slashIndex
                .takeIf { it >= 0 }
                ?.let { directAmountPattern.find(raw, startIndex = it + 1) }
                ?.takeIf { match ->
                    canonicalMeasure(match.groupValues[1].number(), match.groupValues[2]).unit == "g"
                }
            if (exactWeight != null) {
                val enteredWeight = exactWeight.groupValues[1].number()
                val enteredWeightUnit = exactWeight.groupValues[2]
                val canonicalWeight = canonicalMeasure(enteredWeight, enteredWeightUnit)
                return QuantityResolutionMetadata(
                    origin = QuantityOrigin.MENU_EXPLICIT,
                    semantic = QuantitySemantic.DIRECT_AMOUNT,
                    canonicalQuantity = canonicalWeight.quantity,
                    canonicalUnit = canonicalWeight.unit,
                    enteredQuantity = servingQuantity,
                    enteredUnit = servingUnit,
                )
            }
            // A later amount separated by a dash describes an ingredient, not the whole dish.
            return QuantityResolutionMetadata(
                origin = QuantityOrigin.MENU_EXPLICIT,
                semantic = QuantitySemantic.DIRECT_AMOUNT,
                canonicalQuantity = servingQuantity,
                canonicalUnit = servingUnit,
                enteredQuantity = servingQuantity,
                enteredUnit = servingUnit,
            )
        }

        val metric = directAmountPattern.find(raw)
            ?.takeIf { structured != null || it.range.first <= MENU_LEADING_QUANTITY_MAX_OFFSET }
            ?: return null
        val enteredQuantity = metric.groupValues[1].number()
        val enteredUnit = metric.groupValues[2]
        val canonical = canonicalMeasure(enteredQuantity, enteredUnit)
        return QuantityResolutionMetadata(
            origin = QuantityOrigin.MENU_EXPLICIT,
            semantic = QuantitySemantic.DIRECT_AMOUNT,
            canonicalQuantity = canonical.quantity,
            canonicalUnit = canonical.unit,
            enteredQuantity = enteredQuantity,
            enteredUnit = enteredUnit,
        )
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
            "oz", "ounce", "ounces" -> CanonicalMeasure(quantity * 28.349523125, "g")
            "lb", "lbs", "pound", "pounds" -> CanonicalMeasure(quantity * 453.59237, "g")
            "tbsp", "tbs", "tablespoon", "tablespoons", "el", "essloffel", "essloeffel" ->
                CanonicalMeasure(quantity * 15.0, "ml")
            "tsp", "teaspoon", "teaspoons", "tl", "teeloffel", "teeloeffel" ->
                CanonicalMeasure(quantity * 5.0, "ml")
            "loffel", "loeffel", "spoon", "spoons" ->
                CanonicalMeasure(quantity * 15.0, "ml")
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> CanonicalMeasure(quantity, "ml")
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
        // "floz", "fl.oz" and "usfl oz" are all accepted by the unit pattern above, so they must
        // land on the same spelling [canonicalMeasure] knows rather than failing the whole entry.
        .replace(Regex("^(us ?)?fl ?oz$")) { if (it.groupValues[1].isEmpty()) "fl oz" else "us fl oz" }

    private fun String.menuIdentity(): String = trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
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

    private const val MENU_LEADING_QUANTITY_MAX_OFFSET = 8
}
