package com.nomi.app.ui.localization

/**
 * One English interface string rendered in every other language Nomi supports.
 *
 * Named arguments are mandatory at the construction sites so a translation can never silently
 * land in the wrong language when an entry is edited.
 */
internal class NomiTranslation(
    val de: String,
    val es: String,
    val fr: String,
    val it: String,
    val nl: String,
    val pt: String,
    val sq: String,
    val sv: String,
    val tr: String,
) {
    /** Null for English, which is the key itself rather than a stored translation. */
    fun forLanguage(language: NomiLanguage): String? = when (language) {
        NomiLanguage.ENGLISH -> null
        NomiLanguage.GERMAN -> de
        NomiLanguage.SPANISH -> es
        NomiLanguage.FRENCH -> fr
        NomiLanguage.ITALIAN -> it
        NomiLanguage.DUTCH -> nl
        NomiLanguage.PORTUGUESE -> pt
        NomiLanguage.ALBANIAN -> sq
        NomiLanguage.SWEDISH -> sv
        NomiLanguage.TURKISH -> tr
    }
}

/**
 * The interface translation catalogue, keyed by the English source string.
 *
 * It is split across files by the part of the app the strings belong to; a string shared by
 * several screens lives in [commonTranslations]. Lookups that miss fall back to the English key,
 * which keeps an untranslated string readable rather than blank.
 */
internal object NomiTranslations {

    val catalogue: Map<String, NomiTranslation> = buildMap(700) {
        putAll(commonTranslations)
        putAll(onboardingTranslations)
        putAll(messageTranslations)
        putAll(todayTranslations)
        putAll(foodTranslations)
        putAll(captureTranslations)
        putAll(libraryTranslations)
        putAll(profileTranslations)
        putAll(shareTranslations)
        putAll(settingsTranslations)
        putAll(updateTranslations)
        putAll(detailTranslations)
        putAll(errorTranslations)
    }

    fun translate(english: String, language: NomiLanguage): String {
        if (language == NomiLanguage.ENGLISH) return english
        return catalogue[english]?.forLanguage(language) ?: english
    }

    fun format(english: String, language: NomiLanguage, vararg arguments: Any?): String =
        fillTemplate(translate(english, language), arguments)

    /**
     * Translates a message that was built outside a composition, such as an error from the
     * ViewModel, a provider or the speech recogniser.
     *
     * Those messages often carry a value - a status code, a provider name, a food - that was
     * substituted before the text reached the screen, so an exact lookup would miss them. A
     * message that is not a catalogue key is therefore matched against every templated key, and
     * the values it carries are moved into the translated template. Anything that still does not
     * match, including a message that is already translated, is returned unchanged.
     */
    fun localizeMessage(message: String, language: NomiLanguage): String {
        if (language == NomiLanguage.ENGLISH || message.isBlank()) return message
        catalogue[message]?.forLanguage(language)?.let { return it }
        val trimmed = message.trim()
        catalogue[trimmed]?.forLanguage(language)?.let { return it }
        for (template in messageTemplates) {
            val match = template.pattern.matchEntire(trimmed) ?: continue
            val arguments = arrayOfNulls<Any?>(template.slotCount)
            template.slots.forEachIndexed { group, slot ->
                if (arguments[slot] == null) arguments[slot] = match.groupValues[group + 1]
            }
            return format(template.english, language, *arguments)
        }
        return message
    }

    private class MessageTemplate(
        val english: String,
        val pattern: Regex,
        /** The placeholder number captured by each regex group, in order. */
        val slots: List<Int>,
    ) {
        val slotCount: Int = (slots.maxOrNull() ?: -1) + 1
    }

    /**
     * Longest first, so "{0} is temporarily unavailable (HTTP {1}) after …" wins over a shorter
     * template that would also match by swallowing more into one slot.
     */
    private val messageTemplates: List<MessageTemplate> by lazy {
        val placeholder = Regex("""\{(\d+)}""")
        // A template that is mostly slots ("{0} kcal", "{0}: {1}") would claim unrelated
        // messages, so only sentences with real wording around their values take part.
        catalogue.keys
            .filter { placeholder.containsMatchIn(it) && it.replace(placeholder, "").trim().length >= 12 }
            .sortedByDescending { key -> key.replace(placeholder, "").length }
            .map { key ->
                val slots = mutableListOf<Int>()
                val pattern = buildString {
                    var last = 0
                    placeholder.findAll(key).forEach { found ->
                        append(Regex.escape(key.substring(last, found.range.first)))
                        append("(.+?)")
                        slots += found.groupValues[1].toInt()
                        last = found.range.last + 1
                    }
                    append(Regex.escape(key.substring(last)))
                }
                MessageTemplate(key, Regex(pattern, RegexOption.DOT_MATCHES_ALL), slots)
            }
    }
}
