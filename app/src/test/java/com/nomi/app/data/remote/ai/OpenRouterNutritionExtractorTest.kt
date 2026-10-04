package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.validation.AiValidationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterNutritionExtractorTest {
    private val responseHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val config = AiProviderConfig(
        kind = AiProviderKind.EXA_OPEN_ROUTER,
        endpoint = "https://openrouter.ai/api/v1",
        model = "z-ai/glm-5.3-flash",
        timeoutMillis = 5_000,
    )

    @Test
    fun `reads the extraction contract through OpenRouter under its cost limits`() = runBlocking {
        var body = ""
        val engine = MockEngine { request ->
            assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url.toString())
            assertEquals("Bearer openrouter-secret", request.headers[HttpHeaders.Authorization])
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"choices":[{"message":{"content":"{\"items\":[],\"overallConfidence\":null,\"error\":\"no source\"}"}}]}""",
                HttpStatusCode.OK,
                responseHeaders,
            )
        }

        val result = extractor(engine).extract(
            config = config,
            credential = AiRuntimeCredential.from("openrouter-secret"),
            systemPrompt = "Return JSON.",
            userPrompt = "Extract nutrition.",
        )

        assertEquals("no source", result.error)
        assertTrue(body, body.contains("\"model\":\"z-ai/glm-5.3-flash\""))
        assertTrue(body, body.contains("\"type\":\"json_schema\""))
        assertTrue(body, body.contains("\"name\":\"nomi_nutrition_extraction\""))
        assertTrue(body, body.contains("\"sourceBasisText\""))
        assertTrue(body, body.contains("\"max_price\":{\"prompt\":1.0,\"completion\":5.0}"))
        assertTrue(body, body.contains("\"require_parameters\":true"))
        assertTrue(body, body.contains("\"max_tokens\":8000"))
        assertTrue(body, body.contains("\"reasoning\":{\"effort\":\"low\""))
        // The routed endpoint may not accept a custom temperature.
        assertFalse(body, body.contains("temperature"))
        // With no preferred provider, OpenRouter picks the endpoint.
        assertFalse(body, body.contains("\"order\""))
    }

    @Test
    fun `a preferred provider is asked first and others stay as fallbacks`() = runBlocking {
        var body = ""
        val engine = MockEngine { request ->
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"choices":[{"message":{"content":"{\"items\":[]}"}}]}""",
                HttpStatusCode.OK,
                responseHeaders,
            )
        }

        extractor(engine).extract(
            config = config.copy(openRouterProviderOrder = listOf("baseten")),
            credential = AiRuntimeCredential.from("openrouter-secret"),
            systemPrompt = "Return JSON.",
            userPrompt = "Extract nutrition.",
        )

        assertTrue(body, body.contains("\"order\":[\"baseten\"]"))
        assertTrue(body, body.contains("\"max_price\""))
        assertFalse(body, body.contains("allow_fallbacks"))
    }

    @Test
    fun `a model outside the price ceiling is refused with a sentence about the model choice`() {
        val engine = MockEngine {
            respond(
                """{"error":{"message":"No endpoints found matching your data policy and parameters","code":404}}""",
                HttpStatusCode.NotFound,
                responseHeaders,
            )
        }

        val error = assertThrows(AiValidationException::class.java) {
            runBlocking {
                extractor(engine).extract(
                    config = config.copy(model = "anthropic/claude-sonnet-5.5"),
                    credential = AiRuntimeCredential.from("openrouter-secret"),
                    systemPrompt = "Return JSON.",
                    userPrompt = "Extract nutrition.",
                )
            }
        }

        assertEquals(OPENROUTER_RESEARCH_MODEL_REFUSED, error.message)
    }

    private fun extractor(engine: MockEngine): OpenRouterNutritionExtractor {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        val client = OpenAiCompatibleClient(
            json = json,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                expectSuccess = true
            },
        )
        return OpenRouterNutritionExtractor(client)
    }
}
