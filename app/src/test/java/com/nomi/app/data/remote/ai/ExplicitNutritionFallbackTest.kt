package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ExplicitNutritionFallbackTest {
    private val intent = ParsedFoodIntent(
        originalText = "100 g apple",
        items = listOf(ParsedFoodItem("apple", quantity = 100.0, unit = "g")),
    )
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val estimate = """{"items":[{"name":"apple","quantity":100,"unit":"g","calories":52,"proteinGrams":0.3,"carbohydrateGrams":14,"fatGrams":0.2,"sourceServingQuantity":100,"sourceServingUnit":"g","isEstimate":true}]}"""

    @Test
    fun `a rejected research answer does not silently send an estimate request`() {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("""{"choices":[{"message":{"content":"{\"error\":\"No source\"}"}}]}""", headers = headers)
        }
        client(engine).use { client ->
            val provider = provider(client, AiProviderConfig(AiProviderKind.PERPLEXITY, "https://api.perplexity.ai", "sonar"))
            assertThrows(Exception::class.java) { runBlocking { provider.researchNutrition(intent) } }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `an explicit Exa Gemini estimate uses the Gemini compatibility endpoint`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("$GEMINI_API_ENDPOINT/openai/chat/completions", request.url.toString())
            respond("""{"choices":[{"message":{"content":${JsonPrimitive(estimate)}}}]}""", headers = headers)
        }
        client(engine).use { client ->
            val result = provider(client, AiProviderConfig(AiProviderKind.EXA_GEMINI, GEMINI_API_ENDPOINT, "gemini-2.5-flash"))
                .estimateNutrition(intent)
            assertTrue(result.items.single().isEstimate)
            assertEquals(52.0, result.items.single().calories, 1e-9)
        }
    }

    @Test
    fun `an explicit Exa OpenRouter estimate retains price limits and provider preference`() = runBlocking {
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body, body.contains("\"max_price\":{\"prompt\":1.0,\"completion\":5.0}"))
            assertTrue(body, body.contains("\"order\":[\"baseten\"]"))
            assertEquals(JsonNull, Json.parseToJsonElement(body).jsonObject["temperature"])
            respond("""{"choices":[{"message":{"content":${JsonPrimitive(estimate)}}}]}""", headers = headers)
        }
        client(engine).use { client ->
            val config = AiProviderConfig(
                AiProviderKind.EXA_OPEN_ROUTER, "https://openrouter.ai/api/v1", "z-ai/glm-5.3-flash",
                openRouterProviderOrder = listOf("baseten"),
            )
            assertTrue(provider(client, config).estimateNutrition(intent).items.single().isEstimate)
        }
    }

    private fun client(engine: MockEngine) = OpenAiCompatibleClient(httpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
    })

    private fun provider(client: OpenAiCompatibleClient, config: AiProviderConfig): OpenAiCompatibleProviders {
        val credential = { AiRuntimeCredential.from("test-key") }
        return OpenAiCompatibleProviders(client, config, credential, config, credential, config, credential, config, credential)
    }
}
