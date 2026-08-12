package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.FoodAnalysis
import java.util.Locale

internal const val FOOD_RESEARCH_CACHE_TTL_MILLIS = 21L * 24L * 60L * 60L * 1_000L

internal fun FoodAnalysis.canPersistForResearchReuse(): Boolean =
    items.isNotEmpty() && items.all { item -> !item.isEstimate && !item.sourceUrl.isNullOrBlank() }

internal fun foodResearchExpiry(storedAtEpochMillis: Long): Long =
    storedAtEpochMillis + FOOD_RESEARCH_CACHE_TTL_MILLIS

data class FoodAnalysisCacheKey(
    val normalizedInput: String,
    val localeCountry: String,
    val interpretationProviderIdentity: String,
    val researchProviderIdentity: String,
) {
    /** Stable Room primary key; the separators cannot occur in normalized UI/provider fields. */
    fun storageKey(): String = listOf(
        normalizedInput,
        localeCountry,
        interpretationProviderIdentity,
        researchProviderIdentity,
    ).joinToString("\u001f")

    companion object {
        fun create(
            input: String,
            localeCountry: String?,
            interpretationProviderIdentity: String,
            researchProviderIdentity: String,
        ) = FoodAnalysisCacheKey(
            normalizedInput = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " "),
            localeCountry = localeCountry.orEmpty().trim().uppercase(Locale.ROOT),
            interpretationProviderIdentity = interpretationProviderIdentity,
            researchProviderIdentity = researchProviderIdentity,
        )
    }
}

/** Small session cache for exact retries/repeated foods. It never persists provider results. */
class RecentFoodAnalysisCache(
    private val maxEntries: Int = 8,
    private val ttlMillis: Long = 5 * 60 * 1_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val analysis: FoodAnalysis, val storedAtMillis: Long)

    private val entries = object : LinkedHashMap<FoodAnalysisCacheKey, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FoodAnalysisCacheKey, Entry>?): Boolean =
            size > maxEntries
    }

    init {
        require(maxEntries > 0) { "Cache capacity must be positive" }
        require(ttlMillis > 0) { "Cache TTL must be positive" }
    }

    @Synchronized
    fun get(key: FoodAnalysisCacheKey): FoodAnalysis? {
        val entry = entries[key] ?: return null
        if (nowMillis() - entry.storedAtMillis >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.analysis.immutableCopy()
    }

    @Synchronized
    fun put(key: FoodAnalysisCacheKey, analysis: FoodAnalysis) {
        entries[key] = Entry(analysis.immutableCopy(), nowMillis())
    }

    @Synchronized
    fun clear() = entries.clear()
}

private fun FoodAnalysis.immutableCopy(): FoodAnalysis = copy(
    items = items.map { item ->
        item.copy(
            assumptions = item.assumptions.toList(),
            supportingSourceUrls = item.supportingSourceUrls.toList(),
            servingValidation = item.servingValidation?.copy(),
            quantityResolution = item.quantityResolution?.copy(),
        )
    },
)
