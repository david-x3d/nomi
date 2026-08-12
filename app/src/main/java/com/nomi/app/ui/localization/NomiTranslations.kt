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

    val catalogue: Map<String, NomiTranslation> = buildMap(560) {
        putAll(commonTranslations)
        putAll(todayTranslations)
        putAll(foodTranslations)
        putAll(captureTranslations)
        putAll(libraryTranslations)
        putAll(profileTranslations)
        putAll(settingsTranslations)
    }

    fun translate(english: String, language: NomiLanguage): String {
        if (language == NomiLanguage.ENGLISH) return english
        return catalogue[english]?.forLanguage(language) ?: english
    }

    fun format(english: String, language: NomiLanguage, vararg arguments: Any?): String =
        fillTemplate(translate(english, language), arguments)
}
