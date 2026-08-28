package com.nomi.app.domain.usecase

import com.nomi.app.data.local.entity.FoodEntity

/** Only data with a durable, non-model provenance may bypass fresh nutrition research. */
internal fun FoodEntity.isTrustedForNutritionReuse(): Boolean =
    isUserCreated || !barcode.isNullOrBlank() || nutritionSourceId != null

/**
 * Whether a freshly researched reading may overwrite the catalogue row it matched.
 *
 * The catalogue is what later logs of the same food reuse, so a weak first answer must not pin
 * that food forever. A verified package or manufacturer reading replaces a stored estimate; a
 * fresh estimate never overwrites anything, so a good row cannot be degraded by a later bad
 * lookup; and a food the user created stays theirs, because research has no standing to rewrite
 * something a person entered deliberately.
 */
internal fun FoodEntity.acceptsVerifiedUpgradeFrom(researchIsEstimate: Boolean): Boolean =
    isEstimated && !researchIsEstimate && !isUserCreated
