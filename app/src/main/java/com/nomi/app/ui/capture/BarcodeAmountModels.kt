package com.nomi.app.ui.capture

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ui.today.MealCategory
import java.util.Locale

data class BarcodeAmountUiState(
    val barcode: String,
    val sourceItem: AnalyzedFoodItem,
    val amount: String,
    val unit: String,
    val compatibleUnits: List<String>,
    val mealCategory: MealCategory,
    val servingLabel: String? = null,
    val errorMessage: String? = null,
) {
    val parsedAmount: Double?
        get() = BarcodeAmountSupport.parseAmount(amount)

    val canCalculate: Boolean
        get() = parsedAmount?.let { it.isFinite() && it > 0.0 } == true && unit in compatibleUnits
}

data class BarcodeAmountSuggestion(
    val amount: String,
    val unit: String,
)

/** Pure measurement helpers shared by the barcode amount UI and ViewModel. */
object BarcodeAmountSupport {
    private const val OUNCE_GRAMS = 28.349523125

    fun compatibleUnits(sourceUnit: String): List<String> = when (family(sourceUnit)) {
        UnitFamily.MASS -> listOf("g", "mg", "kg", "oz")
        UnitFamily.VOLUME -> listOf("ml", "cl", "l", "fl oz", "tbsp", "tsp")
        UnitFamily.OTHER -> listOf(canonicalUnit(sourceUnit))
    }

    fun initialSuggestion(servingSize: String?, sourceUnit: String): BarcodeAmountSuggestion {
        val baseUnit = when (family(sourceUnit)) {
            UnitFamily.MASS -> "g"
            UnitFamily.VOLUME -> "ml"
            UnitFamily.OTHER -> canonicalUnit(sourceUnit)
        }
        val fallback = BarcodeAmountSuggestion("100", baseUnit)
        val text = servingSize?.trim().orEmpty()
        if (text.isBlank()) return fallback

        val candidates = AMOUNT_WITH_UNIT.findAll(text).mapNotNull { match ->
            val amount = parseAmount(match.groupValues[1]) ?: return@mapNotNull null
            val unit = canonicalUnit(match.groupValues[2])
            if (amount <= 0.0 || family(unit) != family(sourceUnit)) return@mapNotNull null
            BarcodeAmountSuggestion(clean(amount), unit)
        }.toList()

        // Labels such as "12 fl oz / 355 ml" should prefer the metric amount when the
        // source is per 100 ml, while still retaining fl oz as an available compatible unit.
        return candidates.lastOrNull { it.unit == baseUnit }
            ?: candidates.firstOrNull()
            ?: fallback
    }

    fun parseAmount(value: String): Double? = value
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

    fun gramsEquivalent(quantity: Double, unit: String): Double? = when (canonicalUnit(unit)) {
        "mg" -> quantity * 0.001
        "g" -> quantity
        "kg" -> quantity * 1_000.0
        "oz" -> quantity * OUNCE_GRAMS
        else -> null
    }

    fun sanitizeAmount(value: String): String = value
        .filter { it.isDigit() || it == '.' || it == ',' }
        .take(12)

    fun description(amount: String, unit: String, itemName: String): String =
        listOf(amount.trim(), unit.trim(), itemName.trim()).filter(String::isNotBlank).joinToString(" ")

    private fun family(unit: String): UnitFamily = when (canonicalUnit(unit)) {
        "g", "mg", "kg", "oz" -> UnitFamily.MASS
        "ml", "cl", "l", "fl oz", "tbsp", "tsp" -> UnitFamily.VOLUME
        else -> UnitFamily.OTHER
    }

    private fun canonicalUnit(raw: String): String {
        val unit = raw.trim().lowercase(Locale.ROOT)
            .replace("fl. oz.", "fl oz")
            .replace("fl. oz", "fl oz")
            .replace('\u00e4', 'a')
            .replace('\u00f6', 'o')
            .replace('\u00fc', 'u')
            .replace("\u00df", "ss")
            .replace(Regex("[._-]+"), " ")
            .replace(Regex("\\s+"), " ")
        return when (unit) {
            "milligram", "milligrams", "milligramm" -> "mg"
            "gram", "grams", "gramm" -> "g"
            "kilogram", "kilograms", "kilogramm" -> "kg"
            "ounce", "ounces" -> "oz"
            "milliliter", "milliliters", "millilitre", "millilitres" -> "ml"
            "centiliter", "centiliters", "centilitre", "centilitres" -> "cl"
            "liter", "liters", "litre", "litres" -> "l"
            "floz", "fluid ounce", "fluid ounces", "us fl oz" -> "fl oz"
            "tbs", "tablespoon", "tablespoons", "el", "essloffel", "essloeffel" -> "tbsp"
            "teaspoon", "teaspoons", "tl", "teeloffel", "teeloeffel" -> "tsp"
            else -> unit
        }
    }

    private fun clean(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private enum class UnitFamily { MASS, VOLUME, OTHER }

    private val AMOUNT_WITH_UNIT = Regex(
        pattern = """(?iu)(\d+(?:[.,]\d+)?)\s*(fl\.?\s*oz\.?|milligrams?|milligramm|mg|kilograms?|kilogramm|kg|grams?|gramm|g|essl(?:\u00f6|oe|o)ffel|el|tbsp|tbs|tablespoons?|teel(?:\u00f6|oe|o)ffel|tl|tsp|teaspoons?|ml|cl|oz|l)\b""",
    )
}
