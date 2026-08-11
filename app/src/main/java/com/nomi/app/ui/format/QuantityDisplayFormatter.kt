package com.nomi.app.ui.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Presentation-only quantity metadata. Nutrition math must keep using the unrounded values.
 *
 * [canonicalQuantity] is the actual amount eaten in g/ml. Package wording is optional context;
 * it never replaces that canonical amount.
 */
data class QuantityDisplayRequest(
    val quantity: Double,
    val unit: String,
    val gramsEquivalent: Double? = null,
    val canonicalQuantity: Double? = null,
    val canonicalUnit: String? = null,
    val enteredQuantity: Double? = null,
    val enteredUnit: String? = null,
    val semantic: QuantityDisplaySemantic = QuantityDisplaySemantic.DIRECT_AMOUNT,
    /** Optional package/container word from the user's wording, e.g. bag or can. */
    val containerUnit: String? = null,
    val packageQuantity: Double? = null,
    val packageUnit: String? = null,
    val fractionNumerator: Int? = null,
    val fractionDenominator: Int? = null,
    val percentage: Double? = null,
    val isApproximate: Boolean = false,
    val sourcePackageQuantity: Double? = null,
    val sourcePackageUnit: String? = null,
    val sourcePackageConflict: Boolean = false,
)

enum class QuantityDisplaySemantic {
    DIRECT_AMOUNT,
    PACKAGE_PERCENT,
    PACKAGE_FRACTION,
    LOCAL_CAN_DEFAULT,
}

data class QuantityDisplayText(
    /** Canonical g/ml amount whenever it is known. */
    val primary: String,
    /** Human-friendly package context, such as "½ bag" or "55% of package". */
    val context: String? = null,
    /** Subtle explanation when a web source lists a different package size. */
    val sourceConflictNote: String? = null,
) {
    val withContext: String
        get() = context?.let { "$it · $primary" } ?: primary
}

object QuantityDisplayFormatter {
    private const val EPSILON = 1e-6

    fun format(request: QuantityDisplayRequest, locale: Locale): QuantityDisplayText {
        val packageKind = packageKind(request.containerUnit)
            ?: packageKind(request.unit)
            ?: when (request.semantic) {
                QuantityDisplaySemantic.PACKAGE_PERCENT,
                QuantityDisplaySemantic.PACKAGE_FRACTION,
                -> PackageKind.PACKAGE
                QuantityDisplaySemantic.LOCAL_CAN_DEFAULT -> PackageKind.CAN
                QuantityDisplaySemantic.DIRECT_AMOUNT -> null
            }
        val spoonContext = enteredSpoonContext(request, locale)
        val householdContext = enteredHouseholdContext(request, locale)
        val canonical = if (spoonContext != null && request.gramsEquivalent?.let(::isUsableAmount) == true) {
            CanonicalAmount(
                value = requireNotNull(request.gramsEquivalent),
                unit = "g",
                convertedApproximately = request.isApproximate,
            )
        } else {
            canonicalAmount(request)
        }
        val context = spoonContext ?: householdContext ?: semanticContext(request, packageKind, locale)
        val primary = canonical?.let { amount ->
            val approximate = request.isApproximate ||
                amount.convertedApproximately ||
                (context != null && !isNearlyWhole(amount.value))
            formatCanonical(amount.value, amount.unit, approximate, locale)
        } ?: formatNonCanonical(request.quantity, request.unit, packageKind, locale)

        return QuantityDisplayText(
            primary = primary,
            context = context,
            sourceConflictNote = conflictNote(request, locale),
        )
    }

    private fun canonicalAmount(request: QuantityDisplayRequest): CanonicalAmount? {
        canonicalFrom(request.canonicalQuantity, request.canonicalUnit)?.let { return it }
        canonicalFrom(request.quantity, request.unit)?.let { return it }

        return request.gramsEquivalent
            ?.takeIf(::isUsableAmount)
            ?.let { CanonicalAmount(it, "g") }
    }

    private fun canonicalFrom(quantity: Double?, rawUnit: String?): CanonicalAmount? {
        if (quantity == null || !isUsableAmount(quantity) || rawUnit.isNullOrBlank()) return null
        return when (normalizeUnit(rawUnit)) {
            "mg", "milligram", "milligrams", "milligramm" -> CanonicalAmount(quantity, "mg")
            "g", "gram", "grams", "gramm" -> CanonicalAmount(quantity, "g")
            "kg", "kilogram", "kilograms", "kilogramm" -> CanonicalAmount(quantity, "kg")
            "oz" -> CanonicalAmount(quantity * 28.349523125, "g", convertedApproximately = true)
            "ml" -> CanonicalAmount(quantity, "ml")
            "cl" -> CanonicalAmount(quantity * 10.0, "ml")
            "dl" -> CanonicalAmount(quantity * 100.0, "ml")
            "l" -> CanonicalAmount(quantity * 1_000.0, "ml")
            "fl oz", "us fl oz" -> CanonicalAmount(quantity * 29.5735295625, "ml", convertedApproximately = true)
            else -> null
        }
    }

    private fun enteredSpoonContext(request: QuantityDisplayRequest, locale: Locale): String? {
        val metadataUnit = request.enteredUnit?.takeIf(::isSpoonUnit)
        val unit = metadataUnit ?: request.unit.takeIf(::isSpoonUnit) ?: return null
        val quantity = if (metadataUnit != null) request.enteredQuantity else request.quantity
        val usableQuantity = quantity?.takeIf(::isUsableAmount) ?: return null
        return "${formatNumber(usableQuantity, locale, 2)} ${localizedUnit(unit, usableQuantity, locale)}"
    }

    private fun enteredHouseholdContext(
        request: QuantityDisplayRequest,
        locale: Locale,
    ): String? {
        val hasExactMetricAmount = canonicalFrom(
            request.canonicalQuantity,
            request.canonicalUnit,
        ) != null || request.gramsEquivalent?.let(::isUsableAmount) == true
        if (!hasExactMetricAmount) return null
        val metadataUnit = request.enteredUnit?.takeIf(::isHouseholdCountUnit)
        val unit = metadataUnit ?: request.unit.takeIf(::isHouseholdCountUnit) ?: return null
        val quantity = (if (metadataUnit != null) request.enteredQuantity else request.quantity)
            ?.takeIf(::isUsableAmount) ?: return null
        return "${formatNumber(quantity, locale, 2)} $unit"
    }

    private fun isHouseholdCountUnit(rawUnit: String): Boolean = when (normalizeUnit(rawUnit)) {
        "piece", "pieces", "pc", "pcs", "st\u00fcck", "st\u00fccke", "stueck", "stuecke",
        "kugel", "kugeln", "scoop", "scoops",
        -> true
        else -> false
    }

    private fun isSpoonUnit(rawUnit: String): Boolean = when (normalizeUnit(rawUnit)) {
        "tbsp", "tbs", "tablespoon", "tablespoons", "el", "esslöffel", "essloeffel", "essloffel",
        "tsp", "teaspoon", "teaspoons", "tl", "teelöffel", "teeloeffel", "teeloffel",
        "löffel", "loeffel", "loffel", "spoon", "spoons",
        -> true
        else -> false
    }

    private fun semanticContext(
        request: QuantityDisplayRequest,
        packageKind: PackageKind?,
        locale: Locale,
    ): String? {
        val kind = packageKind ?: return null
        if (request.semantic == QuantityDisplaySemantic.LOCAL_CAN_DEFAULT) {
            return "1 ${kind.label(locale)}"
        }
        val fraction = request.fractionNumerator
            ?.takeIf { it > 0 }
            ?.let { numerator ->
                request.fractionDenominator
                    ?.takeIf { it > numerator }
                    ?.let { denominator -> numerator to denominator }
            }
        if (fraction != null) {
            return "${fractionText(fraction.first, fraction.second)} ${kind.label(locale)}"
        }

        request.percentage
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { return percentContext(it, kind, locale) }

        val portion = request.packageQuantity ?: request.quantity
        if (!isUsableAmount(portion)) return null

        commonFraction(portion)?.let { (numerator, denominator) ->
            return "${fractionText(numerator, denominator)} ${kind.label(locale)}"
        }
        if (portion < 1.0) return percentContext(portion * 100.0, kind, locale)

        val number = formatNumber(portion, locale, maximumFractionDigits = 2)
        return "$number ${kind.label(locale, plural = portion > 1.0 + EPSILON)}"
    }

    private fun formatNonCanonical(
        quantity: Double,
        unit: String,
        packageKind: PackageKind?,
        locale: Locale,
    ): String {
        if (!isUsableAmount(quantity)) return unit.trim().ifBlank { "—" }
        packageKind?.let { kind ->
            commonFraction(quantity)?.let { (numerator, denominator) ->
                return "${fractionText(numerator, denominator)} ${kind.label(locale)}"
            }
            if (quantity < 1.0) return percentContext(quantity * 100.0, kind, locale)
            return "${formatNumber(quantity, locale, 2)} ${kind.label(locale, quantity > 1.0 + EPSILON)}"
        }
        return "${formatNumber(quantity, locale, 2)} ${localizedUnit(unit, quantity, locale)}".trim()
    }

    private fun formatCanonical(
        amount: Double,
        unit: String,
        approximate: Boolean,
        locale: Locale,
    ): String {
        val maxDigits = when {
            approximate && amount >= 10.0 -> 0
            isNearlyWhole(amount) -> 0
            else -> 2
        }
        // An approximate amount is still rounded more coarsely, but it is not marked with a
        // symbol: the page reads as written numbers, and a "≈" in front of every converted
        // spoon or package fraction turned that into arithmetic notation.
        val value = formatNumber(amount, locale, maxDigits)
        return "$value $unit"
    }

    private fun conflictNote(request: QuantityDisplayRequest, locale: Locale): String? {
        if (!request.sourcePackageConflict) return null
        val source = request.sourcePackageQuantity
            ?.takeIf(::isUsableAmount)
            ?.let { quantity ->
                val unit = request.sourcePackageUnit.orEmpty()
                "${formatNumber(quantity, locale, 2)} ${localizedUnit(unit, quantity, locale)}".trim()
            }
        val entered = request.packageQuantity
            ?.takeIf(::isUsableAmount)
            ?.let { quantity ->
                val unit = request.packageUnit.orEmpty()
                "${formatNumber(quantity, locale, 2)} ${localizedUnit(unit, quantity, locale)}".trim()
            }
        val german = locale.language.equals("de", ignoreCase = true)

        return when {
            german && source != null && entered != null ->
                "Die Quelle listet derzeit eine Packungsgröße von $source. Deine Eingabe von $entered wurde beibehalten."
            german && source != null ->
                "Die Quelle listet derzeit eine Packungsgröße von $source. Deine eingegebene Menge wurde beibehalten."
            german ->
                "Die Quelle listet eine andere Packungsgröße. Deine eingegebene Menge wurde beibehalten."
            source != null && entered != null ->
                "The source currently lists a $source package. Your entered $entered package was kept."
            source != null ->
                "The source currently lists a $source package. Your entered amount was kept."
            else ->
                "The source lists a different package size. Your entered amount was kept."
        }
    }

    private fun percentContext(percent: Double, kind: PackageKind, locale: Locale): String {
        val formatted = formatNumber(percent, locale, maximumFractionDigits = 1)
        return if (locale.language.equals("de", ignoreCase = true)) {
            "$formatted % ${kind.germanGenitive}"
        } else {
            "$formatted% of ${kind.englishSingular}"
        }
    }

    private fun commonFraction(value: Double): Pair<Int, Int>? = listOf(
        1 to 4,
        1 to 3,
        1 to 2,
        2 to 3,
        3 to 4,
    ).firstOrNull { (numerator, denominator) ->
        abs(value - numerator.toDouble() / denominator) <= 0.015
    }

    private fun fractionText(numerator: Int, denominator: Int): String = when (numerator to denominator) {
        1 to 2 -> "½"
        1 to 3 -> "⅓"
        2 to 3 -> "⅔"
        1 to 4 -> "¼"
        3 to 4 -> "¾"
        else -> "$numerator/$denominator"
    }

    private fun localizedUnit(rawUnit: String, quantity: Double, locale: Locale): String {
        packageKind(rawUnit)?.let { return it.label(locale, quantity > 1.0 + EPSILON) }
        return when (normalizeUnit(rawUnit)) {
            "mg", "milligram", "milligrams", "milligramm" -> "mg"
            "g", "gram", "grams", "gramm" -> "g"
            "kg", "kilogram", "kilograms", "kilogramm" -> "kg"
            "tbsp", "tbs", "tablespoon", "tablespoons", "el", "esslöffel", "essloeffel", "essloffel", "löffel", "loeffel", "loffel", "spoon", "spoons" -> if (locale.language == "de") "EL" else "tbsp"
            "tsp", "teaspoon", "teaspoons", "tl", "teelöffel", "teeloeffel", "teeloffel" -> if (locale.language == "de") "TL" else "tsp"
            "piece", "pieces", "stück", "pcs" -> if (locale.language == "de") "Stück" else if (quantity > 1.0 + EPSILON) "pieces" else "piece"
            else -> rawUnit.trim()
        }
    }

    private fun packageKind(rawUnit: String?): PackageKind? = when (normalizeUnit(rawUnit.orEmpty())) {
        "package", "packages", "pack", "packs", "packet", "packung", "packungen", "verpackung" -> PackageKind.PACKAGE
        "bag", "bags", "beutel", "tüte", "tüten" -> PackageKind.BAG
        "can", "cans", "dose", "dosen" -> PackageKind.CAN
        "bottle", "bottles", "flasche", "flaschen" -> PackageKind.BOTTLE
        else -> null
    }

    private fun normalizeUnit(unit: String): String = unit
        .trim()
        .lowercase(Locale.ROOT)
        .replace('.', ' ')
        .replace(Regex("\\s+"), " ")

    private fun formatNumber(value: Double, locale: Locale, maximumFractionDigits: Int): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = true
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits
        }.format(value)

    private fun isNearlyWhole(value: Double): Boolean = abs(value - value.roundToLong()) <= EPSILON

    private fun isUsableAmount(value: Double): Boolean = value.isFinite() && value > 0.0

    private data class CanonicalAmount(
        val value: Double,
        val unit: String,
        val convertedApproximately: Boolean = false,
    )

    private enum class PackageKind(
        val englishSingular: String,
        val englishPlural: String,
        val germanSingular: String,
        val germanPlural: String,
        val germanGenitive: String,
    ) {
        PACKAGE("package", "packages", "Packung", "Packungen", "der Packung"),
        BAG("bag", "bags", "Beutel", "Beutel", "des Beutels"),
        CAN("can", "cans", "Dose", "Dosen", "der Dose"),
        BOTTLE("bottle", "bottles", "Flasche", "Flaschen", "der Flasche"),
        ;

        fun label(locale: Locale, plural: Boolean = false): String =
            if (locale.language.equals("de", ignoreCase = true)) {
                if (plural) germanPlural else germanSingular
            } else {
                if (plural) englishPlural else englishSingular
            }
    }
}
