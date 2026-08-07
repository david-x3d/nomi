package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `openrouter perplexity request omits unsupported json object format`() {
        val config = config(AiProviderKind.OPEN_ROUTER, "perplexity/sonar")
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config,
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertFalse(config.supportsJsonObjectResponseFormat())
        assertFalse(encoded.contains("response_format"))
        assertTrue(encoded.contains("\"model\":\"perplexity/sonar\""))
    }

    @Test
    fun `direct perplexity request also omits unsupported json object format`() {
        val config = config(AiProviderKind.PERPLEXITY, "sonar")
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config,
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertFalse(encoded.contains("response_format"))
    }

    @Test
    fun `openrouter model supporting json object retains response format`() {
        val config = config(AiProviderKind.OPEN_ROUTER, "deepseek/deepseek-v4")
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config,
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertTrue(config.supportsJsonObjectResponseFormat())
        assertTrue(encoded.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun `sonar openrouter fixture extracts food json before citations`() {
        val fixture = """
            {
              "id": "gen-1754600000-AbCdEf",
              "provider": "Perplexity",
              "model": "perplexity/sonar",
              "object": "chat.completion",
              "choices": [{
                "finish_reason": "stop",
                "message": {
                  "role": "assistant",
                  "content": "{\"items\":[{\"name\":\"Salami pizza\",\"calories\":960}]}\n\nSources: [1]",
                  "annotations": [{"type":"url_citation","url_citation":{"url":"https://example.com"}}]
                }
              }],
              "citations": ["https://example.com"],
              "usage": {"prompt_tokens": 120, "completion_tokens": 40}
            }
        """.trimIndent()

        assertEquals(
            "{\"items\":[{\"name\":\"Salami pizza\",\"calories\":960}]}",
            decodeChatCompletionPayload(json, fixture),
        )
    }

    @Test
    fun `sonar markdown fixture extracts nested json with quoted braces`() {
        val content = """
            ```json
            {"ok":true,"note":"a {brace} in a string","nested":{"value":1}}
            ```
            [1] https://example.com/source
        """.trimIndent()

        assertEquals(
            "{\"ok\":true,\"note\":\"a {brace} in a string\",\"nested\":{\"value\":1}}",
            extractJsonDocument(content),
        )
    }

    private fun config(kind: AiProviderKind, model: String) = AiProviderConfig(
        kind = kind,
        endpoint = when (kind) {
            AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
            else -> "https://openrouter.ai/api/v1"
        },
        model = model,
    )
}
