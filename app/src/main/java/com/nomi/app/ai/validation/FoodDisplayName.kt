package com.nomi.app.ai.validation

import java.util.Locale

/**
 * The name a logged food carries on the page.
 *
 * The page is meant to read like something a person wrote, so an entry says "Cheeseburger" -
 * not "mcdonalds cheeseburger with fries and coke" and not a cited page's product title.
 * Shortening a name is judgment, and it belongs to the model: it knows that "coke" is a
 * Coca-Cola, that a German noun takes a capital, and that dropping "Juneberry" from "Red Bull
 * Juneberry" would leave a different drink in the log. The prompts ask it for exactly that.
 *
 * This is the deterministic net underneath, and it is deliberately narrow. It only fixes what
 * is wrong no matter what the food is - stray spacing, an amount glued to the front, a missing
 * capital letter - and never decides which words carry the meaning. A slightly long name is a
 * far smaller failure than a wrong one.
 */
object FoodDisplayName {
    private const val MAX_LENGTH = 120
    private const val MIN_NAME_AFTER_AMOUNT = 4

    private val whitespace = Regex("\\s+")
    private val leadingAmount = Regex("""^\d+(?:[.,]\d+)?\s*(?:[x×]\s*)?""")
    private val leadingSeparators = charArrayOf(' ', '-', '–', '—', ':', ',', '·', '.')

    /** Words that only carry a sentence, never the food it is about. */
    private val leadingNoise = setOf(
        "a", "an", "the", "of", "with", "and", "plus",
        "ein", "eine", "einen", "einem", "einer", "eines",
        "der", "die", "das", "mit", "und", "von",
    )

    /**
     * Cleans a provider-returned [name] for display.
     *
     * Returns the input rather than an empty or gutted result, so a name is never lost to
     * cleaning.
     */
    fun clean(name: String): String {
        val collapsed = name.trim().replace(whitespace, " ")
        if (collapsed.isEmpty()) return collapsed
        val withoutNoise = withoutLeadingNoise(collapsed).ifBlank { collapsed }
        return capitalized(withoutNoise).take(MAX_LENGTH).trim()
    }

    private fun withoutLeadingNoise(name: String): String {
        // A leading number is an amount only when a food is still left without it. Otherwise
        // it is part of the name, the way it is in "7 Up".
        var result = name.replaceFirst(leadingAmount, "")
            .takeIf { it.length >= MIN_NAME_AFTER_AMOUNT } ?: name
        while (true) {
            val words = result.split(' ')
            if (words.size < 2 || comparisonKey(words.first()) !in leadingNoise) return result
            result = words.drop(1).joinToString(" ").trimStart(*leadingSeparators)
        }
    }

    /**
     * Gives a name a capital letter, and settles shouted names. A short all-caps word is left
     * alone because that is how names like KFC and BBQ are written.
     */
    private fun capitalized(name: String): String {
        val first = name.firstOrNull() ?: return name
        val isShouted = name.any(Char::isLetter) && name.none(Char::isLowerCase)
        return when {
            isShouted && (name.contains(' ') || name.length >= 5) ->
                name.split(' ').joinToString(" ", transform = ::titleCased)
            first.isLowerCase() -> name.replaceFirstChar { it.uppercaseChar() }
            else -> name
        }
    }

    private fun titleCased(word: String): String = word.lowercase(Locale.ROOT)
        .replaceFirstChar { it.uppercaseChar() }

    private fun comparisonKey(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
