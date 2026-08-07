package com.nomi.app.ai.parsing

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import java.util.Locale

/**
 * A deliberately narrow parser for the most obvious single-food entries.
 *
 * It exists to avoid spending an AI round trip on inputs such as `apple`. Anything that could
 * encode a portion, package, brand relationship, saved meal, or more than one food returns null
 * and continues through the configured interpretation provider.
 */
object LocalFoodIntentParser {
    private const val MAX_INPUT_LENGTH = 80

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
        if (text.isBlank() || text.length > MAX_INPUT_LENGTH || unsafeCharacters.containsMatchIn(text)) {
            return null
        }

        val tokens = text.split(' ')
        if (tokens.any { it.lowercase(Locale.ROOT) in providerRequiredWords }) return null
        val foodTokens = tokens.dropWhile { it.lowercase(Locale.ROOT) in articles }
        if (foodTokens.size != 1 || !singleFoodToken.matches(foodTokens.single())) return null

        return ParsedFoodIntent(
            originalText = text,
            items = listOf(ParsedFoodItem(name = foodTokens.single())),
        )
    }
}
