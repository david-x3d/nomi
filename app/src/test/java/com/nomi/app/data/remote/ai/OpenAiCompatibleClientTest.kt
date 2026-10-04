package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import io.ktor.client.plugins.HttpTimeoutConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    fun `openrouter limits put a price ceiling and low reasoning on the request`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.OPEN_ROUTER, "z-ai/glm-5.3-flash"),
                listOf(ChatMessage("user", JsonPrimitive("Extract"))),
                maxTokens = 8_000,
                openRouterLimits = OpenRouterRequestLimits(
                    maxPromptPrice = 1.0,
                    maxCompletionPrice = 5.0,
                    reasoningEffort = "low",
                ),
            ),
        )

        assertTrue(encoded, encoded.contains("\"provider\":{\"max_price\":{\"prompt\":1.0,\"completion\":5.0},\"require_parameters\":true}"))
        assertTrue(encoded, encoded.contains("\"reasoning\":{\"effort\":\"low\",\"exclude\":true}"))
        assertTrue(encoded, encoded.contains("\"max_tokens\":8000"))
        assertFalse(encoded, encoded.contains("temperature"))
    }

    @Test
    fun `requests without limits carry no openrouter routing fields`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.OPEN_ROUTER, "perplexity/sonar"),
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertFalse(encoded, encoded.contains("\"provider\""))
        assertFalse(encoded, encoded.contains("\"reasoning\""))
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
    fun `direct perplexity food research uses strict nutrition json schema`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.PERPLEXITY, "sonar"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            ),
        )

        assertTrue(encoded.contains("\"response_format\":{\"type\":\"json_schema\""))
        assertTrue(encoded.contains("\"name\":\"nomi_nutrition_research\""))
        assertTrue(encoded.contains("\"sourceServingQuantity\""))
        assertFalse(encoded.contains("\"sourceUrl\""))
        assertFalse(encoded.contains("\"supportingSourceUrls\""))
    }

    @Test
    fun `openrouter omits response format to keep every route eligible`() {
        val config = config(AiProviderKind.OPEN_ROUTER, "deepseek/deepseek-v4-flash")
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config,
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertFalse(config.supportsJsonObjectResponseFormat())
        assertFalse(encoded.contains("response_format"))
    }

    @Test
    fun `openrouter free variant is preserved without optional routing constraints`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.OPEN_ROUTER, "moonshotai/kimi-k2.6:free"),
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertTrue(encoded.contains("\"model\":\"moonshotai/kimi-k2.6:free\""))
        assertFalse(encoded.contains("response_format"))
        assertFalse(encoded.contains("temperature"))
        assertFalse(encoded.contains("\"tools\""))
        assertFalse(encoded.contains("max_tool_calls"))
    }

    @Test
    fun `custom compatible models keep the configured temperature`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE, "custom-model"),
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertTrue(encoded.contains("\"temperature\":0.1"))
    }

    @Test
    fun `openrouter sonar food research enables search and fetch server tools`() {
        val encoded = json.encodeToString(
            openRouterResponsesResearchRequest(
                config(AiProviderKind.OPEN_ROUTER, "perplexity/sonar"),
                systemPrompt = "Return validated JSON",
                userPrompt = "Research this food",
            ),
        )

        assertFalse(encoded.contains("response_format"))
        assertTrue(encoded.contains("\"type\":\"openrouter:web_search\""))
        assertTrue(encoded.contains("\"type\":\"openrouter:web_fetch\""))
        assertTrue(encoded.contains("\"max_tool_calls\":15"))
        assertFalse(encoded.contains("\"plugins\""))
    }

    @Test
    fun `openrouter GPT food research configures agentic search and page fetch`() {
        val encoded = json.encodeToString(
            openRouterResponsesResearchRequest(
                config(AiProviderKind.OPEN_ROUTER, "openai/gpt-5.6-sol"),
                systemPrompt = "Return validated JSON",
                userPrompt = "Research this food",
            ),
        )

        assertTrue(encoded.contains("\"model\":\"openai/gpt-5.6-sol\""))
        assertTrue(encoded.contains("\"type\":\"openrouter:web_search\""))
        assertTrue(encoded.contains("\"max_results\":5"))
        assertTrue(encoded.contains("\"max_total_results\":15"))
        assertTrue(encoded.contains("\"search_context_size\":\"high\""))
        assertTrue(encoded.contains("\"type\":\"openrouter:web_fetch\""))
        assertTrue(encoded.contains("\"engine\":\"openrouter\""))
        assertTrue(encoded.contains("\"max_content_tokens\":50000"))
        assertFalse(encoded.contains("response_format"))
        assertFalse(encoded.contains("web_search_options"))
        assertFalse(encoded.contains("temperature"))
        assertFalse(encoded.contains("\"plugins\""))
    }

    @Test
    fun `openai food research explicitly enables web search options`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.OPEN_AI, "gpt-4o-search-preview"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            ),
        )

        assertTrue(encoded.contains("\"web_search_options\":{\"search_context_size\":\"high\"}"))
        assertFalse(encoded.contains("\"tools\""))
        assertFalse(encoded.contains("max_tool_calls"))
    }

    @Test
    fun `openai food research rejects models without web search support upfront`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            chatCompletionRequest(
                config(AiProviderKind.OPEN_AI, "gpt-4.1-mini"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            )
        }

        assertTrue(error.message.orEmpty().contains("gpt-4o-search-preview"))
    }

    @Test
    fun `nutrition research schema allows integrity fields and the error escape hatch`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.PERPLEXITY, "sonar"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            ),
        )

        assertTrue(encoded.contains("\"sourceProductName\""))
        assertTrue(encoded.contains("\"sourceDomain\""))
        assertTrue(encoded.contains("\"error\""))
        assertTrue(encoded.contains("\"minItems\":0"))
    }

    @Test
    fun `custom food research provider is allowed without provider specific search options`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE, "custom-model"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            ),
        )

        assertTrue(encoded.contains("\"model\":\"custom-model\""))
        assertFalse(encoded.contains("web_search_options"))
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

        val completion = decodeWebSearchCompletionPayload(json, fixture)
        val expected = "{\"items\":[{\"name\":\"Salami pizza\",\"calories\":960}]}"
        assertEquals(expected, completion.content)
        assertEquals(setOf("https://example.com"), completion.evidenceUrls)
        assertEquals(expected, decodeChatCompletionPayload(json, fixture))
    }

    @Test
    fun `explicit null provider metadata decodes to an uncited completion`() {
        val fixture = """
            {
              "choices": [{
                "message": {
                  "content": "{\"ok\":true}",
                  "annotations": null
                }
              }],
              "citations": null,
              "search_results": null
            }
        """.trimIndent()

        val completion = decodeWebSearchCompletionPayload(json, fixture)

        // Missing citations cost the food its VERIFIED status downstream, not the whole entry.
        assertEquals("{\"ok\":true}", completion.content)
        assertTrue(completion.evidenceUrls.isEmpty())
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

    @Test
    fun `gemini posts to the openai surface under the documented api root`() {
        val config = AiProviderConfig(
            kind = AiProviderKind.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta/",
            model = "gemini-2.5-flash-lite",
        )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            config.chatCompletionsUrl(),
        )
        // Every other provider is addressed exactly where its base URL says.
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            config(AiProviderKind.OPEN_ROUTER, "perplexity/sonar").chatCompletionsUrl(),
        )
    }

    @Test
    fun `gemini relies on the json-only prompt and keeps its temperature`() {
        val config = config(AiProviderKind.GEMINI, "gemini-2.5-flash-lite")
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config,
                listOf(ChatMessage("user", JsonPrimitive("Reply with JSON"))),
            ),
        )

        assertFalse(config.supportsJsonObjectResponseFormat())
        assertFalse(encoded.contains("response_format"))
        assertTrue(encoded.contains("\"temperature\":0.1"))
    }

    @Test
    fun `gemini on its own is refused for research, which has to cite pages`() {
        val error = runCatching {
            chatCompletionRequest(
                config(AiProviderKind.GEMINI, "gemini-2.5-flash-lite"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Exa + Gemini"))
    }

    @Test
    fun `research without provider citations is kept without evidence`() {
        val fixture = """
            {
              "output": [
                {"type": "message", "content": [{
                  "type": "output_text",
                  "text": "{\"items\":[{\"name\":\"Penne Rigate\",\"calories\":359}]}",
                  "annotations": []
                }]}
              ]
            }
        """.trimIndent()

        val completion = decodeOpenRouterResponsesResearchPayload(json, fixture)

        assertEquals("{\"items\":[{\"name\":\"Penne Rigate\",\"calories\":359}]}", completion.content)
        assertTrue(completion.evidenceUrls.isEmpty())
    }

    @Test
    fun `configured timeout is used for both the request and the socket`() {
        val config = config(AiProviderKind.OPEN_ROUTER, "openai/gpt-5.6-sol")

        assertEquals(45_000L, config.timeoutMillis)
        assertEquals(45_000L, config.effectiveTimeoutMillis())
    }

    @Test
    fun `a disabled timeout lets a slow research call run to completion`() {
        val config = config(AiProviderKind.OPEN_ROUTER, "openai/gpt-5.6-sol")
            .copy(timeoutMillis = null)

        assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, config.effectiveTimeoutMillis())
    }

    /**
     * Sonar on OpenRouter is a chat-completions model that searches by itself. Sending it the
     * Responses-API server tools made every food-research request fail with HTTP 400, so the
     * routing flag that keeps it on the chat path is worth pinning down by name.
     */
    @Test
    fun `openrouter sonar models search natively and must not take the server-tool path`() {
        listOf(
            "perplexity/sonar",
            "perplexity/sonar-pro",
            "perplexity/sonar-reasoning",
            "perplexity/sonar-deep-research",
            "perplexity/llama-3.1-sonar-large-128k-online",
            "PERPLEXITY/SONAR",
        ).forEach { model ->
            assertTrue(
                "$model searches natively and cannot accept openrouter server tools",
                config(AiProviderKind.OPEN_ROUTER, model).usesNativeWebSearch(),
            )
        }
    }

    @Test
    fun `an openrouter online variant also searches natively`() {
        assertTrue(config(AiProviderKind.OPEN_ROUTER, "openai/gpt-5.6-sol:online").usesNativeWebSearch())
    }

    @Test
    fun `an ordinary openrouter model still uses the server-tool research path`() {
        listOf("openai/gpt-5.6-sol", "anthropic/claude-sonnet-5", "google/gemini-2.5-flash")
            .forEach { model ->
                assertFalse(
                    "$model has no search of its own and needs the server tools",
                    config(AiProviderKind.OPEN_ROUTER, model).usesNativeWebSearch(),
                )
            }
    }

    @Test
    fun `native web search is an openrouter concern only`() {
        // Direct Perplexity already has its own request path and must not be diverted by this flag.
        assertFalse(config(AiProviderKind.PERPLEXITY, "sonar").usesNativeWebSearch())
        assertFalse(config(AiProviderKind.GEMINI, "gemini-2.5-flash-lite").usesNativeWebSearch())
    }

    @Test
    fun `openrouter sonar research is a plain chat request with no server tools`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.OPEN_ROUTER, "perplexity/sonar"),
                listOf(ChatMessage("user", JsonPrimitive("Research this food"))),
                requireWebSearch = true,
            ),
        )

        // The three fields that make OpenRouter reject a Sonar request.
        assertFalse(encoded.contains("openrouter:web_search"))
        assertFalse(encoded.contains("openrouter:web_fetch"))
        assertFalse(encoded.contains("max_tool_calls"))
        // Sonar searches on its own, so it needs no web_search_options either.
        assertFalse(encoded.contains("web_search_options"))
        assertTrue(encoded.contains("\"model\":\"perplexity/sonar\""))
    }

    @Test
    fun `bounded completions use model supported token field`() {
        val encoded = json.encodeToString(
            chatCompletionRequest(
                config(AiProviderKind.EXA_GEMINI, "gemini-2.5-flash"),
                listOf(ChatMessage("user", JsonPrimitive("Extract nutrition"))),
                maxTokens = 4_096,
            ),
        )

        assertTrue(encoded.contains("\"max_tokens\":4096"))
        assertFalse(encoded.contains("\"max_completion_tokens\""))
    }

    private fun config(kind: AiProviderKind, model: String) = AiProviderConfig(
        kind = kind,
        endpoint = when (kind) {
            AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
            AiProviderKind.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
            else -> "https://openrouter.ai/api/v1"
        },
        model = model,
    )
}
