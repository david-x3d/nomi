package com.nomi.app.ui.app

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.parsing.FoodNameCorrection
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.domain.usecase.acceptsVerifiedUpgradeFrom
import com.nomi.app.domain.usecase.isTrustedForNutritionReuse
import java.util.Locale

/**
 * The foods Nomi already knows on this phone, used to answer a lookup without a provider.
 *
 * [recentFoods] is the list the library screen is already observing; it is consulted first so a
 * food eaten every week is matched in memory, and the database is only asked for the rest.
 */
internal class LocalFoodCatalog(
    private val repository: NomiRepository,
    private val recentFoods: () -> List<FoodEntity>,
) {
    /**
     * Repairs a mistyped food against the ones already in the log.
     *
     * It runs before the cache lookup on purpose: a corrected spelling can hit the local
     * catalog exactly, so a typo in something eaten every week costs no provider request at
     * all. The correction is strict about variants - it will fix "junebrry" but never trade one
     * edition for another - and it leaves anything it is not sure about untouched for research.
     */
    fun withKnownSpellings(intent: ParsedFoodIntent): ParsedFoodIntent {
        val known = recentFoods().map(FoodEntity::canonicalName)
        if (known.isEmpty()) return intent
        return intent.copy(
            items = intent.items.map { item ->
                val corrected = FoodNameCorrection.correctedOrNull(item.name, known)
                if (corrected == null) item else item.copy(name = corrected)
            },
        )
    }

    /** Reuses an exact local catalog match before paying for another provider request. */
    suspend fun cachedAnalysis(intent: ParsedFoodIntent): FoodAnalysis? {
        val reconciled = UserQuantityResolver.reconcileIntent(intent, Locale.getDefault().country)
        if (reconciled.items.isEmpty()) return null

        val analyzed = mutableListOf<AnalyzedFoodItem>()
        for (requested in reconciled.items) {
            val quantity = requested.quantity ?: return null
            val unit = requested.unit?.takeIf(String::isNotBlank) ?: return null
            val normalizedName = requested.name.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
            val normalizedBrand = requested.brand?.trim()?.lowercase(Locale.ROOT)
            val food = recentFoods().firstOrNull {
                it.normalizedName == normalizedName &&
                    it.brand?.trim()?.lowercase(Locale.ROOT) == normalizedBrand
            } ?: repository.foodByIdentity(normalizedName, normalizedBrand) ?: return null
            if (!food.isTrustedForNutritionReuse()) return null
            val scaled = runCatching {
                ServingNutritionNormalizer.normalizeSourceServingTo(
                    sourceServingItem = food.toAnalyzedItem("Nomi local food cache"),
                    loggedQuantity = quantity,
                    loggedUnit = unit,
                    loggedGramsEquivalent = requested.gramsEquivalent,
                    loggedResolvedVolumeMl = requested.resolvedVolumeMl,
                ).copy(quantityResolution = requested.quantityResolution)
            }.getOrNull() ?: return null
            analyzed += scaled
        }
        return FoodAnalysis(items = analyzed, overallConfidence = 1.0)
    }
    /** Files a manual entry so the same food can be picked from the library next time. */
    suspend fun cache(log: FoodLogEntity): Long? = cache(
        AnalyzedFoodItem(
            name = log.displayNameSnapshot,
            brand = log.brandSnapshot,
            quantity = log.amount,
            unit = log.unit,
            gramsEquivalent = log.grams ?: log.amount.takeIf { log.unit.equals("g", true) },
            resolvedVolumeMl = log.resolvedVolumeMl,
            resolutionSource = log.resolutionSource,
            calories = log.nutritionSnapshot.caloriesKcal,
            proteinGrams = log.nutritionSnapshot.proteinGrams,
            carbohydrateGrams = log.nutritionSnapshot.carbohydrateGrams,
            fatGrams = log.nutritionSnapshot.fatGrams,
            fiberGrams = log.nutritionSnapshot.fiberGrams,
            sugarGrams = log.nutritionSnapshot.sugarGrams,
            saturatedFatGrams = log.nutritionSnapshot.saturatedFatGrams,
            sodiumMilligrams = log.nutritionSnapshot.sodiumMilligrams,
            sourceName = log.sourceSnapshot.displayName,
            sourceUrl = log.sourceSnapshot.url,
            isEstimate = log.isEstimated,
        ),
    )

    /**
     * Adds [item] to the catalogue as per-100 g values, or upgrades the row it matches, and returns
     * that row's id. Null when the item has no weight to derive a per-100 basis from.
     */
    suspend fun cache(item: AnalyzedFoodItem, barcode: String? = null): Long? {
        val grams = item.gramsEquivalent ?: item.quantity.takeIf { item.unit.equals("g", true) } ?: return null
        if (!grams.isFinite() || grams <= 0.0) return null
        val normalized = item.name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val normalizedBrand = item.brand?.trim()?.lowercase(Locale.ROOT)
        val existing = barcode?.let { repository.foodByBarcode(it) }
            ?: recentFoods().firstOrNull {
                it.normalizedName == normalized &&
                    it.brand?.trim()?.lowercase(Locale.ROOT) == normalizedBrand
            }
            ?: repository.foodByIdentity(normalized, normalizedBrand)
        val factor = 100.0 / grams
        val now = System.currentTimeMillis()
        val nutritionPer100 = NutritionValues(
            caloriesKcal = item.calories * factor,
            proteinGrams = item.proteinGrams * factor,
            carbohydrateGrams = item.carbohydrateGrams * factor,
            fatGrams = item.fatGrams * factor,
            fiberGrams = item.fiberGrams?.times(factor),
            sugarGrams = item.sugarGrams?.times(factor),
            saturatedFatGrams = item.saturatedFatGrams?.times(factor),
            sodiumMilligrams = item.sodiumMilligrams?.times(factor),
        )
        if (existing != null) {
            if (existing.acceptsVerifiedUpgradeFrom(item.isEstimate)) {
                repository.updateFood(
                    existing.copy(
                        canonicalName = item.name.trim().take(300),
                        normalizedName = normalized.take(300),
                        brand = item.brand?.trim()?.take(200),
                        barcode = barcode ?: existing.barcode,
                        nutritionPer100g = nutritionPer100,
                        isEstimated = false,
                        lastVerifiedAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            return existing.id
        }
        return repository.addFood(
            FoodEntity(
                canonicalName = item.name.trim().take(300),
                normalizedName = normalized.take(300),
                brand = item.brand?.trim()?.take(200),
                barcode = barcode,
                nutritionPer100g = nutritionPer100,
                isUserCreated = item.sourceName == MANUAL_SOURCE_NAME,
                isEstimated = item.isEstimate,
                lastVerifiedAtEpochMillis = now.takeUnless { item.isEstimate },
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }
}
