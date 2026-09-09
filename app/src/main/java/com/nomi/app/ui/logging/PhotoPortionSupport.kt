package com.nomi.app.ui.logging

import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.VisionFoodItem
import java.math.BigDecimal

/** Prefer editable food weights to generic servings, retaining volume units for drinks. */
internal fun VisionFoodItem.toPhotoParsedItem(): ParsedFoodItem {
    val grams = estimatedGrams?.takeIf { it.isFinite() && it > 0 }
    val amount = estimatedQuantity?.takeIf { it.isFinite() && it > 0 }
    val volume = unit?.trim()?.lowercase(java.util.Locale.ROOT) in
        setOf("ml", "l", "cl", "dl", "fl oz", "floz", "cup", "cups", "tbsp", "tsp")
    val useWeight = grams != null && !(volume && amount != null)
    return ParsedFoodItem(
        name = name,
        quantity = if (useWeight) grams else amount,
        unit = if (useWeight) "g" else unit,
        gramsEquivalent = grams,
        assumptions = visibleIngredients + listOfNotNull(
            "Photo portion estimate; not a measured weight.",
            weightEstimationBasis?.takeIf(String::isNotBlank),
            if (useWeight && amount != null && !unit.isNullOrBlank() && unit != "g")
                "Visible amount: ${amount.photoNumber()} $unit" else null,
        ),
    )
}

internal fun List<ParsedFoodItem>.toPhotoMealDescription(): String = joinToString(", ") { item ->
    val quantity = item.quantity?.takeIf { it.isFinite() && it > 0 }
    val unit = item.unit?.trim()?.takeIf(String::isNotBlank)
    when {
        quantity != null && unit != null -> "${quantity.photoNumber()} $unit ${item.name}"
        quantity != null -> "${quantity.photoNumber()} ${item.name}"
        else -> item.name
    }
}

private fun Double.photoNumber(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
