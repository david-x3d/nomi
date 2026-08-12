package com.nomi.app.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * The languages Nomi's interface is translated into.
 *
 * English is the source language every other entry is written against, so it is first and needs
 * no catalogue entries. The rest follow in alphabetical order of their own name, which is the
 * order the language picker shows them in.
 */
enum class NomiLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    DUTCH("nl", "Nederlands"),
    PORTUGUESE("pt", "Português"),
    ALBANIAN("sq", "Shqip"),
    SWEDISH("sv", "Svenska"),
    TURKISH("tr", "Türkçe"),
    ;

    /** Drives number, date and collation formatting wherever the UI needs a [Locale]. */
    val locale: Locale = Locale.forLanguageTag(tag)

    companion object {
        val Default = ENGLISH

        fun fromTag(tag: String?): NomiLanguage? =
            tag?.takeIf { it.isNotBlank() }?.let { value ->
                entries.firstOrNull { it.tag.equals(value, ignoreCase = true) }
            }

        /** The closest supported language for a system locale, ignoring region. */
        fun matching(locale: Locale): NomiLanguage = fromTag(locale.language) ?: Default
    }
}

val LocalNomiLanguage = staticCompositionLocalOf { NomiLanguage.Default }

/**
 * Translates an English interface string into the language the user picked.
 *
 * The English text is the catalogue key, so call sites stay readable and a missing translation
 * degrades to English instead of to a resource name. [NomiTranslationCatalogTest] fails the build
 * if a key used here has no entry for every language.
 */
@Composable
@ReadOnlyComposable
fun nomiString(english: String): String =
    NomiTranslations.translate(english, LocalNomiLanguage.current)

/**
 * Translates a template and substitutes `{0}`, `{1}`, … with [arguments] in the order they are
 * passed, so a translation is free to reorder them.
 *
 * Braces are used rather than `String.format` specifiers because every template is repeated once
 * per language in the catalogue and `%1$s` needs escaping in a Kotlin literal.
 */
@Composable
@ReadOnlyComposable
fun nomiFormat(english: String, vararg arguments: Any?): String =
    fillTemplate(NomiTranslations.translate(english, LocalNomiLanguage.current), arguments)

@Composable
@ReadOnlyComposable
fun nomiLocale(): Locale = LocalNomiLanguage.current.locale

internal fun fillTemplate(template: String, arguments: Array<out Any?>): String {
    if (arguments.isEmpty() || '{' !in template) return template
    val result = StringBuilder(template.length + arguments.size * 8)
    var index = 0
    while (index < template.length) {
        val character = template[index]
        if (character == '{') {
            val close = template.indexOf('}', index + 1)
            val slot = if (close > index) template.substring(index + 1, close).toIntOrNull() else null
            if (slot != null && slot in arguments.indices) {
                result.append(arguments[slot]?.toString().orEmpty())
                index = close + 1
                continue
            }
        }
        result.append(character)
        index++
    }
    return result.toString()
}
