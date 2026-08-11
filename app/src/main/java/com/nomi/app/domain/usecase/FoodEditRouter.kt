package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.PortionContext

/**
 * Decides how much a correction is allowed to cost.
 *
 * Three tiers in increasing price. Most corrections people type are arithmetic in words —
 * "half", "2x", "200 g" — and [PortionEditParser] settles those on device, so the common case
 * costs nothing and returns instantly. Wording the parser will not guess at goes to a cheap
 * classifier. Only a correction that genuinely changes what the food *is* reaches the research
 * model, because only then is the researched source actually wrong.
 *
 * Kept out of the ViewModel so the routing rules can be tested as rules, without a provider,
 * a database, or an Android runtime.
 */
class FoodEditRouter(
    private val classify: suspend (PortionContext, String) -> FoodEditClassification,
) {
    sealed interface Decision {
        /**
         * The amount changed. [result] is already computed, so no further model call happens
         * and the number shown is the number saved.
         */
        data class Scale(
            val result: PortionEditApplier.Result,
            val decidedBy: NutritionRoute.Decision,
            val classification: FoodEditClassification? = null,
        ) : Decision

        /** The food changed, or the classifier was not sure enough to let arithmetic stand in. */
        data class Research(
            val reason: String?,
            val classification: FoodEditClassification?,
        ) : Decision
    }

    suspend fun route(item: AnalyzedFoodItem, correction: String): Decision {
        require(correction.isNotBlank()) { "Describe what should change" }

        PortionEditParser.parseAgainstCurrentOrNull(
            correction = correction,
            currentQuantity = item.quantity,
            currentUnit = item.unit,
        )
            ?.let { PortionEditApplier.apply(item, it) }
            ?.let { return Decision.Scale(it, NutritionRoute.Decision.LOCAL) }

        val classification = classify(item.toPortionContext(), correction)
        val applied = classification.portionInstructionOrNull()
            ?.let { PortionEditApplier.apply(item, it) }

        return if (applied != null) {
            Decision.Scale(applied, NutritionRoute.Decision.CLASSIFIER, classification)
        } else {
            Decision.Research(classification.reason.takeIf(String::isNotBlank), classification)
        }
    }
}

/**
 * The slice of a researched item a portion decision needs. Deliberately excludes source and
 * citation fields: the classifier is choosing a route, and has no business seeing, or being
 * able to influence, where the nutrition came from.
 */
fun AnalyzedFoodItem.toPortionContext(): PortionContext = PortionContext(
    name = name,
    currentQuantity = quantity,
    currentUnit = unit,
    currentGrams = gramsEquivalent,
    calories = calories,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
)
