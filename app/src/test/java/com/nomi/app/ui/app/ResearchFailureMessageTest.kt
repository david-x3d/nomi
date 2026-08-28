package com.nomi.app.ui.app

import com.nomi.app.ai.validation.NutritionFailureReason
import com.nomi.app.data.remote.ai.ProviderTemporarilyUnavailableException
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every research failure used to reach the user as one sentence. These check that each cause
 * keeps its own answer, in the user's language, and that the sentences stay distinct.
 */
class ResearchFailureMessageTest {

    private val itemReasons = listOf(
        NutritionFailureReason.MISSING_PORTION_WEIGHT,
        NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
        NutritionFailureReason.NO_SUITABLE_SOURCE,
        NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES,
        NutritionFailureReason.INVALID_NUTRITION_BASIS,
        NutritionFailureReason.PARSING_FAILURE,
    )

    @Test
    fun `each item-level cause has its own sentence`() {
        val templates = itemReasons.map { reason ->
            requireNotNull(researchFailureTemplate(reason)) { "no template for $reason" }
        }
        assertEquals(templates.size, templates.distinct().size)
        templates.forEach { assertTrue("missing placeholder in \"$it\"", it.contains("{0}")) }
    }

    @Test
    fun `transport causes keep the provider wording instead of naming the food`() {
        listOf(
            NutritionFailureReason.PROVIDER_TIMEOUT,
            NutritionFailureReason.PROVIDER_RATE_LIMITED,
            NutritionFailureReason.MODEL_UNAVAILABLE,
            NutritionFailureReason.PROVIDER_UNREACHABLE,
        ).forEach { assertNull(researchFailureTemplate(it)) }
    }

    @Test
    fun `the failed food is named in the user's language`() {
        val message = NomiTranslations.format(
            requireNotNull(
                researchFailureTemplate(NutritionFailureReason.MISSING_PORTION_WEIGHT),
            ),
            NomiLanguage.GERMAN,
            "Banane",
        )
        assertTrue(message, message.contains("Banane"))
        assertTrue(message, message.contains("Gewicht"))
        // The message that made every failure look identical is gone from this path.
        assertFalse(message, message.contains("alle Produkte"))
    }

    @Test
    fun `every item-level sentence is translated into every language`() {
        val untranslated = mutableListOf<String>()
        itemReasons.forEach { reason ->
            val english = requireNotNull(researchFailureTemplate(reason))
            (NomiLanguage.entries - NomiLanguage.ENGLISH).forEach { language ->
                val translated = NomiTranslations.translate(english, language)
                if (translated == english) untranslated += "${language.tag}: $reason"
            }
        }
        assertEquals(emptyList<String>(), untranslated)
    }

    @Test
    fun `transport failures are classified apart from each other`() {
        assertEquals(
            NutritionFailureReason.PROVIDER_TIMEOUT,
            HttpRequestTimeoutException("https://example.test", 1_000).nutritionFailureReason(),
        )
        assertEquals(
            NutritionFailureReason.PROVIDER_RATE_LIMITED,
            IOException(
                "wrapped",
                ProviderTemporarilyUnavailableException(
                    providerName = "Google Gemini",
                    statusCode = 429,
                    cause = IOException("429"),
                ),
            ).nutritionFailureReason(),
        )
        assertEquals(
            NutritionFailureReason.PROVIDER_UNREACHABLE,
            UnknownHostException("api.exa.ai").nutritionFailureReason(),
        )
        assertNull(IllegalStateException("something else").nutritionFailureReason())
    }

    @Test
    fun `a retried rate limit does not read as an outage`() {
        val rateLimited = ProviderTemporarilyUnavailableException(
            providerName = "Google Gemini",
            statusCode = 429,
            cause = IOException("429"),
        ).safeAiMessage()
        val unavailable = ProviderTemporarilyUnavailableException(
            providerName = "Google Gemini",
            statusCode = 503,
            cause = IOException("503"),
        ).safeAiMessage()

        assertNotNull(rateLimited)
        assertTrue(rateLimited, rateLimited.contains("rate limit"))
        assertTrue(unavailable, unavailable.contains("temporarily unavailable"))
        assertFalse(rateLimited, rateLimited == unavailable)
    }
}
