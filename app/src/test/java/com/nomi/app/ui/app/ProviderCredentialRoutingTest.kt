package com.nomi.app.ui.app

import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.ProviderSelection
import com.nomi.app.data.security.SecretUnavailableException
import java.io.IOException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCredentialRoutingTest {
    @Test
    fun `api key input is trimmed and blank input is ignored`() {
        val chars = "  sk-example  ".normalizedApiKeyCharsOrNull()

        assertArrayEquals("sk-example".toCharArray(), chars)
        chars?.fill('\u0000')
        assertNull("  \t ".normalizedApiKeyCharsOrNull())
    }

    @Test
    fun `fallback reuses matching research credential independently of model`() {
        val primary = ProviderSelection(providerId = "perplexity", model = "sonar")
        val fallback = ProviderSelection(providerId = "perplexity", model = "sonar-pro")

        val ids = smartFallbackCredentialIds(fallback, primary)

        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        assertEquals(
            ids,
            smartFallbackCredentialIds(
                fallback.copy(model = "sonar-reasoning-pro"),
                primary.copy(model = "sonar-deep-research"),
            ),
        )
    }

    @Test
    fun `fallback does not borrow a credential from a different provider`() {
        val ids = smartFallbackCredentialIds(
            selection = ProviderSelection(providerId = "openai", model = "gpt-5.2"),
            primary = ProviderSelection(providerId = "perplexity", model = "sonar"),
        )

        assertEquals(1, ids.size)
    }

    @Test
    fun `only research pipelines run the research connection test`() {
        assertTrue(ProviderPipeline.FOOD_RESEARCH.requiresWebResearch())
        assertTrue(ProviderPipeline.SMART_FALLBACK.requiresWebResearch())
        assertFalse(ProviderPipeline.FOOD_INTERPRETATION.requiresWebResearch())
        assertFalse(ProviderPipeline.PORTION_CHANGE.requiresWebResearch())
        assertFalse(ProviderPipeline.VISION.requiresWebResearch())
    }

    @Test
    fun `unreadable stored key gets an actionable message`() {
        val error = IllegalStateException(
            "wrapped",
            SecretUnavailableException("Secure storage decryption failed"),
        )

        assertEquals(
            "Nomi couldn't read the stored API key. Remove it in Settings and enter it again.",
            error.safeAiMessage(),
        )
        assertEquals(
            "Nomi couldn't read the stored API key. Remove it in Settings and enter it again.",
            error.safeProviderConnectionMessage(),
        )
    }

    @Test
    fun `serialization and network failures get explicit messages`() {
        assertEquals(
            "The provider returned a response Nomi couldn't read. Check the selected model in Settings.",
            SerializationException("bad JSON").safeAiMessage(),
        )
        assertEquals(
            "Nomi couldn't reach the provider. Check the internet connection and endpoint.",
            IOException("connection reset").safeAiMessage(),
        )
    }

    @Test
    fun `an endpoint that answers with a web page points at the base URL, not the model`() {
        val error = IllegalStateException(
            "No transformation found: class io.ktor.utils.io.ByteBufferChannel -> " +
                "class ChatCompletionResponse, with response from " +
                "https://codex-easy.ai/chat/completions, Content-Type: text/html",
        )

        assertEquals(
            "That endpoint answered with a web page instead of an API response. Check the " +
                "base URL in Settings — an OpenAI-compatible endpoint usually ends in /v1.",
            error.safeAiMessage(),
        )
    }

    @Test
    fun `common http status text gets an explicit message when no response object is available`() {
        assertEquals(
            "The provider rate limit was reached. Wait a moment and try again.",
            IllegalStateException("HTTP 429 Too Many Requests").safeAiMessage(),
        )
    }
}
