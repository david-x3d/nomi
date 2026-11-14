package com.nomi.app.ai.parsing

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import java.util.Locale
import com.nomi.app.ai.validation.UserQuantityResolver

/**
 * A deliberately narrow parser for the most obvious single-food entries.
 *
 * It exists to avoid spending an AI round trip on inputs such as `apple` or `250 g Skyr`.
 * Anything that could encode a package, brand relationship, saved meal, or multiple foods returns
 * null and continues through the configured interpretation provider.
 */
object LocalFoodIntentParser {
    private const val MAX_INPUT_LENGTH = 80

    private val explicitAmountFood = Regex(
        """(?iu)^(\d+(?:[.,]\d+)?)\s*""" +
            """(mg|milligrams?|milligramm|kg|kilograms?|kilogramm|g|grams?|gramm|""" +
            """ml|milliliters?|millilitres?|cl|l|liters?|litres?|""" +
            """essl(?:\u00f6|oe|o)ffel|el|tbsp|tbs|tablespoons?|""" +
            """teel(?:\u00f6|oe|o)ffel|tl|tsp|teaspoons?|""" +
            """l(?:\u00f6|oe|o)ffel|spoons?)\s+""" +
            """([\p{L}][\p{L}'’.-]*(?:\s+[\p{L}][\p{L}'’.-]*){0,5})$""",
    )

    private val articles = setOf(
        "a", "an", "the",
        "ein", "eine", "einen", "einem", "einer", "eines", "der", "die", "das",
    )
    private val unsafeCharacters = Regex("[,;\\n\\r%+&/\\d]")
    private val providerRequiredWords = setOf(
        "meal", "breakfast", "lunch", "dinner", "snack",
        "essen", "fruehstueck", "mittagessen", "abendessen",
        "package", "pack", "bag", "can", "serving", "portion",
        "packung", "tuete", "beutel", "dose",
        "and", "plus", "with", "from", "by", "my", "usual",
        "und", "mit", "von", "bei", "mein", "meine", "meinen", "ueblich", "üblich",
    )
    private val singleFoodToken = Regex("^[\\p{L}][\\p{L}'’.-]*$")

    fun parseOrNull(rawText: String): ParsedFoodIntent? {
        val text = rawText.trim().replace(Regex("[ \\t]+"), " ")
        if (text.isBlank() || text.length > MAX_INPUT_LENGTH) return null

        parseExplicitAmount(text)?.let { return it }
        UserQuantityResolver.parseCountIntentOrNull(text)?.let { return it }
        parseAmountList(text)?.let { return it }
        if (unsafeCharacters.containsMatchIn(text)) return null

        val tokens = text.split(' ')
        if (tokens.any { it.lowercase(Locale.ROOT) in providerRequiredWords }) return null
        val articleCount = tokens.takeWhile { it.lowercase(Locale.ROOT) in articles }.size
        val foodTokens = tokens.drop(articleCount)
        if (foodTokens.size != 1 || !singleFoodToken.matches(foodTokens.single())) return null

        return ParsedFoodIntent(
            originalText = text,
            items = listOf(
                ParsedFoodItem(
                    name = foodTokens.single(),
                    // "ein Duplo" is an explicit count, even though it contains no metric unit.
                    // Keeping that fact locally saves the interpretation-model round trip while
                    // web research remains responsible for finding the product's grams per piece.
                    quantity = 1.0.takeIf { articleCount > 0 },
                    unit = "piece".takeIf { articleCount > 0 },
                ),
            ),
        )
    }

    /**
     * Skips a full interpretation-provider round trip for an unambiguous amount + unit + food.
     * Nutrition research still runs, and UserQuantityResolver remains authoritative for the
     * decimal comma and unit conversion.
     */
    private fun parseExplicitAmount(text: String): ParsedFoodIntent? {
        val match = explicitAmountFood.matchEntire(text) ?: return null
        val foodName = match.groupValues[3].trim()
        val foodTokens = foodName.split(' ')
        if (foodTokens.any { it.lowercase(Locale.ROOT) in providerRequiredWords }) return null

        return ParsedFoodIntent(
            originalText = text,
            items = listOf(
                ParsedFoodItem(
                    name = foodName,
                    quantity = match.groupValues[1].replace(',', '.').toDouble(),
                    unit = match.groupValues[2],
                    assumptions = listOfNotNull(
                        "Unqualified German Löffel was interpreted as an Esslöffel (15 ml)."
                            .takeIf { match.groupValues[2].isGenericSpoon() },
                    ),
                ),
            ),
        )
    }

    /**
     * A plain list in which every entry opens with its own number, such as
     * `2 Eier, 100 g Haferflocken und 1 Banane`, needs no model to take apart.
     *
     * The leading number is what makes the split safe: "Fish and Chips" or "Nudeln mit Soße" has
     * no number after the joining word, so it still goes to the interpretation provider.
     */
    private fun parseAmountList(text: String): ParsedFoodIntent? {
        val segments = text.split(listSeparator).map(String::trim)
        if (segments.size < 2 || segments.any { !it.firstOrNull().isAsciiDigit() }) return null
        val items = segments.map { segment ->
            (parseExplicitAmount(segment) ?: UserQuantityResolver.parseCountIntentOrNull(segment))
                ?.items?.singleOrNull() ?: return null
        }
        return ParsedFoodIntent(originalText = text, items = items)
    }

    /** A comma between two digits is a decimal comma and never separates foods. */
    private val listSeparator = Regex("""(?iu)\s*(?:(?<!\d),|,(?!\d)|;|\+)\s*|\s+(?:und|and|plus)\s+""")

    private fun Char?.isAsciiDigit(): Boolean = this != null && this in '0'..'9'

    private fun String.isGenericSpoon(): Boolean = lowercase(Locale.ROOT)
        .replace('\u00f6', 'o')
        .replace("oe", "o") in setOf("loffel", "spoon", "spoons")
}
