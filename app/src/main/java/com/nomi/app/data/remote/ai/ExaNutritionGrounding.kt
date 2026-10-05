package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityUnits
import com.nomi.app.ai.model.ResearchNutritionBasis
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.NutritionFailureReason
import com.nomi.app.ai.validation.NutritionResearchException
import java.net.URI
import java.util.Locale
import kotlin.math.abs

internal fun ParsedFoodItem.unitForOneUnstatedItem(): String {
    val identity = listOfNotNull(brand, name).joinToString(" ").normalizedBasisEvidence()
    val singleProduct = SINGLE_PRODUCT_FOOD.containsMatchIn(identity) &&
        !MULTI_ITEM_PRODUCT.containsMatchIn(identity)
    return if (singleProduct) "piece" else "serving"
}

/** Source choice, evidence checks, and estimate policy are explicit, ordered stages. */
internal fun groundExtractedItem(
    parsed: ParsedFoodItem,
    extracted: GeminiNutritionItem,
    documents: List<ExaNutritionDocument>,
): AnalyzedFoodItem {
    val selected = selectNutritionSources(parsed, extracted, documents)
    val evidence = findGroundedReading(parsed, extracted, selected)
    evidence.grounded?.let { (reading, document) ->
        val supporting = selected.supporting.takeIf { document.sourceId == selected.primary.sourceId }.orEmpty()
        return reading.withSingleProductServing(parsed, document).toAnalyzedItem(parsed, document, supporting)
    }
    return estimateUnsupportedReading(
        parsed, extracted, documents, selected,
        evidence.failures.reportedFailure(selected.primary, selected.candidates),
    )
}

private data class NutritionSourceSelection(
    val primary: ExaNutritionDocument,
    val supporting: List<ExaNutritionDocument>,
    val candidates: List<ExaNutritionDocument>,
)

private fun selectNutritionSources(
    parsed: ParsedFoodItem,
    extracted: GeminiNutritionItem,
    documents: List<ExaNutritionDocument>,
): NutritionSourceSelection {
    val byId = documents.associateBy(ExaNutritionDocument::sourceId)
    val primary = byId[extracted.sourceId]
        ?: throw NutritionResearchException(
            reason = NutritionFailureReason.NO_SUITABLE_SOURCE,
            itemName = parsed.name,
            detail = "Gemini selected a source that Exa did not return",
        )
    val supporting = extracted.supportingSourceIds.distinct().map { sourceId ->
        byId[sourceId]
            ?: throw NutritionResearchException(
                reason = NutritionFailureReason.NO_SUITABLE_SOURCE,
                itemName = parsed.name,
                detail = "Gemini selected a supporting source that Exa did not return",
            )
    }.filter { it.sourceId != primary.sourceId }.take(5)

    val officialProductDocuments = documents.filter { candidate ->
        candidate.isOfficialBrandDocument(parsed.brand) &&
            candidate.containsNutritionTable() &&
            candidate.supportsRequestedIdentity(parsed)
    }
    val eligibleDocuments = officialProductDocuments.ifEmpty { documents }
    val candidates = eligibleDocuments.sortedBy { candidate ->
        when {
            candidate.isOfficialBrandDocument(parsed.brand) -> 0
            candidate.sourceId == primary.sourceId -> 1
            else -> 2
        }
    }
    return NutritionSourceSelection(primary, supporting, candidates)
}

private data class GroundedReading(val reading: GeminiNutritionItem, val document: ExaNutritionDocument)
private data class EvidenceAttempt(
    val grounded: GroundedReading?,
    val failures: Map<String, NutritionResearchException>,
)

/** Try the original reading on every eligible page before repairing unsupported bridges. */
private fun findGroundedReading(
    parsed: ParsedFoodItem,
    extracted: GeminiNutritionItem,
    selected: NutritionSourceSelection,
): EvidenceAttempt {
    val evidenceFailures = linkedMapOf<String, NutritionResearchException>()
    fun groundedIn(reading: GeminiNutritionItem, candidate: ExaNutritionDocument): Boolean = try {
        requireNutritionEvidence(reading, parsed, candidate)
        true
    } catch (failure: NutritionResearchException) {
        evidenceFailures.putIfAbsent(candidate.sourceId, failure)
        false
    }

    selected.candidates.firstOrNull { groundedIn(extracted, it) }?.let { groundedPrimary ->
        return EvidenceAttempt(GroundedReading(extracted, groundedPrimary), evidenceFailures)
    }
    // A unit weight or volume the page does not print cannot be kept, but it is no reason to
    // discard nutrition the page does print: a per-burger reading needs no weight at all. The
    // reading goes on without it, and if the arithmetic did need that bridge, the normalizer
    // still refuses the item for exactly that reason.
    selected.candidates.firstNotNullOfOrNull { candidate ->
        extracted.keepingBridgesPrintedIn(parsed, candidate)
            .takeIf { it != extracted && groundedIn(it, candidate) }
            ?.let { candidate to it }
    }?.let { (groundedPrimary, reading) ->
        return EvidenceAttempt(GroundedReading(reading, groundedPrimary), evidenceFailures)
    }

    return EvidenceAttempt(null, evidenceFailures)
}

/** Only an explicitly permitted estimate can survive when no page supports the full reading. */
private fun estimateUnsupportedReading(
    parsed: ParsedFoodItem,
    extracted: GeminiNutritionItem,
    documents: List<ExaNutritionDocument>,
    selected: NutritionSourceSelection,
    failure: NutritionResearchException?,
): AnalyzedFoodItem {
    val allZero = extracted.calories == 0.0 && extracted.proteinGrams == 0.0 &&
        extracted.carbohydrateGrams == 0.0 && extracted.fatGrams == 0.0
    if (allZero) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES,
            itemName = parsed.name,
            detail = "A zero-calorie result needs explicit zero-calorie evidence from Exa",
        )
    }
    val parsedQuantity = parsed.quantity
    val parsedUnit = parsed.unit
    val explicitWholeServingEstimate = extracted.isEstimate &&
        extracted.nutritionBasis == ResearchNutritionBasis.SOURCE_SERVING &&
        extracted.sourceBasisText.isNullOrBlank() &&
        parsedQuantity != null && !parsedUnit.isNullOrBlank() &&
        equivalentServing(
            extracted.sourceServingQuantity,
            extracted.sourceServingUnit,
            parsedQuantity,
            parsedUnit,
        )
    val genericEstimate = !explicitWholeServingEstimate &&
        extracted.qualifiesAsGenericEstimate(parsed, documents)
    if (!explicitWholeServingEstimate && !genericEstimate) {
        throw failure ?: NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "No single product-specific source supports the reported nutrition and basis",
        )
    }
    // Nothing below may keep a citation or a product identity: no single document supported the
    // complete reading, so the item is offered as an explicit estimate. The claimed brand and
    // package go with it - an unverified number must not carry a specific product's name.
    val ungrounded = extracted.toAnalyzedItem(parsed, selected.primary, emptyList()).copy(
        brand = parsed.brand,
        sourceName = null,
        sourceUrl = null,
        supportingSourceUrls = emptyList(),
        sourceProductName = null,
        sourceBasisText = null,
        sourcePackageQuantity = null,
        sourcePackageUnit = null,
        isEstimate = true,
        assumptions = (
            extracted.assumptions +
                "Live research ran for this item, but the retrieved page excerpt did not " +
                "contain every number needed for independent verification; shown as an estimate."
            ).distinct().takeLast(12),
    )
    return if (genericEstimate) {
        // A generic food carries its own self-contained basis. Keep it exactly as researched so
        // the deterministic normalizer scales it to the logged amount; rewriting it to the logged
        // serving would republish a per-100 reading as a whole-portion total, which is the bug
        // this basis exists to prevent.
        ungrounded
    } else {
        // The remaining values are an estimate for the requested restaurant/item portion. Keep
        // its basis identical to the logged basis so the deterministic normalizer does not try to
        // convert an unknown size through g/ml.
        ungrounded.copy(
            sourceServingQuantity = parsed.quantity,
            sourceServingUnit = parsed.unit,
            sourceServingGramsEquivalent = parsed.gramsEquivalent,
            nutritionBasis = ResearchNutritionBasis.SOURCE_SERVING,
        )
    }
}

/**
 * The refusal of the page Gemini says it read, or failing that of the first page tried, which is
 * the official brand page when there is one. The other pages' reasons follow in the detail, so
 * the debug log shows why each page was refused rather than only the one that was reported.
 */
private fun Map<String, NutritionResearchException>.reportedFailure(
    primary: ExaNutritionDocument,
    candidates: List<ExaNutritionDocument>,
): NutritionResearchException? {
    val reportedId = primary.sourceId.takeIf(::containsKey)
        ?: candidates.map(ExaNutritionDocument::sourceId).firstOrNull(::containsKey)
        ?: return null
    val reported = getValue(reportedId)
    val others = filterKeys { it != reportedId }.entries.joinToString("; ") { (sourceId, failure) ->
        "$sourceId: ${failure.reason.name} - ${failure.detail}"
    }
    return NutritionResearchException(
        reason = reported.reason,
        itemName = reported.itemName,
        itemIndex = reported.itemIndex,
        detail = "$reportedId: ${reported.detail}" +
            others.takeIf(String::isNotEmpty)?.let { " (other sources: $it)" }.orEmpty(),
    )
}

private fun GeminiNutritionItem.toAnalyzedItem(
    parsed: ParsedFoodItem,
    primary: ExaNutritionDocument,
    supporting: List<ExaNutritionDocument>,
): AnalyzedFoodItem = AnalyzedFoodItem(
    name = name,
    brand = brand,
    quantity = parsed.quantity
        ?: throw AiValidationException("The parsed logged quantity is missing"),
    unit = parsed.unit?.takeIf(String::isNotBlank)
        ?: throw AiValidationException("The parsed logged unit is missing"),
    gramsEquivalent = parsed.gramsEquivalent ?: loggedServingGramsEquivalent,
    resolvedVolumeMl = parsed.resolvedVolumeMl ?: resolvedVolumeMl,
    sourceUnit = sourceUnit,
    sourceUnitWeightGrams = sourceUnitWeightGrams,
    sourceUnitVolumeMl = sourceUnitVolumeMl,
    resolutionSource = if (isEstimate) "estimated unit conversion" else primary.url,
    calories = calories,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    calorieExplanation = calorieExplanation,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    saturatedFatGrams = saturatedFatGrams,
    sodiumMilligrams = sodiumMilligrams,
    sourceName = primary.title,
    sourceUrl = primary.url,
    supportingSourceUrls = supporting.map(ExaNutritionDocument::url),
    sourceServingQuantity = sourceServingQuantity,
    sourceServingUnit = sourceServingUnit,
    sourceServingGramsEquivalent = sourceServingGramsEquivalent,
    nutritionBasis = nutritionBasis,
    sourceBasisText = sourceBasisText,
    sourceProductName = sourceProductName,
    sourceCountry = sourceCountry,
    sourcePackageQuantity = sourcePackageQuantity,
    sourcePackageUnit = sourcePackageUnit,
    isEstimate = isEstimate,
    uncertaintyPercent = uncertaintyPercent,
    confidence = confidence,
    assumptions = assumptions,
    quantityResolution = parsed.quantityResolution,
)

private fun requireNutritionEvidence(
    item: GeminiNutritionItem,
    parsed: ParsedFoodItem,
    document: ExaNutritionDocument,
) {
    val corpus = document.title + "\n" + document.content
    requireGroundedNutritionBasis(item, parsed, corpus)
    requireEntityEvidence(item, parsed, corpus)
    if (!NUTRITION_WORDS.containsMatchIn(corpus)) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.NO_SUITABLE_SOURCE,
            itemName = parsed.name,
            detail = "The selected Exa source contains no recognizable nutrition evidence",
        )
    }
    val values = document.evidenceValues()
    item.loggedServingGramsEquivalent?.let { loggedGrams ->
        if (!values.supportsLoggedWeight(loggedGrams, parsed)) {
            throw NutritionResearchException(
                reason = NutritionFailureReason.MISSING_PORTION_WEIGHT,
                itemName = parsed.name,
                detail = "The selected Exa source does not support Gemini's weight per logged piece",
            )
        }
    }
    listOf(item.sourceUnitWeightGrams to "g", item.sourceUnitVolumeMl to "ml").forEach { (amount, unit) ->
        if (amount != null && !values.matches(amount, unit)) {
            throw NutritionResearchException(
                reason = NutritionFailureReason.MISSING_PORTION_WEIGHT,
                itemName = parsed.name,
                detail = "The product evidence does not support the declared unit conversion",
            )
        }
    }
    item.resolvedVolumeMl?.let { volume ->
        if (!values.supportsResolvedVolume(volume, parsed)) {
            throw NutritionResearchException(
                reason = NutritionFailureReason.MISSING_PORTION_WEIGHT,
                itemName = parsed.name,
                detail = "The product evidence does not support the declared volume",
            )
        }
    }
    if (!values.matches(item.calories, "kcal")) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES,
            itemName = parsed.name,
            detail = "The selected Exa source does not support Gemini's calorie value",
        )
    }
    val supportedMacros = listOf(item.proteinGrams, item.carbohydrateGrams, item.fatGrams)
        .count { values.matches(it, "g") }
    val allZero = item.calories == 0.0 && item.proteinGrams == 0.0 &&
        item.carbohydrateGrams == 0.0 && item.fatGrams == 0.0
    if ((!allZero && supportedMacros < 2) ||
        (allZero && !ZERO_CALORIE_EVIDENCE.containsMatchIn(corpus))
    ) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES,
            itemName = parsed.name,
            detail = "The selected Exa source does not support Gemini's macro values",
        )
    }
}

private fun ExaNutritionDocument.evidenceValues(): List<EvidenceValue> =
    NUTRITION_VALUE.findAll(title + "\n" + content).mapNotNull { match ->
        match.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { value ->
            EvidenceValue(value, match.groupValues[2].lowercase(Locale.ROOT))
        }
    }.toList()

/** Whether the page prints the logged count's total weight, or what one of them weighs. */
private fun List<EvidenceValue>.supportsLoggedWeight(grams: Double, parsed: ParsedFoodItem): Boolean {
    val perLoggedPiece = parsed.quantity?.takeIf { it.isFinite() && it > 0.0 }?.let { grams / it }
    return matches(grams, "g") || (perLoggedPiece != null && matches(perLoggedPiece, "g"))
}

private fun List<EvidenceValue>.supportsResolvedVolume(volume: Double, parsed: ParsedFoodItem): Boolean {
    if (parsed.resolvedVolumeMl != null) return true
    val perUnit = parsed.quantity?.takeIf { it > 0.0 }?.let { volume / it }
    return matches(volume, "ml") || (perUnit != null && matches(perUnit, "ml"))
}

/** The same reading, keeping only the unit weights and volumes this document prints. */
private fun GeminiNutritionItem.keepingBridgesPrintedIn(
    parsed: ParsedFoodItem,
    document: ExaNutritionDocument,
): GeminiNutritionItem {
    val values = document.evidenceValues()
    return copy(
        loggedServingGramsEquivalent = loggedServingGramsEquivalent
            ?.takeIf { values.supportsLoggedWeight(it, parsed) },
        sourceUnitWeightGrams = sourceUnitWeightGrams?.takeIf { values.matches(it, "g") },
        sourceUnitVolumeMl = sourceUnitVolumeMl?.takeIf { values.matches(it, "ml") },
        resolvedVolumeMl = resolvedVolumeMl?.takeIf { values.supportsResolvedVolume(it, parsed) },
    )
}

/**
 * A serving printed as the food itself - "pro Hamburger", "per burger", "1 Big Mac" - counts the
 * same thing the user counted when they logged pieces of that food. Carried as free text, such a
 * unit shared no dimension with "piece", and a reading that needed no weight failed for the lack
 * of one. Serving words keep their meaning: a portion, pack or bar is never taken for a piece.
 */
internal fun GeminiNutritionItem.withFoodNamedServingAsLoggedUnit(
    parsed: ParsedFoodItem,
): GeminiNutritionItem {
    if (nutritionBasis != ResearchNutritionBasis.SOURCE_SERVING) return this
    val loggedUnit = parsed.unit?.trim()
        ?.takeIf { QuantityUnits.normalize(it) == "piece" } ?: return this
    if (QuantityUnits.isCount(sourceServingUnit)) return this
    val servingTokens = entityTokens(sourceServingUnit)
    if (servingTokens.isEmpty() || servingTokens.any { it.length < MIN_FOOD_SERVING_TOKEN }) {
        return this
    }
    val foodTokens = entityTokens(parsed.name) + entityTokens(sourceProductName.orEmpty())
    val namesTheFood = servingTokens.all { serving ->
        foodTokens.any { food -> food == serving || food.endsWith(serving) }
    }
    return if (namesTheFood) copy(sourceServingUnit = loggedUnit) else this
}

/**
 * Resolves a source's individual product after its identity, basis and nutrients are grounded.
 * A burger portion counts one burger; an explicitly sized can or bottle supplies volume for
 * one drink. Arbitrary servings and multi-item products must still provide their own bridge.
 */
private fun GeminiNutritionItem.withSingleProductServing(
    parsed: ParsedFoodItem,
    document: ExaNutritionDocument,
): GeminiNutritionItem {
    val loggedUnit = parsed.unit ?: return this
    val unit = QuantityUnits.normalize(loggedUnit)
    if (unit !in setOf("piece", "can", "bottle")) return this
    val identity = (parsed.name + " " + sourceProductName.orEmpty()).normalizedBasisEvidence()
    val basis = sourceBasisText.orEmpty().normalizedBasisEvidence()
    if (MULTI_ITEM_PRODUCT.containsMatchIn(identity + " " + basis)) return this

    val corpus = (document.title + "\n" + document.content).normalizedBasisEvidence()
    if (unit == "piece" && SINGLE_PRODUCT_FOOD.containsMatchIn(identity)) {
        if (nutritionBasis == ResearchNutritionBasis.SOURCE_SERVING &&
            sourceServingQuantity == 1.0 && QuantityUnits.normalize(sourceServingUnit) == "serving"
        ) {
            return copy(sourceServingUnit = loggedUnit)
        }
        if (nutritionBasis == ResearchNutritionBasis.PER_100_G && loggedServingGramsEquivalent == null) {
            val weight = SINGLE_PORTION_GRAMS.findAll(corpus)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it > 0.0 }.distinct().singleOrNull()
            if (weight != null && parsed.quantity != null) {
                return copy(loggedServingGramsEquivalent = weight * parsed.quantity)
            }
        }
    }

    // Bind volume to a named container, never to an arbitrary ml number elsewhere in the table.
    // Several sizes on the same page remain ambiguous unless the quoted serving selects one.
    fun sizes(text: String): List<Pair<String, Double>> = (
        CONTAINER_VOLUME.findAll(text).map { it.groupValues[1] to it.groupValues[2] } +
            VOLUME_CONTAINER.findAll(text).map { it.groupValues[2] to it.groupValues[1] }
        ).mapNotNull { (container, amount) ->
            amount.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { QuantityUnits.normalize(container) to it }
        }.filter { unit == "piece" || it.first == unit }.distinct().toList()
    val selected = sizes(basis).singleOrNull() ?: sizes(corpus).singleOrNull() ?: return this
    val count = parsed.quantity ?: return this
    val volume = selected.second * count
    if (nutritionBasis == ResearchNutritionBasis.PER_100_ML ||
        (nutritionBasis == ResearchNutritionBasis.SOURCE_SERVING &&
            QuantityUnits.normalize(sourceServingUnit) == "ml")
    ) {
        return copy(resolvedVolumeMl = parsed.resolvedVolumeMl ?: resolvedVolumeMl ?: volume)
    }
    if (nutritionBasis == ResearchNutritionBasis.SOURCE_SERVING && sourceServingQuantity == 1.0 &&
        QuantityUnits.normalize(sourceServingUnit) in setOf(selected.first, "serving") &&
        (sizes(basis).singleOrNull() == selected ||
            QuantityUnits.normalize(sourceServingUnit) == selected.first)
    ) {
        return copy(sourceServingUnit = loggedUnit, resolvedVolumeMl = parsed.resolvedVolumeMl ?: resolvedVolumeMl ?: volume)
    }
    return this
}

private val SINGLE_PORTION_GRAMS = Regex(
    "\\b(?:pro|per|je)\\s+(?:portion|serving)\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*g\\s*\\)",
)
private val SINGLE_PRODUCT_FOOD = Regex("\\b(?:[\\p{L}]*burger|sandwich|wrap|croissant)\\b")
private val MULTI_ITEM_PRODUCT = Regex(
    "\\b(?:pack|multipack|packung|menu|menü|meal|box|bundle|nuggets)\\b|" +
        "\\b(?:[2-9]|[1-9]\\d+)\\s*[-x×]?\\s*(?:piece|stück|stueck|pack|can|dose|bottle|flasche|[\\p{L}]*burger|sandwich|wrap|croissant)",
)
private val CONTAINER_VOLUME = Regex(
    "\\b(can|dose|bottle|flasche)\\s*(?:contains|enthält|of|à|:)?\\s*\\(?\\s*(\\d+(?:\\.\\d+)?)\\s*ml\\b",
)

private val VOLUME_CONTAINER = Regex(
    "\\b(\\d+(?:\\.\\d+)?)\\s*ml\\s*[-–]?\\s*(can|dose|bottle|flasche)\\b",
)

private const val MIN_FOOD_SERVING_TOKEN = 3

/**
 * Reads a counted serving's weight from the basis quote when the model left it out.
 *
 * "pro Portion (105 g)" states both the serving and what it weighs, and the quote itself is
 * bound to the page before anything is accepted. Only a single printed gram amount is read,
 * after the per-100 column is set aside, so a quote that also carries nutrient values in grams
 * is left alone rather than guessed at. The quote weighs one serving, so it is only read for a
 * declared serving of exactly one.
 */
internal fun GeminiNutritionItem.withServingWeightFromBasisQuote(): GeminiNutritionItem {
    if (nutritionBasis != ResearchNutritionBasis.SOURCE_SERVING) return this
    if (sourceServingGramsEquivalent != null || sourceServingQuantity != 1.0) return this
    if (!QuantityUnits.isCount(sourceServingUnit)) return this
    val quote = sourceBasisText?.normalizedBasisEvidence()
        ?.replace(PER_100_G_BASIS, " ")
        ?.replace(PER_100_ML_BASIS, " ")
        ?: return this
    val grams = PRINTED_GRAMS.findAll(quote)
        .mapNotNull { it.groupValues[1].toDoubleOrNull() }
        .distinct()
        .singleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: return this
    return copy(sourceServingGramsEquivalent = grams)
}

private val PRINTED_GRAMS = Regex("(\\d+(?:\\.\\d+)?)\\s*g\\b")

/**
 * Binds the model's serving-basis classification to exact retrieved page text. Nutrient values
 * were already grounded, but without this check a model could pair a real per-100 table with the
 * user's 400 g request and make correct arithmetic operate on the wrong semantic basis.
 */
private fun requireGroundedNutritionBasis(
    item: GeminiNutritionItem,
    parsed: ParsedFoodItem,
    corpus: String,
) {
    val basisText = item.sourceBasisText?.trim()?.takeIf(String::isNotBlank)
    if (basisText == null) {
        if (!item.isEstimate) {
            throw NutritionResearchException(
                reason = NutritionFailureReason.INVALID_NUTRITION_BASIS,
                itemName = parsed.name,
                detail = "Verified nutrition is missing its source basis text",
            )
        }
        return
    }
    if (!corpus.containsBasisQuote(basisText)) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.INVALID_NUTRITION_BASIS,
            itemName = parsed.name,
            detail = "Gemini's nutrition basis text does not occur in the selected Exa source",
        )
    }
    if (!item.basisTextAgreesWithDeclaredBasis()) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.INVALID_NUTRITION_BASIS,
            itemName = parsed.name,
            detail = if (item.nutritionBasis == ResearchNutritionBasis.SOURCE_SERVING) {
                "Per-100 nutrition was mislabeled as a complete source serving"
            } else {
                "Gemini's nutrition basis classification disagrees with its source text"
            },
        )
    }
}

/**
 * Whether the model's own basis text says the same thing as the basis it declared.
 *
 * This is the reading's internal consistency, separate from whether that text was found in the
 * evidence. A reading that quotes "per 100 g" and then declares the whole logged portion as its
 * serving has contradicted itself, and no estimate label makes that safe to publish: the numbers
 * would be republished as a total for an amount they never described.
 */
private fun GeminiNutritionItem.basisTextAgreesWithDeclaredBasis(): Boolean {
    val basisText = sourceBasisText?.trim()?.takeIf(String::isNotBlank)
        ?.normalizedBasisEvidence() ?: return true
    return when (nutritionBasis) {
        ResearchNutritionBasis.PER_100_G -> PER_100_G_BASIS.containsMatchIn(basisText)
        ResearchNutritionBasis.PER_100_ML -> PER_100_ML_BASIS.containsMatchIn(basisText)
        ResearchNutritionBasis.SOURCE_SERVING -> {
            val namesPer100 = PER_100_G_BASIS.containsMatchIn(basisText) ||
                PER_100_ML_BASIS.containsMatchIn(basisText)
            // Nutrition tables commonly head two columns at once - "pro 100 g | pro Portion
            // (119 g)" - and a model quoting the whole heading has not mislabeled anything. It
            // agrees with a serving basis when, once the per-100 part is set aside, the quote
            // still names a serving and the declared serving is not itself 100 g or 100 ml.
            // "per 100 g" on its own is still rejected: that is the mislabel this check exists for.
            !namesPer100 || (
                SERVING_BASIS.containsMatchIn(
                    basisText.replace(PER_100_G_BASIS, " ").replace(PER_100_ML_BASIS, " "),
                ) && !declaresHundredUnitServing()
            )
        }
    }
}

private fun GeminiNutritionItem.declaresHundredUnitServing(): Boolean {
    val unit = sourceServingUnit?.trim()?.lowercase(Locale.ROOT) ?: return false
    return sourceServingQuantity == 100.0 && unit in setOf("g", "gram", "grams", "ml")
}

/**
 * Whether the model's basis quote appears in the source.
 *
 * The quote is required to be verbatim, and it still is - character for character - but web
 * pages reach Nomi as extracted text: table cells joined by pipes, headings broken across lines,
 * "Portion(119g)" printed without the spaces the model writes back. Comparing only letters and
 * digits keeps every word and number of the quote bound to the page while no longer failing a
 * correct quote over layout. Very short quotes must still match in the whitespace-normalized
 * form, where a two-letter fragment cannot turn up by accident inside unrelated words.
 */
private fun String.containsBasisQuote(quote: String): Boolean {
    val normalizedQuote = quote.normalizedBasisEvidence()
    if (normalizedBasisEvidence().contains(normalizedQuote)) return true
    val compactQuote = normalizedQuote.compactBasisEvidence()
    if (compactQuote.length < MIN_COMPACT_BASIS_QUOTE) return false
    return normalizedBasisEvidence().compactBasisEvidence().contains(compactQuote)
}

private fun String.compactBasisEvidence(): String = replace(Regex("[^\\p{L}\\p{N}.]"), "")

private const val MIN_COMPACT_BASIS_QUOTE = 6

/** Words and printed amounts that name one serving rather than a per-100 reference. */
private val SERVING_BASIS = Regex(
    "(?:portion|serving|stück|stueck|piece|pièce|porción|porcion|porzione|portie|porção|" +
        "porcao|racion|ración|each|item|pro stk|per stk)" +
        "|\\(\\s*\\d+(?:\\.\\d+)?\\s*(?:g|ml)\\s*\\)",
)

private fun String.normalizedBasisEvidence(): String = lowercase(Locale.ROOT)
    .replace('\u00a0', ' ')
    .replace(',', '.')
    .replace(Regex("(\\d+)\\.0+\\b"), "$1")
    .replace(Regex("\\s+"), " ")
    .trim()

private val PER_100_G_BASIS = Regex("(?:(?:per|pro|je|pour|por|/)\\s*)?100(?:\\.0+)?\\s*g\\b")
private val PER_100_ML_BASIS = Regex("(?:(?:per|pro|je|pour|por|/)\\s*)?100(?:\\.0+)?\\s*ml\\b")

private fun requireEntityEvidence(
    item: GeminiNutritionItem,
    parsed: ParsedFoodItem,
    corpus: String,
) {
    val claimedProduct = item.sourceProductName?.trim()?.takeIf(String::isNotBlank)
        ?: throw NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "Gemini did not identify the product printed by the selected source",
        )
    val normalizedCorpus = corpus.lowercase(Locale.ROOT)
    val productTokens = entityTokens(claimedProduct)
    if (productTokens.isEmpty()) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "The selected Exa source does not identify the claimed product",
        )
    }
    val requiredProductMatches = minOf(2, productTokens.size)
    if (productTokens.count(normalizedCorpus::contains) < requiredProductMatches) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "The selected Exa source does not support the claimed product",
        )
    }
    if (!normalizedCorpus.describesRequestedFood(parsed)) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "The selected Exa source does not support the requested product",
        )
    }
    val brandTokens = entityTokens((parsed.brand ?: item.brand).orEmpty())
    if (brandTokens.isNotEmpty() && brandTokens.none(normalizedCorpus::contains)) {
        throw NutritionResearchException(
            reason = NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
            itemName = parsed.name,
            detail = "The selected Exa source does not support the claimed brand",
        )
    }
}

/**
 * Whether a page is about the food that was actually requested.
 *
 * Part of a food name identifies it and the rest describes it. "Chicken breast, grilled" and
 * "cooked rice" are the same foods as "chicken breast" and "rice", and a nutrition page is under
 * no obligation to repeat the preparation word. Which part is the head depends on the language -
 * German puts it first and English last, and it is not reliably the longest word either - so the
 * rule is a plain majority: at least half of the request's words have to appear.
 *
 * That is deliberately weaker than the two-token rule it replaces, which rejected ordinary
 * requests outright. It is not the only thing binding a reading to its evidence: on the verified
 * path the document must also print the claimed product title and the exact calorie and macro
 * figures, and a request that names a brand must match that brand too. This check only has to
 * establish that research answered the question that was asked.
 */
private fun String.describesRequestedFood(parsed: ParsedFoodItem): Boolean {
    val tokens = entityTokens(parsed.name)
    if (tokens.isEmpty()) return true
    return tokens.count(::contains) * 2 >= tokens.size
}

private fun entityTokens(value: String): List<String> = ENTITY_TOKEN
    .findAll(value.lowercase(Locale.ROOT))
    .map(MatchResult::value)
    .filterNot(ENTITY_STOP_WORDS::contains)
    .distinct()
    .toList()

/**
 * Decides whether an ungrounded reading may still be offered as a generic-food estimate.
 *
 * A generic food ("steak", "rice", "banana") has no manufacturer, package or barcode to verify
 * against, so research legitimately settles on a reputable generic figure whose exact digits
 * appear in no single retrieved excerpt. Refusing those outright leaves ordinary foods
 * unloggable, so they are accepted as an explicit estimate instead.
 *
 * The strictness is kept where it means something. Whether a request is generic is decided by the
 * request and by what the reading claims to have read off a package - never by the brand field.
 * A request that names a brand, or a reading that reports a package size, still needs one document
 * supporting the whole reading, so a branded or packaged product can never be answered with a
 * generic number. The model's own brand string is ignored here, because a reading that could not
 * be verified has no standing to assert a product identity, and consulting it let a model that
 * wrote "Generic" into that field block the very path this exists for.
 *
 * The reading must also be one Nomi can scale deterministically, and it must not contradict
 * itself: an exact per-100 of its own unit, or a source serving with a real amount and unit, with
 * a basis text that agrees with the declared basis. That keeps a whole serving from entering
 * through the per-100 door, and a per-100 table from being republished as a whole portion, while
 * still allowing a per-serving generic table. Finally at least one retrieved document has to be a
 * nutrition page about the requested food, so an unsupported product claim cannot slip through as
 * a generic estimate.
 */
private fun GeminiNutritionItem.qualifiesAsGenericEstimate(
    parsed: ParsedFoodItem,
    documents: List<ExaNutritionDocument>,
): Boolean {
    if (!parsed.brand.isNullOrBlank()) return false
    if (sourcePackageQuantity != null || !sourcePackageUnit.isNullOrBlank()) return false
    if (!basisTextAgreesWithDeclaredBasis()) return false
    val basisIsScalable = when (nutritionBasis) {
        ResearchNutritionBasis.PER_100_G ->
            sourceServingUnit.trim().equals("g", ignoreCase = true) &&
                abs(sourceServingQuantity - 100.0) <= 1e-6
        ResearchNutritionBasis.PER_100_ML ->
            sourceServingUnit.trim().equals("ml", ignoreCase = true) &&
                abs(sourceServingQuantity - 100.0) <= 1e-6
        ResearchNutritionBasis.SOURCE_SERVING ->
            sourceServingQuantity.isFinite() && sourceServingQuantity > 0.0 &&
                sourceServingUnit.isNotBlank()
    }
    if (!basisIsScalable) return false
    if (!calories.isFinite() || calories <= 0.0) return false
    if (listOf(proteinGrams, carbohydrateGrams, fatGrams).any { !it.isFinite() || it < 0.0 }) {
        return false
    }
    // Research must at least have retrieved a nutrition page about the food that was requested.
    // Without one, nothing connects the reading to what the user logged, and accepting it would
    // launder an unsupported product claim into a generic number.
    return documents.any { it.supportsRequestedIdentity(parsed) && it.containsNutritionTable() }
}

private fun ExaNutritionDocument.isOfficialBrandDocument(brand: String?): Boolean {
    val claimedBrand = brand?.trim()?.takeIf(String::isNotBlank) ?: return false
    val host = runCatching { URI(url).host }.getOrNull() ?: return false
    val domainKey = foldForSourceMatch(host.substringBeforeLast('.'))
        .replace(Regex("[^a-z0-9]"), "")
    return ENTITY_TOKEN.findAll(foldForSourceMatch(claimedBrand))
        .map(MatchResult::value)
        .filter { it.length >= 4 && it !in BRAND_DOMAIN_STOP_WORDS }
        .any(domainKey::contains)
}

private fun ExaNutritionDocument.containsNutritionTable(): Boolean {
    val corpus = title + "\n" + content
    return NUTRITION_WORDS.containsMatchIn(corpus) &&
        NUTRITION_VALUE.findAll(corpus).take(2).count() >= 2
}

private fun ExaNutritionDocument.supportsRequestedIdentity(parsed: ParsedFoodItem): Boolean {
    val corpus = (title + "\n" + content).lowercase(Locale.ROOT)
    if (!corpus.describesRequestedFood(parsed)) return false
    val brandTokens = entityTokens(parsed.brand.orEmpty())
    return brandTokens.isEmpty() || brandTokens.any(corpus::contains)
}

private fun foldForSourceMatch(value: String): String = java.text.Normalizer
    .normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")

private fun equivalentServing(
    firstQuantity: Double,
    firstUnit: String,
    secondQuantity: Double,
    secondUnit: String,
): Boolean = firstUnit.trim().equals(secondUnit.trim(), ignoreCase = true) &&
    abs(firstQuantity - secondQuantity) <= maxOf(1e-6, abs(secondQuantity) * 1e-6)

private val BRAND_DOMAIN_STOP_WORDS = setOf(
    "brand", "company", "group", "foods", "food", "gmbh", "ltd", "inc",
)

private val ENTITY_TOKEN = Regex("[\\p{L}\\p{N}]{2,}")
private val ENTITY_STOP_WORDS = setOf(
    "and", "the", "with", "from", "official", "nutrition", "nutritional",
    "n?hrwerte", "naehrwerte", "original", "product", "produkt",
)
private data class EvidenceValue(val value: Double, val unit: String)

private fun List<EvidenceValue>.matches(expected: Double, unit: String): Boolean = any { evidence ->
    evidence.unit == unit && abs(evidence.value - expected) <= maxOf(0.2, abs(expected) * 0.015)
}

private val NUTRITION_WORDS = Regex(
    "(?i)nutrition|nutrient|n.hrwert|naehrwert|kcal|calories|kalorien|protein|eiwei|carbohydrate|kohlenhydrat|fat|fett",
)
private val NUTRITION_VALUE = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(kcal|ml|g|mg)\\b")
private val ZERO_CALORIE_EVIDENCE = Regex("(?i)0(?:[.,]0+)?\\s*kcal|zero[- ]calorie|kalorienfrei")
