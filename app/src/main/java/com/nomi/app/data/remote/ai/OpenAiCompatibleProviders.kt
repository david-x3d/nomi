package com.nomi.app.data.remote.ai

import android.util.Base64
import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.NutritionLabelReading
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.model.VisionFoodResult
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.provider.FoodEditClassificationProvider
import com.nomi.app.ai.provider.FoodParsingProvider
import com.nomi.app.ai.provider.NutritionLabelProvider
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.ai.provider.PortionAdjustmentProvider
import com.nomi.app.ai.provider.VisionFoodProvider
import com.nomi.app.ai.validation.AiResponseValidator
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.ai.validation.UserQuantityResolver
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OpenAiCompatibleProviders(
    private val client: OpenAiCompatibleClient,
    private val parsingConfig: AiProviderConfig,
    private val parsingCredential: () -> AiRuntimeCredential,
    private val nutritionConfig: AiProviderConfig,
    private val nutritionCredential: () -> AiRuntimeCredential,
    private val portionConfig: AiProviderConfig,
    private val portionCredential: () -> AiRuntimeCredential,
    private val visionConfig: AiProviderConfig,
    private val visionCredential: () -> AiRuntimeCredential,
    private val localeCountryProvider: () -> String? = { Locale.getDefault().country },
) : FoodParsingProvider,
    NutritionResearchProvider,
    PortionAdjustmentProvider,
    FoodEditClassificationProvider,
    NutritionLabelProvider,
    VisionFoodProvider {

    override suspend fun parseFood(text: String): ParsedFoodIntent {
        require(text.isNotBlank()) { "Enter what you ate first" }
        val raw = client.completeJson(
            config = parsingConfig,
            credential = parsingCredential(),
            systemPrompt = "You are Nomi's structured multilingual food parser.",
            userPrompt = AiPrompts.parseFood(text),
        )
        val providerIntent: ParsedFoodIntent = client.json.decodeFromString(raw)
        AiResponseValidator.validate(providerIntent)
        return AiResponseValidator.validate(
            UserQuantityResolver.reconcileParsedIntent(text, providerIntent, localeCountryProvider()),
        )
    }

    override suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis {
        val localeCountry = localeCountryProvider()
        val reconciledIntent = AiResponseValidator.validate(
            UserQuantityResolver.reconcileIntent(intent, localeCountry),
        )
        suspend fun research(prompt: String): FoodAnalysis {
            val completion = client.completeWebSearchJson(
                config = nutritionConfig,
                credential = nutritionCredential(),
                systemPrompt = "You compare multiple independent websites and report web-cited " +
                    "source-serving nutrition as validated JSON only; Nomi performs serving arithmetic.",
                userPrompt = prompt,
            )
            throwIfResearchRefusal(client.json, completion.content)
            val analysis: FoodAnalysis = client.json.decodeFromString(completion.content)
            val groundedAnalysis = groundWithWebSearchEvidence(
                analysis = analysis,
                evidenceUrls = completion.evidenceUrls,
                fetchedUrls = completion.fetchedUrls,
                requiresFetchedBrandedSource = completion.requiresFetchedBrandedSource,
            )
            return UserQuantityResolver.reconcileAnalysis(reconciledIntent, groundedAnalysis)
        }

        var reconciledAnalysis = research(
            AiPrompts.researchNutrition(reconciledIntent, client.json, localeCountry),
        )
        val unresolvedAmounts = unresolvedWebAmountItemIndexes(reconciledAnalysis)
        if (unresolvedAmounts.isNotEmpty()) {
            val amountResolution = research(
                AiPrompts.researchNutritionAmountResolution(
                    intent = reconciledIntent,
                    json = client.json,
                    localeCountry = localeCountry,
                    unresolvedItemIndexes = unresolvedAmounts,
                ),
            )
            reconciledAnalysis = mergeWebAmountResolution(
                primary = reconciledAnalysis,
                amountResolution = amountResolution,
                unresolvedItemIndexes = unresolvedAmounts.toSet(),
            )
        }
        val normalized = ServingNutritionNormalizer.normalize(reconciledIntent, reconciledAnalysis)
        return SourceIntegrityVerifier.resolve(rejectPlaceholderNutrition(normalized))
    }

    override suspend fun interpretAdjustment(
        current: PortionContext,
        userCorrection: String,
    ): PortionAdjustment {
        require(userCorrection.isNotBlank()) { "Describe what should change" }
        val raw = client.completeJson(
            config = portionConfig,
            credential = portionCredential(),
            systemPrompt = "You interpret portion corrections and never do nutrition arithmetic.",
            userPrompt = AiPrompts.adjustPortion(current, userCorrection, client.json),
        )
        val adjustment: PortionAdjustment = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(current, adjustment)
    }

    /**
     * Runs on the cheap portion model rather than the research model, which is the point: this
     * call exists to decide whether the expensive one is needed at all.
     */
    override suspend fun classifyEdit(
        current: PortionContext,
        userEdit: String,
    ): FoodEditClassification {
        require(userEdit.isNotBlank()) { "Describe what should change" }
        val raw = client.completeJson(
            config = portionConfig,
            credential = portionCredential(),
            systemPrompt = "You route food corrections and never produce nutrition values.",
            userPrompt = AiPrompts.classifyFoodEdit(current, userEdit, client.json),
        )
        val classification: FoodEditClassification = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(classification)
    }

    override suspend fun readNutritionLabel(
        imageBytes: ByteArray,
        mediaType: String,
    ): NutritionLabelReading {
        require(imageBytes.isNotEmpty()) { "The selected image is empty" }
        val raw = client.completeVisionJson(
            config = visionConfig,
            credential = visionCredential(),
            prompt = AiPrompts.readNutritionLabel(),
            base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
            mediaType = mediaType,
        )
        throwIfLabelUnreadable(client.json, raw)
        val reading: NutritionLabelReading = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(reading)
    }

    override suspend fun identifyFood(imageBytes: ByteArray, mediaType: String): VisionFoodResult {
        require(imageBytes.isNotEmpty()) { "The selected image is empty" }
        val raw = client.completeVisionJson(
            config = visionConfig,
            credential = visionCredential(),
            prompt = AiPrompts.identifyFoodFromPhoto(),
            base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
            mediaType = mediaType,
        )
        val visionResult: VisionFoodResult = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(visionResult)
    }
}

/**
 * Finds results that cannot yet bridge a source mass serving and a logged piece/portion (or the
 * reverse). The normalizer remains strict; this only decides when a second, targeted web search
 * can provide the missing product-specific weight.
 */
internal fun unresolvedWebAmountItemIndexes(analysis: FoodAnalysis): List<Int> =
    analysis.items.mapIndexedNotNull { index, item ->
        val sourceUnit = item.sourceServingUnit ?: return@mapIndexedNotNull null
        val sourceIsMass = isResearchMassUnit(sourceUnit)
        val loggedIsMass = isResearchMassUnit(item.unit)
        val needsLoggedMass = sourceIsMass && !loggedIsMass && item.gramsEquivalent == null
        val needsSourceMass = !sourceIsMass && loggedIsMass &&
            item.sourceServingGramsEquivalent == null
        index.takeIf { needsLoggedMass || needsSourceMass }
    }

/**
 * Keeps the first pass's nutrition table and source identity, importing only missing conversion
 * evidence from the targeted pass. This prevents a package-weight lookup from silently replacing
 * already researched nutrient values.
 */
internal fun mergeWebAmountResolution(
    primary: FoodAnalysis,
    amountResolution: FoodAnalysis,
    unresolvedItemIndexes: Set<Int>,
): FoodAnalysis {
    if (primary.items.size != amountResolution.items.size) {
        throw AiValidationException(
            "Amount-resolution research returned a different number of foods.",
        )
    }
    return primary.copy(
        items = primary.items.mapIndexed { index, item ->
            if (index !in unresolvedItemIndexes) return@mapIndexed item
            val resolved = amountResolution.items[index]
            val addedEvidence = buildList {
                resolved.sourceUrl?.let(::add)
                addAll(resolved.supportingSourceUrls)
            }
            item.copy(
                gramsEquivalent = item.gramsEquivalent
                    ?: resolved.gramsEquivalent.takeIfPositive(),
                sourceServingGramsEquivalent = item.sourceServingGramsEquivalent
                    ?: resolved.sourceServingGramsEquivalent.takeIfPositive(),
                sourcePackageQuantity = item.sourcePackageQuantity
                    ?: resolved.sourcePackageQuantity.takeIfPositive(),
                sourcePackageUnit = item.sourcePackageUnit
                    ?: resolved.sourcePackageUnit?.takeIf(String::isNotBlank),
                supportingSourceUrls = (item.supportingSourceUrls + addedEvidence)
                    .filterNot { it == item.sourceUrl }
                    .distinct()
                    .take(5),
                isEstimate = item.isEstimate || resolved.isEstimate,
                confidence = listOfNotNull(item.confidence, resolved.confidence).minOrNull(),
                assumptions = (item.assumptions + resolved.assumptions).distinct(),
            )
        },
    )
}

private fun Double?.takeIfPositive(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

private fun isResearchMassUnit(rawUnit: String): Boolean {
    val unit = rawUnit.trim().lowercase(Locale.ROOT).removeSuffix(".")
    return unit in setOf(
        "mg", "milligram", "milligrams", "milligramm",
        "g", "gram", "grams", "gramm",
        "kg", "kilogram", "kilograms", "kilogramm",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
    )
}

internal fun groundWithWebSearchEvidence(
    analysis: FoodAnalysis,
    evidenceUrls: Set<String>,
    fetchedUrls: Set<String> = emptySet(),
    requiresFetchedBrandedSource: Boolean = false,
): FoodAnalysis {
    val citationsBySite = linkedMapOf<String, String>()
    evidenceUrls.forEach { rawUrl ->
        val url = canonicalWebUrlOrNull(rawUrl) ?: return@forEach
        val site = canonicalResearchSite(url) ?: return@forEach
        citationsBySite.putIfAbsent(site, url)
    }
    if (citationsBySite.isEmpty()) {
        throw AiValidationException(
            "The food research provider returned no web-search citations. Try again or " +
                "configure Perplexity, OpenRouter, or OpenAI for Food research.",
        )
    }
    val fetchedSites = fetchedUrls.mapNotNull(::canonicalResearchSite).toSet()
    if (requiresFetchedBrandedSource) {
        val unfetchedBrandedItem = analysis.items.firstOrNull { item ->
            !item.brand.isNullOrBlank() &&
                canonicalClaimedResearchSite(item.sourceDomain) !in fetchedSites
        }
        if (unfetchedBrandedItem != null) {
            throw AiValidationException(
                "Branded food research must fetch the cited product page before using its nutrition.",
            )
        }
    }
    val fetchedOfficialSourceIsCanonical = citationsBySite.size == 1 &&
        analysis.items.singleOrNull()?.hasFetchedOfficialBrandSource(
            citationsBySite = citationsBySite,
            fetchedUrls = fetchedUrls,
        ) == true
    if (citationsBySite.size < 2 && !fetchedOfficialSourceIsCanonical) {
        throw AiValidationException(
            "Food research needs citations from at least two independent websites.",
        )
    }
    return analysis.copy(
        items = analysis.items.map { item ->
            val claimedSite = canonicalClaimedResearchSite(item.sourceDomain)
            val primaryEntry = citationsBySite.entries.firstOrNull { (site, _) ->
                site == claimedSite
            } ?: citationsBySite.entries.first()
            val primaryUrl = primaryEntry.value
            val supportingUrls = citationsBySite.entries.asSequence()
                .filter { (site, _) -> site != primaryEntry.key }
                .map { (_, url) -> url }
                .take(5)
                .toList()
            val primarySourceName = runCatching {
                URI(primaryUrl).host?.removePrefix("www.")
            }.getOrNull()?.takeIf(String::isNotBlank)
            item.copy(
                sourceName = item.sourceName?.takeIf(String::isNotBlank) ?: primarySourceName,
                sourceUrl = primaryUrl,
                supportingSourceUrls = supportingUrls,
            )
        },
    )
}

private fun AnalyzedFoodItem.hasFetchedOfficialBrandSource(
    citationsBySite: Map<String, String>,
    fetchedUrls: Set<String>,
): Boolean {
    val claimedBrand = brand?.trim()?.takeIf(String::isNotBlank) ?: return false
    val claimedSite = canonicalClaimedResearchSite(sourceDomain) ?: return false
    val fetchedSites = fetchedUrls.mapNotNull(::canonicalResearchSite).toSet()
    return !sourceProductName.isNullOrBlank() &&
        !isEstimate &&
        citationsBySite.containsKey(claimedSite) &&
        claimedSite in fetchedSites &&
        claimedSite.looksLikeOfficialBrandDomain(claimedBrand)
}

private fun String.looksLikeOfficialBrandDomain(brand: String): Boolean {
    val domainKey = foldForDomainMatch(substringBeforeLast('.'))
        .replace(Regex("[^a-z0-9]"), "")
    val ignored = setOf("brand", "company", "group", "foods", "food", "gmbh", "ltd", "inc")
    return Regex("[a-z0-9]+")
        .findAll(foldForDomainMatch(brand))
        .map { it.value }
        .filter { it.length >= 4 && it !in ignored }
        .any(domainKey::contains)
}

private fun foldForDomainMatch(value: String): String {
    val germanAscii = value.lowercase(Locale.ROOT)
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
    return Normalizer.normalize(germanAscii, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
}
private fun canonicalClaimedResearchSite(value: String?): String? {
    val host = value?.trim()?.lowercase(Locale.ROOT)?.removePrefix("www.")
        ?.takeIf(String::isNotBlank) ?: return null
    return runCatching { URI("https://$host").host }
        .getOrNull()
        ?.lowercase(Locale.ROOT)
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)
}

private fun canonicalResearchSite(url: String): String? = runCatching {
    URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
}.getOrNull()?.takeIf(String::isNotBlank)

@Serializable
private data class ResearchRefusal(val error: String? = null)

/**
 * An unreadable label is the correct answer for a bad photo, so it is reported as such instead
 * of being retried against a second provider: another model cannot read a blurred table either.
 */
internal fun throwIfLabelUnreadable(json: Json, content: String) {
    val refusal = runCatching { json.decodeFromString<ResearchRefusal>(content) }.getOrNull()
    val reason = refusal?.error?.trim()?.takeIf(String::isNotEmpty) ?: return
    throw AiValidationException(
        "The nutrition table couldn't be read: ${reason.take(200)}. " +
            "Photograph the table straight on, filling the frame, with the values in focus.",
    )
}

/** Turns the prompt's {"error": ...} escape hatch into a retryable failure. */
internal fun throwIfResearchRefusal(json: Json, content: String) {
    val refusal = runCatching { json.decodeFromString<ResearchRefusal>(content) }.getOrNull()
    val reason = refusal?.error?.trim()?.takeIf(String::isNotEmpty) ?: return
    throw AiValidationException(
        "Live web research could not verify nutrition data: ${reason.take(200)}. " +
            "Retry, or describe the food more precisely, for example a specific cut or brand.",
    )
}

/**
 * A researched food with zero calories and zero macros is a placeholder for "nothing found",
 * not data, unless the item is plausibly calorie-free. Failing here sends the request to the
 * fallback provider for a fresh web search instead of saving a fabricated 0 kcal entry.
 */
internal fun rejectPlaceholderNutrition(analysis: FoodAnalysis): FoodAnalysis {
    analysis.items.forEach { item ->
        val allZero = item.calories == 0.0 && item.proteinGrams == 0.0 &&
            item.carbohydrateGrams == 0.0 && item.fatGrams == 0.0
        if (allZero && !isPlausiblyCalorieFree(item)) {
            throw AiValidationException(
                "Live web research returned no usable nutrition data for '${item.name}'. " +
                    "Try again or rephrase the food.",
            )
        }
    }
    return analysis
}

private val CALORIE_FREE_HINTS = listOf(
    "wasser", "water", "mineral", "sprudel", "kaffee", "coffee", "espresso", "tee", "tea",
    "zero", "light", "diet", "diat",
)

private fun isPlausiblyCalorieFree(item: AnalyzedFoodItem): Boolean {
    val normalized = listOfNotNull(item.name, item.brand, item.sourceProductName)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('ü', 'u')
        .replace("ß", "ss")
    return CALORIE_FREE_HINTS.any(normalized::contains)
}
