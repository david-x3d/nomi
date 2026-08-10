package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.PortionEditInstruction
import com.nomi.app.ai.model.PortionOperation
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.domain.PortionChangeValidator

/**
 * Applies a portion change in app code, never in a model.
 *
 * Nutrition is linear in the amount eaten, so a portion change is multiplication and nothing
 * else. Asking a language model to redo the numbers is how "half a burrito" comes back as
 * something that is not half: it re-reads sources, picks different ones, and returns a
 * plausible number that no longer belongs to the food the user already accepted.
 *
 * For a research-normalized item the new values are recomputed from the stored per-100 basis
 * rather than from the currently displayed values. That is what keeps ten successive edits from
 * accumulating rounding: every edit measures from the original source, not from the last answer.
 */
object PortionEditApplier {

    data class Result(
        val item: AnalyzedFoodItem,
        val factor: Double,
        /** Plain-language record of what was applied, appended to the item's assumptions. */
        val description: String,
    )

    /**
     * Returns null when the instruction cannot be turned into a multiple of the current amount,
     * which happens when a replacement amount is in units the current amount cannot convert to
     * (grams against pieces, say). Research, not arithmetic, is the answer there.
     */
    fun apply(item: AnalyzedFoodItem, instruction: PortionEditInstruction): Result? {
        val usable = runCatching { instruction.requireUsable() }.getOrNull() ?: return null
        return when (usable.operation) {
            PortionOperation.SCALE -> {
                val factor = usable.factor ?: return null
                scaleBy(
                    item = item,
                    factor = factor,
                    newQuantity = item.quantity * factor,
                    newUnit = item.unit,
                    newGrams = item.gramsEquivalent?.times(factor),
                    description = "Portion scaled to ${formatFactor(factor)} of the logged amount.",
                )
            }

            PortionOperation.SET_QUANTITY -> {
                val quantity = usable.quantity ?: return null
                val unit = usable.unit ?: return null
                val factor = PortionChangeValidator.calculateMultiplier(
                    originalQuantity = item.quantity,
                    originalUnit = item.unit,
                    newQuantity = quantity,
                    newUnit = unit,
                ) ?: return null
                if (!factor.isFinite() || factor <= 0.0 || factor > PortionEditInstruction.MAX_SCALE_FACTOR) {
                    return null
                }
                scaleBy(
                    item = item,
                    factor = factor,
                    newQuantity = quantity,
                    newUnit = unit,
                    // A replacement stated in grams *is* the gram equivalent; otherwise the
                    // known weight travels with the same multiplier as everything else.
                    newGrams = quantity.takeIf { unit.equals("g", ignoreCase = true) }
                        ?: item.gramsEquivalent?.times(factor),
                    description = "Portion set to ${formatQuantity(quantity)} $unit.",
                )
            }
        }
    }

    private fun scaleBy(
        item: AnalyzedFoodItem,
        factor: Double,
        newQuantity: Double,
        newUnit: String,
        newGrams: Double?,
        description: String,
    ): Result? {
        if (!factor.isFinite() || factor <= 0.0) return null
        if (!newQuantity.isFinite() || newQuantity <= 0.0) return null

        val scaled = runCatching {
            if (item.requiresServingValidation) {
                // Recomputed from the validated per-100 basis, so this is exact no matter how
                // many edits came before it.
                ServingNutritionNormalizer.rescaleValidatedItemTo(
                    item = item,
                    loggedQuantity = newQuantity,
                    loggedUnit = newUnit,
                    loggedGramsEquivalent = newGrams,
                )
            } else {
                item.copy(
                    quantity = newQuantity,
                    unit = newUnit,
                    gramsEquivalent = newGrams,
                    calories = item.calories * factor,
                    proteinGrams = item.proteinGrams * factor,
                    carbohydrateGrams = item.carbohydrateGrams * factor,
                    fatGrams = item.fatGrams * factor,
                    fiberGrams = item.fiberGrams?.times(factor),
                    sugarGrams = item.sugarGrams?.times(factor),
                    saturatedFatGrams = item.saturatedFatGrams?.times(factor),
                    sodiumMilligrams = item.sodiumMilligrams?.times(factor),
                )
            }
        }.getOrNull() ?: return null

        return Result(
            item = scaled.copy(
                assumptions = (scaled.assumptions + description).takeLast(12),
            ),
            factor = factor,
            description = description,
        )
    }

    private fun formatFactor(factor: Double): String = when {
        factor == 0.5 -> "half"
        factor == 0.25 -> "a quarter"
        factor == 0.75 -> "three quarters"
        factor == 2.0 -> "double"
        else -> "${formatQuantity(factor * 100.0)}%"
    }

    private fun formatQuantity(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
