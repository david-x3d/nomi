package com.nomi.app.domain

import java.util.Locale

/**
 * One canonical decimal reader for every quantity surface in Nomi.
 *
 * Nomi is used in ten languages and a decimal comma is not an edge case: it is the character a
 * German, French, Italian, Spanish, Dutch, Portuguese, Albanian, Swedish or Turkish keyboard
 * produces for a decimal point. Half a decade of call sites each rolled their own
 * `replace(',', '.')`, and the two that did not — [com.nomi.app.ui.logging.ManualFoodDraft] and
 * the fraction-of-count pattern in
 * [com.nomi.app.domain.usecase.PortionEditParser] — silently rejected the same number a sibling
 * surface accepted.
 *
 * Accepting both separators is deliberate and is not a locale guess: every one of these strings
 * is either free text a person typed or a value Nomi itself is about to persist, so being
 * liberal in what Nomi accepts costs nothing and removing a dead end is worth more than guessing
 * which separator "meant" a bare dot.
 */
object DecimalInput {

    /**
     * Reads a decimal written with either a point or a comma, tolerating surrounding whitespace
     * and an internal grouping separator that a keyboard may have inserted.
     *
     * Returns `null` for anything that is not a finite, non-negative decimal, so callers can use
     * a single `!= null` check as their validity gate.
     */
    fun parseOrNull(raw: String?): Double? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = text
            // A space or a non-breaking space is what several locales use to group digits.
            .replace('\u00A0', ' ')
            .replace(" ", "")
            .replace(',', '.')
        // Guard against a second separator ("1.2.3") that would otherwise be silently truncated.
        if (normalized.count { it == '.' } > 1) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() && it >= 0.0 }
    }

    /**
     * Renders a decimal the way the rest of Nomi stores it internally: a point, no grouping, and
     * no trailing zeros, so a value typed as `1,50` and one typed as `1.5` are the same number.
     */
    fun canonical(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.ROOT, "%s", value)
}
