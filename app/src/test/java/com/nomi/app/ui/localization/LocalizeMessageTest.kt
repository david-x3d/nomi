package com.nomi.app.ui.localization

import com.nomi.app.ui.app.safeAiMessage
import com.nomi.app.ai.validation.AiValidationException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Messages built outside a composition - often with a value already filled in - still reach the
 * screen in the chosen language.
 */
class LocalizeMessageTest {

    @Test
    fun `an exact catalogue message is translated`() {
        assertEquals(
            "Der Anbieter hat zu lange gebraucht. Versuche es erneut.",
            NomiTranslations.localizeMessage("The provider took too long. Try again.", NomiLanguage.GERMAN),
        )
    }

    @Test
    fun `a message with a filled-in value is matched back to its template`() {
        assertEquals(
            "Der Anbieter ist vorübergehend nicht erreichbar (HTTP 503). Versuche es erneut.",
            NomiTranslations.localizeMessage(
                "The provider is temporarily unavailable (HTTP 503). Try again.",
                NomiLanguage.GERMAN,
            ),
        )
        assertEquals(
            "OpenRouter ist auch nach automatischen Wiederholungen vorübergehend nicht erreichbar " +
                "(HTTP 502). Versuche es gleich noch einmal.",
            NomiTranslations.localizeMessage(
                "OpenRouter is temporarily unavailable (HTTP 502) after automatic retries. Try again shortly.",
                NomiLanguage.GERMAN,
            ),
        )
        assertEquals(
            "Health Connect u sinkronizua. U importuan 4 pesha të reja.",
            NomiTranslations.localizeMessage(
                "Health Connect synced. Imported 4 new weights.",
                NomiLanguage.ALBANIAN,
            ),
        )
    }

    @Test
    fun `a value can move to a different place in the translated sentence`() {
        assertEquals(
            "En büyük pay: 520 kcal ile Pizza (öğünün %60 kadarı).",
            NomiTranslations.localizeMessage(
                "Largest contributor: Pizza with 520 kcal (%60 of the meal).",
                NomiLanguage.TURKISH,
            ),
        )
    }

    @Test
    fun `an unknown or already translated message is left alone`() {
        val unknown = "Something Nomi has never said before."
        assertEquals(unknown, NomiTranslations.localizeMessage(unknown, NomiLanguage.FRENCH))
        val german = "Der Anbieter hat zu lange gebraucht. Versuche es erneut."
        assertEquals(german, NomiTranslations.localizeMessage(german, NomiLanguage.GERMAN))
    }

    @Test
    fun `English is returned unchanged`() {
        val message = "The provider took too long. Try again."
        assertEquals(message, NomiTranslations.localizeMessage(message, NomiLanguage.ENGLISH))
    }

    @Test
    fun `an AI validation failure reaches the screen translated`() {
        val shown = AiValidationException("No food was visible in the photo").safeAiMessage()
        assertEquals(
            "Auf dem Foto war kein Lebensmittel zu sehen",
            NomiTranslations.localizeMessage(shown, NomiLanguage.GERMAN),
        )
    }
}
