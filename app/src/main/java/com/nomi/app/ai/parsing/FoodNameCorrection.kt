package com.nomi.app.ai.parsing

import java.util.Locale
import kotlin.math.min

/**
 * Fixes a mistyped food against the ones you have actually eaten.
 *
 * A model corrects toward what is popular, which is the wrong instinct for a food log: the
 * regional bottle you drink every week loses to the famous one that is spelled almost the same.
 * Your own history does not have that bias. "red bull junebrry" becomes "Red Bull Juneberry"
 * because that is a thing you have logged, and a correct hit here also means the entry can be
 * answered from the local cache instead of paying for another round of research.
 *
 * The danger is the opposite mistake: turning one product into a different one. A variant is
 * often a single short word - Zero, Light, Vanille, Juneberry - and swapping it silently logs
 * the wrong food with confident-looking numbers. So the rules are deliberately strict: short
 * words must match exactly, at least one word must be untouched to anchor the match, the total
 * correction is capped, and an ambiguous match is refused rather than guessed.
 */
object FoodNameCorrection {
    /** Beyond this the two names are describing different things, not the same one typed badly. */
    private const val MAX_TOTAL_DISTANCE = 2

    /**
     * Short words carry the meaning: Zero, Bio, Cola, Light, Mild, Diet. One edit turns Zero
     * into Hero, and a variant that short has no redundancy to spare, so it must match exactly.
     */
    private const val EXACT_MATCH_MAX_LENGTH = 4
    private const val ONE_EDIT_MAX_LENGTH = 6

    private val whitespace = Regex("\\s+")

    /**
     * Returns the known spelling [typed] was meant to be, or null when nothing is close enough
     * or two candidates are equally close.
     */
    fun correctedOrNull(typed: String, known: Iterable<String>): String? {
        val typedKey = normalize(typed)
        if (typedKey.isEmpty()) return null

        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestIsAmbiguous = false

        for (candidate in known) {
            val candidateKey = normalize(candidate)
            if (candidateKey.isEmpty()) continue
            if (candidateKey == typedKey) return candidate
            val distance = correctionDistance(typedKey, candidateKey) ?: continue
            when {
                distance < bestDistance -> {
                    best = candidate
                    bestDistance = distance
                    bestIsAmbiguous = false
                }
                distance == bestDistance && normalize(best.orEmpty()) != candidateKey ->
                    bestIsAmbiguous = true
            }
        }
        return if (bestIsAmbiguous) null else best
    }

    /**
     * How many edits turn one name into the other, or null when they are too far apart to be
     * the same food. Word counts must agree, because a missing or extra word is a different
     * product rather than a slip - except when the slip is a missing space, which is checked
     * against the whole name instead.
     */
    private fun correctionDistance(typedKey: String, candidateKey: String): Int? {
        val typedWords = typedKey.split(' ')
        val candidateWords = candidateKey.split(' ')
        if (typedWords.size != candidateWords.size) {
            return spacingOnlyDistance(typedKey, candidateKey)
        }

        var total = 0
        var anchored = false
        for (index in typedWords.indices) {
            val typedWord = typedWords[index]
            val candidateWord = candidateWords[index]
            if (typedWord == candidateWord) {
                anchored = true
                continue
            }
            val distance = editDistance(typedWord, candidateWord)
            if (distance > allowedEdits(min(typedWord.length, candidateWord.length))) return null
            total += distance
            if (total > MAX_TOTAL_DISTANCE) return null
        }
        // In a name of several words, one has to survive untouched: it is what says this is the
        // same product at all. A single-word name has nothing to anchor against, so there the
        // length-scaled edit budget is the whole test.
        if (typedWords.size > 1 && !anchored) return null
        return total
    }

    /** "redbull juneberry" and "Red Bull Juneberry" are the same words, spaced differently. */
    private fun spacingOnlyDistance(typedKey: String, candidateKey: String): Int? {
        val typed = typedKey.replace(" ", "")
        val candidate = candidateKey.replace(" ", "")
        if (typed == candidate) return 1
        if (min(typed.length, candidate.length) <= ONE_EDIT_MAX_LENGTH) return null
        val distance = editDistance(typed, candidate)
        return if (distance <= MAX_TOTAL_DISTANCE) distance + 1 else null
    }

    private fun allowedEdits(shorterLength: Int): Int = when {
        shorterLength <= EXACT_MATCH_MAX_LENGTH -> 0
        shorterLength <= ONE_EDIT_MAX_LENGTH -> 1
        else -> 2
    }

    /**
     * Folds the ways the same German word gets typed - missing umlauts, "ss" for "ß",
     * punctuation, casing - so those are not counted as mistakes at all.
     */
    private fun normalize(value: String): String {
        val folded = StringBuilder()
        for (character in value.lowercase(Locale.ROOT)) {
            when (character) {
                'ä' -> folded.append("ae")
                'ö' -> folded.append("oe")
                'ü' -> folded.append("ue")
                'ß' -> folded.append("ss")
                'é', 'è', 'ê' -> folded.append('e')
                'á', 'à', 'â' -> folded.append('a')
                else -> if (character.isLetterOrDigit()) folded.append(character) else folded.append(' ')
            }
        }
        return folded.toString().trim().replace(whitespace, " ")
    }

    private fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
