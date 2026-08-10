package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.PortionEditInstruction
import java.util.Locale

/**
 * Reads the portion corrections people actually type, without asking a model.
 *
 * "Half" is the single most common edit in a food tracker, and it is arithmetic. Sending it to
 * a language model costs money and a visible pause to be told something this file can decide
 * with certainty, so the model is only consulted for wording this parser declines to guess at.
 *
 * The parser is deliberately conservative: it matches only when the *entire* correction is a
 * quantity expression. "Actually it was chicken, not tuna" must never look like a portion
 * change, because a wrong PORTION_ONLY answer silently keeps the wrong food's nutrition under
 * a number the user now trusts more for having corrected it. When in doubt it returns null and
 * lets the classifier decide.
 */
object PortionEditParser {

    fun parseOrNull(correction: String): PortionEditInstruction? {
        val normalized = normalize(correction)
        if (normalized.isBlank()) return null
        val core = stripFillerWords(normalized)
        if (core.isBlank()) return null

        return parseWordFactor(core)
            ?: parsePercentage(core)
            ?: parseMultiplier(core)
            ?: parseFractionOfCount(core)
            ?: parseFraction(core)
            ?: parseExplicitAmount(core)
    }

    /** "half", "double", "a third", "¼". */
    private fun parseWordFactor(core: String): PortionEditInstruction? =
        WORD_FACTORS[core]?.let(PortionEditInstruction::scale)

    /** "50%", "55% of the package", "75% of it". */
    private fun parsePercentage(core: String): PortionEditInstruction? {
        val match = PERCENTAGE.matchEntire(core) ?: return null
        val percent = match.groupValues[1].toDoubleOrNull() ?: return null
        if (!percent.isFinite() || percent <= 0.0) return null
        // Percentages describe a share of what is already logged, so anything above 100 is a
        // different statement than the user thinks they are making. Let the model handle it.
        if (percent > 100.0) return null
        return PortionEditInstruction.scale(percent / 100.0)
    }

    /** "2x", "x2", "3 times". */
    private fun parseMultiplier(core: String): PortionEditInstruction? {
        val match = MULTIPLIER.matchEntire(core) ?: return null
        val factor = match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return null
        if (!factor.isFinite() || factor <= 0.0 || factor > PortionEditInstruction.MAX_SCALE_FACTOR) {
            return null
        }
        return PortionEditInstruction.scale(factor)
    }

    /**
     * "3 of the 6 pieces", "2 of 4".
     *
     * The denominator is what was originally logged, so this is a share rather than a new
     * count: the researched food may have been logged in grams, and 3/6 still halves it.
     */
    private fun parseFractionOfCount(core: String): PortionEditInstruction? {
        val match = FRACTION_OF_COUNT.matchEntire(core) ?: return null
        val eaten = match.groupValues[1].toDoubleOrNull() ?: return null
        val total = match.groupValues[2].toDoubleOrNull() ?: return null
        if (!eaten.isFinite() || !total.isFinite() || total <= 0.0 || eaten <= 0.0) return null
        if (eaten > total) return null
        return PortionEditInstruction.scale(eaten / total)
    }

    /** "1/2", "2/3". */
    private fun parseFraction(core: String): PortionEditInstruction? {
        val match = FRACTION.matchEntire(core) ?: return null
        val numerator = match.groupValues[1].toDoubleOrNull() ?: return null
        val denominator = match.groupValues[2].toDoubleOrNull() ?: return null
        if (denominator <= 0.0 || numerator <= 0.0) return null
        val factor = numerator / denominator
        if (factor > PortionEditInstruction.MAX_SCALE_FACTOR) return null
        return PortionEditInstruction.scale(factor)
    }

    /**
     * "200 g", "200g instead of 400g", "3 pieces".
     *
     * Only the amount actually eaten is taken. A trailing "instead of 400 g" names what the
     * value is replacing, which is context, not a second amount.
     */
    private fun parseExplicitAmount(core: String): PortionEditInstruction? {
        val withoutReplaced = core.replace(REPLACED_AMOUNT, "").trim()
        val match = EXPLICIT_AMOUNT.matchEntire(withoutReplaced) ?: return null
        val quantity = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (!quantity.isFinite() || quantity <= 0.0) return null
        val unit = CANONICAL_UNITS[match.groupValues[2]] ?: return null
        return PortionEditInstruction.setQuantity(quantity, unit)
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        // Vulgar fractions carry their whole meaning in one character, so they are expanded
        // before the punctuation strip below would otherwise delete them.
        .replace("½", "1/2")
        .replace("⅓", "1/3")
        .replace("⅔", "2/3")
        .replace("¼", "1/4")
        .replace("¾", "3/4")
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('ü', 'u')
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9%/.,\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Removes only words that carry no quantity meaning, so what remains either is a quantity
     * expression or is not one. Anything naming a food, brand, or ingredient survives and
     * therefore fails to match, which is the intended outcome.
     */
    private fun stripFillerWords(value: String): String {
        var result = " $value "
        FILLER_WORDS.forEach { word -> result = result.replace(" $word ", " ") }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private val WORD_FACTORS: Map<String, Double> = mapOf(
        "half" to 0.5,
        "halve" to 0.5,
        "hal" to 0.5,
        "halfte" to 0.5,
        "haelfte" to 0.5,
        "double" to 2.0,
        "doubled" to 2.0,
        "twice" to 2.0,
        "doppelt" to 2.0,
        "doppelte" to 2.0,
        "triple" to 3.0,
        "tripled" to 3.0,
        "dreifach" to 3.0,
        "third" to 1.0 / 3.0,
        "thirds" to 1.0 / 3.0,
        "drittel" to 1.0 / 3.0,
        "quarter" to 0.25,
        "quarters" to 0.25,
        "viertel" to 0.25,
        "three quarters" to 0.75,
        "two thirds" to 2.0 / 3.0,
        "three quarter" to 0.75,
        "two third" to 2.0 / 3.0,
    )

    /**
     * Words that only soften a sentence. "Couple" and "few" are deliberately absent: "a couple
     * of bites" has no defensible factor, so it falls through to the model rather than being
     * guessed at here.
     */
    private val FILLER_WORDS = listOf(
        "i", "only", "just", "ate", "eat", "had", "have", "actually", "really",
        "about", "approximately", "roughly", "around", "make", "made", "it", "its",
        "this", "that", "the", "a", "an", "of", "was", "were", "is", "please",
        // "one" only ever modifies the fraction that follows it ("one third"); it never names
        // a count on its own here, because a bare "1" would be written as a digit.
        "one",
        "change", "to", "instead", "portion", "amount", "total", "nur", "etwa",
        "ungefahr", "circa", "ca", "war", "waren", "habe", "gegessen", "davon",
        "die", "der", "das", "ein", "eine", "von", "nut", "bitte", "andere",
    )

    private val PERCENTAGE = Regex("^(\\d{1,3}(?:[.,]\\d+)?)\\s*%(?:\\s*(?:package|packung|pack))?$")
    private val MULTIPLIER = Regex("^(?:(\\d+(?:[.,]\\d+)?)\\s*x|x\\s*(\\d+(?:[.,]\\d+)?)|(\\d+(?:[.,]\\d+)?)\\s*times)$")
    private val FRACTION_OF_COUNT = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:/|out)?\\s*(?:of)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:pieces?|piece|slices?|stucke?|stuck|items?)$")
    private val FRACTION = Regex("^(\\d+)\\s*/\\s*(\\d+)$")
    private val EXPLICIT_AMOUNT = Regex("^(\\d+(?:[.,]\\d+)?)\\s*([a-z]+)$")
    private val REPLACED_AMOUNT = Regex("\\s*\\d+(?:[.,]\\d+)?\\s*[a-z]*\\s*$")

    private val CANONICAL_UNITS: Map<String, String> = buildMap {
        listOf("g", "gram", "grams", "gramm").forEach { put(it, "g") }
        listOf("mg", "milligram", "milligrams", "milligramm").forEach { put(it, "mg") }
        listOf("kg", "kilogram", "kilograms", "kilogramm").forEach { put(it, "kg") }
        listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres").forEach { put(it, "ml") }
        listOf("l", "liter", "liters", "litre", "litres").forEach { put(it, "l") }
        listOf("oz", "ounce", "ounces").forEach { put(it, "oz") }
        listOf("piece", "pieces", "pcs", "stuck", "stucke").forEach { put(it, "pieces") }
        listOf("slice", "slices", "scheibe", "scheiben").forEach { put(it, "slices") }
        listOf("serving", "servings", "portionen").forEach { put(it, "servings") }
    }
}
