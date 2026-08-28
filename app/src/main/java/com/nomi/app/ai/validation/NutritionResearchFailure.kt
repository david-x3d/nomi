package com.nomi.app.ai.validation

/**
 * Why one logged item could not be given nutrition.
 *
 * Nutrition research fails for causes that need completely different answers from the user: a
 * rate-limited provider needs a retry, a missing piece weight needs an amount in grams, and a
 * mismatched product needs a better name. Collapsing them into one "could not verify nutrition"
 * message made every failure look the same and left nothing to act on, so the cause travels with
 * the failure instead of being flattened at the throw site.
 */
enum class NutritionFailureReason {
    /** Retrieval returned nothing usable, or the model cited a source that was never returned. */
    NO_SUITABLE_SOURCE,

    /** Evidence describes a different product than the one that was requested or claimed. */
    SOURCE_IDENTITY_MISMATCH,

    /** Evidence does not support the reported calories or macros. */
    UNSUPPORTED_NUTRITION_VALUES,

    /** The declared per-100/per-serving basis is missing, contradicted, or mislabeled. */
    INVALID_NUTRITION_BASIS,

    /**
     * A mass or volume basis has to be scaled to a counted amount (pieces, servings, bars) and
     * nothing in the request or the evidence states what one of them weighs. Nomi will not invent
     * that weight from a food name, so the item is reported rather than silently approximated.
     */
    MISSING_PORTION_WEIGHT,

    /** The provider's response could not be read as the agreed structured contract. */
    PARSING_FAILURE,

    /** The provider did not answer within the configured timeout. */
    PROVIDER_TIMEOUT,

    /** The provider refused the request because the account is over its rate limit. */
    PROVIDER_RATE_LIMITED,

    /** The selected model or endpoint does not exist or is not available to this account. */
    MODEL_UNAVAILABLE,

    /** The provider could not be reached at all. */
    PROVIDER_UNREACHABLE,
}

/**
 * A research failure that names the item it belongs to and why it failed.
 *
 * It stays an [AiValidationException] so every existing catch site keeps working; callers that
 * care about the cause read [reason] and [itemName] instead of matching on message text.
 */
open class NutritionResearchException(
    val reason: NutritionFailureReason,
    /** The logged item this failure belongs to, or null for a whole-request failure. */
    val itemName: String? = null,
    /** Position of that item in the logged meal, for multi-item diagnosis. */
    val itemIndex: Int? = null,
    /** Developer-facing detail: the exact rejection, never shown raw in the UI. */
    val detail: String,
) : AiValidationException(
    buildString {
        append(reason.name)
        if (itemName != null) append(" for '").append(itemName).append('\'')
        append(": ").append(detail)
    },
)
