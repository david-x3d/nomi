package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import java.net.URI
import java.util.Locale

/**
 * Decides each item's [NutritionVerificationStatus] deterministically in app code.
 *
 * Provider output never gets to assert VERIFIED about itself. The displayed source domain is
 * always derived from the actually cited [AnalyzedFoodItem.sourceUrl], so the UI can never show
 * one source while the data came from another. A provider-claimed domain that contradicts the
 * cited URL downgrades the item instead of being displayed.
 */
object SourceIntegrityVerifier {
    fun resolve(analysis: FoodAnalysis): FoodAnalysis =
        analysis.copy(items = analysis.items.map(::resolveItem))

    fun resolveItem(item: AnalyzedFoodItem): AnalyzedFoodItem {
        val citedHost = hostOrNull(item.sourceUrl)
        val claimedDomain = item.sourceDomain?.trim()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
        val domainConflict = citedHost != null && claimedDomain != null && claimedDomain != citedHost

        val status = when {
            citedHost == null -> NutritionVerificationStatus.UNKNOWN
            domainConflict -> NutritionVerificationStatus.ESTIMATED
            item.isEstimate -> NutritionVerificationStatus.ESTIMATED
            item.sourceProductName.isNullOrBlank() -> NutritionVerificationStatus.ESTIMATED
            else -> NutritionVerificationStatus.VERIFIED
        }
        val conflictNote =
            "Provider-claimed source domain '$claimedDomain' does not match the cited page " +
                "'$citedHost'; the result is treated as an estimate."
        return item.copy(
            sourceDomain = citedHost,
            verificationStatus = status,
            isEstimate = item.isEstimate || status != NutritionVerificationStatus.VERIFIED,
            assumptions = if (domainConflict) {
                (item.assumptions + conflictNote).takeLast(12)
            } else {
                item.assumptions
            },
        )
    }

    private fun hostOrNull(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url) }.getOrNull()
            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?.host
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
    }
}
