package com.nomi.app.ai.model

import kotlinx.serialization.Serializable

/**
 * What a user's correction actually asks for.
 *
 * The distinction decides how much a correction costs. Changing how much of the same food was
 * eaten is arithmetic Nomi can do itself; changing what the food is invalidates the researched
 * source and nothing but new research can fix it.
 */
@Serializable
enum class FoodEditType {
    /** Only the eaten amount changed. Deterministic scaling, no research. */
    PORTION_ONLY,

    /** The food, its ingredients, brand, or preparation changed. */
    CONTENT_CHANGE,

    /** Cannot be resolved without looking the food up again. */
    RESEARCH_REQUIRED,
    ;

    /** Both non-portion outcomes end in the same place: the food has to be researched again. */
    val needsResearch: Boolean
        get() = this != PORTION_ONLY
}

@Serializable
enum class PortionOperation {
    /** Multiply the current amount, as in "half" or "2x". */
    SCALE,

    /** Replace the amount outright, as in "200 g instead of 400 g". */
    SET_QUANTITY,
}

/**
 * A portion change expressed as arithmetic rather than as nutrition.
 *
 * This is deliberately the only thing a model is allowed to say about a portion edit. The
 * nutrient values are computed from it in Kotlin, so a model can misread the words but can
 * never hand back numbers that fail to be a clean multiple of the researched food.
 */
@Serializable
data class PortionEditInstruction(
    val operation: PortionOperation,
    /** Required for [PortionOperation.SCALE]. */
    val factor: Double? = null,
    /** Required for [PortionOperation.SET_QUANTITY]. */
    val quantity: Double? = null,
    val unit: String? = null,
) {
    /** Rejects an instruction that names an operation without the values that operation needs. */
    fun requireUsable(): PortionEditInstruction {
        when (operation) {
            PortionOperation.SCALE -> {
                val value = factor
                require(value != null && value.isFinite() && value > 0.0 && value <= MAX_SCALE_FACTOR) {
                    "A portion scale factor must be a finite value above zero"
                }
            }

            PortionOperation.SET_QUANTITY -> {
                val value = quantity
                require(value != null && value.isFinite() && value > 0.0) {
                    "A replacement portion amount must be a finite value above zero"
                }
                require(!unit.isNullOrBlank()) { "A replacement portion amount needs a unit" }
            }
        }
        return this
    }

    companion object {
        /** Beyond this a "portion change" is really a different meal, and is treated as one. */
        const val MAX_SCALE_FACTOR = 20.0

        fun scale(factor: Double) = PortionEditInstruction(PortionOperation.SCALE, factor = factor)

        fun setQuantity(quantity: Double, unit: String) =
            PortionEditInstruction(PortionOperation.SET_QUANTITY, quantity = quantity, unit = unit)
    }
}

/**
 * The cheap model's reading of a correction.
 *
 * [portion] is carried alongside the classification so a portion-only edit costs one small
 * request rather than two: the same call that decides the route also supplies the arithmetic
 * for it. It is absent whenever the edit needs research.
 */
@Serializable
data class FoodEditClassification(
    val type: FoodEditType,
    val confidence: Double? = null,
    val reason: String = "",
    val portion: PortionEditInstruction? = null,
) {
    companion object {
        /**
         * Below this, an edit is researched rather than scaled.
         *
         * The two mistakes are not equal. Researching something that only needed arithmetic
         * costs a web search; scaling something that actually changed food silently keeps the
         * wrong nutrition under a number the user now trusts more, because they corrected it.
         */
        const val MIN_PORTION_CONFIDENCE = 0.7
    }

    /** A portion route is taken only on a confident classification that carries the arithmetic. */
    fun portionInstructionOrNull(): PortionEditInstruction? {
        if (type != FoodEditType.PORTION_ONLY) return null
        if ((confidence ?: 1.0) < MIN_PORTION_CONFIDENCE) return null
        return portion?.runCatching { requireUsable() }?.getOrNull()
    }
}
