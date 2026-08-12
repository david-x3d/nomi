package com.nomi.app.ui.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the interface catalogue.
 *
 * A missing or malformed translation does not crash - it silently falls back to English, or
 * drops a value out of a sentence - so nothing but a test will notice it. These checks run over
 * every entry in every language, which is the only way a ten-language catalogue stays honest.
 */
class NomiTranslationCatalogTest {

    private val translatedLanguages = NomiLanguage.entries - NomiLanguage.ENGLISH

    @Test
    fun `every entry is translated into every language`() {
        val blanks = mutableListOf<String>()
        NomiTranslations.catalogue.forEach { (english, translation) ->
            translatedLanguages.forEach { language ->
                val value = translation.forLanguage(language)
                if (value.isNullOrBlank()) {
                    blanks += "${language.tag}: $english"
                }
            }
        }
        assertEquals("untranslated catalogue entries: $blanks", emptyList<String>(), blanks)
    }

    @Test
    fun `placeholders survive translation in every language`() {
        val mismatches = mutableListOf<String>()
        NomiTranslations.catalogue.forEach { (english, translation) ->
            val expected = english.placeholders()
            translatedLanguages.forEach { language ->
                val actual = translation.forLanguage(language).orEmpty().placeholders()
                if (actual != expected) {
                    mismatches += "${language.tag}: \"$english\" has $actual, expected $expected"
                }
            }
        }
        assertEquals("placeholder mismatches: $mismatches", emptyList<String>(), mismatches)
    }

    /**
     * A translation that is character-for-character the English source is usually a forgotten
     * entry rather than a real match. Short labels and proper nouns legitimately collide, so
     * only longer sentences are checked.
     */
    @Test
    fun `long sentences are not left as untranslated English`() {
        val copies = mutableListOf<String>()
        NomiTranslations.catalogue.forEach { (english, translation) ->
            if (english.length < 40) return@forEach
            translatedLanguages.forEach { language ->
                if (translation.forLanguage(language) == english) {
                    copies += "${language.tag}: $english"
                }
            }
        }
        assertEquals("English left in a translated slot: $copies", emptyList<String>(), copies)
    }

    @Test
    fun `every supported language resolves a distinct locale and native name`() {
        val tags = NomiLanguage.entries.map { it.tag }
        assertEquals("duplicate language tags", tags.distinct().size, tags.size)
        val names = NomiLanguage.entries.map { it.nativeName }
        assertEquals("duplicate native names", names.distinct().size, names.size)
        NomiLanguage.entries.forEach { language ->
            assertEquals(
                "locale for ${language.tag}",
                language.tag,
                language.locale.language,
            )
        }
    }

    @Test
    fun `an unknown tag falls back rather than failing`() {
        assertEquals(null, NomiLanguage.fromTag("xx"))
        assertEquals(null, NomiLanguage.fromTag(""))
        assertEquals(null, NomiLanguage.fromTag(null))
        assertEquals(NomiLanguage.GERMAN, NomiLanguage.fromTag("DE"))
    }

    @Test
    fun `a string with no catalogue entry stays readable English`() {
        val unknown = "This string is deliberately absent from the catalogue."
        assertEquals(unknown, NomiTranslations.translate(unknown, NomiLanguage.FRENCH))
        assertEquals(unknown, NomiTranslations.translate(unknown, NomiLanguage.ENGLISH))
    }

    @Test
    fun `templates substitute arguments in the order the translation asks for`() {
        assertEquals("b then a", fillTemplate("{1} then {0}", arrayOf("a", "b")))
        assertEquals("1 item", fillTemplate("{0} item", arrayOf(1)))
        // A slot with no argument is left alone rather than throwing or blanking the sentence.
        assertEquals("{3} left", fillTemplate("{3} left", arrayOf("a")))
        assertEquals("no slots", fillTemplate("no slots", arrayOf("a")))
        assertEquals("{0} kept", fillTemplate("{0} kept", emptyArray()))
    }

    @Test
    fun `German keeps the translations it shipped with`() {
        // Spot check against the strings the app used before the catalogue existed, so the
        // migration from inline pairs cannot have shuffled a language column.
        assertEquals("Heute", NomiTranslations.translate("Today", NomiLanguage.GERMAN))
        assertEquals("Einstellungen", NomiTranslations.translate("Settings", NomiLanguage.GERMAN))
        assertEquals("Frühstück", NomiTranslations.translate("Breakfast", NomiLanguage.GERMAN))
        assertEquals("Speichern", NomiTranslations.translate("Save", NomiLanguage.GERMAN))
    }

    @Test
    fun `each language column holds its own language`() {
        // "Save" is short, common, and different in all ten, so a copy-paste slip between two
        // adjacent columns in the catalogue shows up here immediately.
        val save = mapOf(
            NomiLanguage.ENGLISH to "Save",
            NomiLanguage.GERMAN to "Speichern",
            NomiLanguage.SPANISH to "Guardar",
            NomiLanguage.FRENCH to "Enregistrer",
            NomiLanguage.ITALIAN to "Salva",
            NomiLanguage.DUTCH to "Opslaan",
            NomiLanguage.PORTUGUESE to "Guardar",
            NomiLanguage.ALBANIAN to "Ruaj",
            NomiLanguage.SWEDISH to "Spara",
            NomiLanguage.TURKISH to "Kaydet",
        )
        assertEquals(NomiLanguage.entries.size, save.size)
        save.forEach { (language, expected) ->
            assertEquals(expected, NomiTranslations.translate("Save", language))
        }
    }

    @Test
    fun `catalogue text is valid UTF-8 without mojibake`() {
        // Plain Â/â are valid letters in French, Portuguese and Turkish; only broken byte patterns
        // are rejected here.
        val suspicious = listOf("Ã", "Â ", "Â€", "â€", "â‚", "ï¿½", "\uFFFD")
        val broken = buildList {
            NomiLanguage.entries.forEach { language ->
                if (suspicious.any(language.nativeName::contains)) {
                    add("native name ${language.tag}: ${language.nativeName}")
                }
            }
            NomiTranslations.catalogue.forEach { (english, translation) ->
                translatedLanguages.forEach { language ->
                    val value = translation.forLanguage(language).orEmpty()
                    if (suspicious.any(value::contains)) add("${language.tag}: $english -> $value")
                }
            }
        }
        assertEquals("mojibake in translations: $broken", emptyList<String>(), broken)
        assertEquals("Frühstück", NomiTranslations.translate("Breakfast", NomiLanguage.GERMAN))
        assertEquals("Español", NomiLanguage.SPANISH.nativeName)
        assertEquals("Français", NomiLanguage.FRENCH.nativeName)
        assertEquals("Português", NomiLanguage.PORTUGUESE.nativeName)
        assertEquals("Türkçe", NomiLanguage.TURKISH.nativeName)
    }

    @Test
    fun `the catalogue covers the whole interface`() {
        // A floor, not an exact count: it fails loudly if a catalogue file stops being merged
        // into the map, which would otherwise only show up as English leaking into the UI.
        assertTrue(
            "catalogue shrank to ${NomiTranslations.catalogue.size} entries",
            NomiTranslations.catalogue.size >= 590,
        )
        assertFalse(NomiTranslations.catalogue.containsKey(""))
    }

    private fun String.placeholders(): Set<String> =
        Regex("""\{\d+}""").findAll(this).map { it.value }.toSet()
}
