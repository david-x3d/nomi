package com.nomi.app.data.remote.ai

import android.util.Base64
import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.MenuScanResult
import com.nomi.app.ai.model.NutritionLabelReading
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.model.VisionFoodResult
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.provider.FoodEditClassificationProvider
import com.nomi.app.ai.provider.MenuVisionProvider
import com.nomi.app.ai.provider.FoodParsingProvider
import com.nomi.app.ai.provider.NutritionEstimateProvider
import com.nomi.app.ai.provider.NutritionLabelProvider
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.ai.provider.PortionAdjustmentProvider
import com.nomi.app.ai.provider.VisionFoodProvider
import com.nomi.app.ai.validation.AiResponseValidator
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.domain.calculator.CalorieBiasAdjuster
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    private val calorieBiasProvider: () -> CalorieEstimateBias = { CalorieEstimateBias.NONE },
) : FoodParsingProvider,
    NutritionResearchProvider,
    NutritionEstimateProvider,
    PortionAdjustmentProvider,
    FoodEditClassificationProvider,
    NutritionLabelProvider,
    VisionFoodProvider,
    MenuVisionProvider {

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

    /**
     * Sourced research first, a labeled estimate rather than an error second.
     *
     * The gates below research are deliberately strict, and every one of them used to end the
     * user's logging attempt. A person who ate something wants it in their journal, so a refusal
     * now falls through to an estimate that is plainly marked as one, and only a failure of that
     * too - no key, no network, no usable answer - surfaces the original research error.
     */
    override suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis {
        val localeCountry = localeCountryProvider()
        val reconciledIntent = AiResponseValidator.validate(
            UserQuantityResolver.reconcileIntent(intent, localeCountry),
        )
        return try {
            researchNutritionFromSources(reconciledIntent, localeCountry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (researchFailure: Throwable) {
            try {
                estimateReconciledNutrition(reconciledIntent, localeCountry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (estimateFailure: Throwable) {
                researchFailure.addSuppressed(estimateFailure)
                throw researchFailure
            }
        }
    }

    private suspend fun researchNutritionFromSources(
        reconciledIntent: ParsedFoodIntent,
        localeCountry: String?,
    ): FoodAnalysis {
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
            return UserQuantityResolver.reconcileAnalysis(
                reconciledIntent,
                withCalorieBias(groundedAnalysis),
            )
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

    /**
     * The fast path: one completion, no search, answered from model knowledge.
     *
     * This is what the text-logging flow calls first so a meal appears in a second or two
     * instead of after a web search. It is also what [researchNutrition] falls back to when
     * sourced research refuses, which is why it is public rather than private to that path.
     */
    override suspend fun estimateNutrition(intent: ParsedFoodIntent): FoodAnalysis {
        val localeCountry = localeCountryProvider()
        val reconciledIntent = AiResponseValidator.validate(
            UserQuantityResolver.reconcileIntent(intent, localeCountry),
        )
        return estimateReconciledNutrition(reconciledIntent, localeCountry)
    }

    /**
     * One plain completion, no search, no citation requirement. The serving arithmetic and the
     * plausibility checks still run, so an estimate cannot smuggle in impossible numbers - it
     * just no longer has to prove where it came from.
     */
    private suspend fun estimateReconciledNutrition(
        reconciledIntent: ParsedFoodIntent,
        localeCountry: String?,
    ): FoodAnalysis {
        val raw = client.completeJson(
            config = nutritionConfig,
            credential = nutritionCredential(),
            systemPrompt = "You estimate nutrition per 100 g or 100 ml as validated JSON only; " +
                "Nomi performs all serving arithmetic.",
            userPrompt = AiPrompts.estimateNutrition(reconciledIntent, client.json, localeCountry),
        )
        val analysis: FoodAnalysis = client.json.decodeFromString(raw)
        val labeled = analysis.copy(
            items = analysis.items.map { item ->
                item.copy(
                    isEstimate = true,
                    sourceUrl = null,
                    supportingSourceUrls = emptyList(),
                    sourceDomain = null,
                )
            },
        )
        val reconciled = UserQuantityResolver.reconcileAnalysis(
            reconciledIntent,
            withCalorieBias(labeled),
        )
        val normalized = ServingNutritionNormalizer.normalize(reconciledIntent, reconciled)
        return SourceIntegrityVerifier.resolve(rejectPlaceholderNutrition(normalized))
    }

    /**
     * Applied to the source-serving values, before the normalizer scales them to the logged
     * amount. Biasing afterwards would leave the stored per-100 basis disagreeing with the
     * stored total, and the save-time validation would reject the entry.
     */
    private fun withCalorieBias(analysis: FoodAnalysis): FoodAnalysis =
        CalorieBiasAdjuster.apply(analysis, calorieBiasProvider())

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

    override suspend fun scanMenu(imageBytes: ByteArray, mediaType: String): MenuScanResult {
        require(imageBytes.isNotEmpty()) { "The selected image is empty" }
        val raw = client.completeVisionJson(
            config = visionConfig,
            credential = visionCredential(),
            prompt = AiPrompts.readRestaurantMenu(),
            base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
            mediaType = mediaType,
            maxTokens = 8_192,
        )
        val result: MenuScanResult = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(result)
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
        // Nothing to attribute the values to, so they are kept as a plainly labeled estimate.
        return analysis.copy(items = analysis.items.map { it.copy(isEstimate = true) })
    }
    val fetchedSites = fetchedUrls.mapNotNull(::canonicalResearchSite).toSet()
    val fetchedOfficialSourceIsCanonical = citationsBySite.size == 1 &&
        analysis.items.singleOrNull()?.hasFetchedOfficialBrandSource(
            citationsBySite = citationsBySite,
            fetchedUrls = fetchedUrls,
        ) == true
    val singleSiteOnly = citationsBySite.size < 2 && !fetchedOfficialSourceIsCanonical
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
            // A branded product whose own page was never opened is one site's claim about
            // another site's product, which is exactly what "estimated" means here.
            val unfetchedBrandedSource = requiresFetchedBrandedSource &&
                !item.brand.isNullOrBlank() && claimedSite !in fetchedSites
            item.copy(
                sourceName = item.sourceName?.takeIf(String::isNotBlank) ?: primarySourceName,
                sourceUrl = primaryUrl,
                supportingSourceUrls = supportingUrls,
                isEstimate = item.isEstimate || singleSiteOnly || unfetchedBrandedSource,
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
