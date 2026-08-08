package com.nomi.app.ui.format

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.QuantitySemantic
import com.nomi.app.ui.today.TodayFoodEntry
import java.util.Locale

/** Formats reconciled user quantity metadata without ever consulting a source serving size. */
fun AnalyzedFoodItem.quantityDisplay(locale: Locale): QuantityDisplayText {
    val resolution = quantityResolution
    return QuantityDisplayFormatter.format(
        request = QuantityDisplayRequest(
            quantity = quantity,
            unit = unit,
            gramsEquivalent = gramsEquivalent,
            canonicalQuantity = resolution?.canonicalQuantity,
            canonicalUnit = resolution?.canonicalUnit,
            enteredQuantity = resolution?.enteredQuantity,
            enteredUnit = resolution?.enteredUnit,
            semantic = when (resolution?.semantic) {
                QuantitySemantic.PACKAGE_PERCENT -> QuantityDisplaySemantic.PACKAGE_PERCENT
                QuantitySemantic.PACKAGE_FRACTION -> QuantityDisplaySemantic.PACKAGE_FRACTION
                QuantitySemantic.LOCAL_CAN_DEFAULT -> QuantityDisplaySemantic.LOCAL_CAN_DEFAULT
                QuantitySemantic.DIRECT_AMOUNT, null -> QuantityDisplaySemantic.DIRECT_AMOUNT
            },
            packageQuantity = resolution?.packageQuantity,
            packageUnit = resolution?.packageUnit,
            fractionNumerator = resolution?.fractionNumerator,
            fractionDenominator = resolution?.fractionDenominator,
            percentage = resolution?.percentage,
            isApproximate = resolution?.isApproximate == true ||
                (resolution?.enteredUnit != null && gramsEquivalent != null && isEstimate),
            sourcePackageQuantity = resolution?.sourcePackageQuantity ?: sourcePackageQuantity,
            sourcePackageUnit = resolution?.sourcePackageUnit ?: sourcePackageUnit,
            sourcePackageConflict = resolution?.sourcePackageConflict == true,
        ),
        locale = locale,
    )
}

/** Keeps legacy rows readable while new logs store canonical quantities directly. */
fun TodayFoodEntry.quantityDisplay(locale: Locale): QuantityDisplayText {
    if (!amount.isFinite() || amount <= 0.0) {
        return QuantityDisplayText(primary = amountText.ifBlank { "—" })
    }
    return QuantityDisplayFormatter.format(
        request = QuantityDisplayRequest(
            quantity = amount,
            unit = unit,
            gramsEquivalent = grams,
            isApproximate = isEstimated && grams != null,
        ),
        locale = locale,
    )
}
