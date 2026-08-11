package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaGeminiHttpClientTest {
    private val responseHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `Exa retries a transient 503 and keeps official API authentication`() = runBlocking {
        var requests = 0
        val delays = mutableListOf<Long>()
        val engine = MockEngine { request ->
            requests += 1
            assertEquals("https://api.exa.ai/search", request.url.toString())
            assertEquals("exa-secret", request.headers["x-api-key"])
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("\"numResults\":4"))
            if (requests == 1) {
                respond("{\"error\":\"temporarily unavailable\"}", HttpStatusCode.ServiceUnavailable, responseHeaders)
            } else {
                respond(
                    """{"requestId":"request-1","results":[{"title":"Label","url":"https://example.com/label","text":"100 kcal","highlights":["100 kcal"]}]}""",
                    HttpStatusCode.OK,
                    responseHeaders,
                )
            }
        }
        client(engine, delays).use { client ->
            val response = client.search(
                query = "test nutrition",
                credential = AiRuntimeCredential.from("exa-secret"),
                timeoutMillis = 5_000,
                resultLimit = 4,
            )

            assertEquals("request-1", response.requestId)
            assertEquals(1, response.results.size)
        }
        assertEquals(2, requests)
        assertEquals(listOf(750L), delays)
    }

    @Test
    fun `Gemini retries 503 through Google endpoint without deprecated temperature`() = runBlocking {
        var requests = 0
        val delays = mutableListOf<Long>()
        val requestBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += 1
            assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                request.url.toString(),
            )
            assertEquals("gemini-secret", request.headers["x-goog-api-key"])
            requestBodies += (request.body as OutgoingContent.ByteArrayContent)
                .bytes().decodeToString()
            if (requests == 1) {
                respond("{\"error\":\"overloaded\"}", HttpStatusCode.ServiceUnavailable, responseHeaders)
            } else {
                respond(
                    """{"candidates":[{"content":{"parts":[{"text":"{\"items\":[],\"overallConfidence\":null,\"error\":\"not found\"}"}]}}]}""",
                    HttpStatusCode.OK,
                    responseHeaders,
                )
            }
        }
        client(engine, delays).use { client ->
            val result = client.extract(
                config = AiProviderConfig(
                    kind = AiProviderKind.EXA_GEMINI,
                    endpoint = GEMINI_API_ENDPOINT,
                    model = DEFAULT_GEMINI_NUTRITION_MODEL,
                    timeoutMillis = 5_000,
                ),
                credential = AiRuntimeCredential.from("gemini-secret"),
                systemPrompt = "Return JSON.",
                userPrompt = "Extract nutrition.",
            )

            assertEquals("not found", result.error)
            assertTrue(result.items.isEmpty())
        }
        assertEquals(2, requests)
        assertEquals(listOf(750L), delays)
        assertFalse(requestBodies.any { "temperature" in it })
    }

    @Test
    fun `retry backoff is exponential and honors bounded Retry-After`() {
        assertEquals(750L, transientRetryDelayMillis(attempt = 0, retryAfterMillis = null))
        assertEquals(1_500L, transientRetryDelayMillis(attempt = 1, retryAfterMillis = null))
        assertEquals(9_000L, transientRetryDelayMillis(attempt = 2, retryAfterMillis = 9_000L))
        assertEquals(10_000L, transientRetryDelayMillis(attempt = 2, retryAfterMillis = 60_000L))
    }

    @Test
    fun `persistently unavailable Gemini 3_6 falls back to stable 2_5`() = runBlocking {
        val requestedModels = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val engine = MockEngine { request ->
            val model = request.url.encodedPath.substringAfter("/models/").substringBefore(':')
            requestedModels += model
            if (model == "gemini-3.6-flash") {
                respond("{\"error\":\"overloaded\"}", HttpStatusCode.ServiceUnavailable, responseHeaders)
            } else {
                respond(
                    """{"candidates":[{"content":{"parts":[{"text":"{\"ok\":true}"}]}}]}""",
                    HttpStatusCode.OK,
                    responseHeaders,
                )
            }
        }

        client(engine, delays).use { client ->
            val result = client.generateStructuredJson(
                config = AiProviderConfig(
                    kind = AiProviderKind.EXA_GEMINI,
                    endpoint = GEMINI_API_ENDPOINT,
                    model = "gemini-3.6-flash",
                    timeoutMillis = 5_000,
                ),
                credential = AiRuntimeCredential.from("gemini-secret"),
                systemPrompt = "Return JSON.",
                userPrompt = "Confirm.",
            )

            assertEquals("{\"ok\":true}", result)
        }
        assertEquals(
            listOf(
                "gemini-3.6-flash",
                "gemini-3.6-flash",
                "gemini-3.6-flash",
                "gemini-3.6-flash",
                "gemini-2.5-flash",
            ),
            requestedModels,
        )
        assertEquals(listOf(750L, 1_500L, 3_000L), delays)
    }

    @Test
    fun `exhausted 503 retries identify Google Gemini`() {
        var requests = 0
        val delays = mutableListOf<Long>()
        val engine = MockEngine {
            requests += 1
            respond("{\"error\":\"overloaded\"}", HttpStatusCode.ServiceUnavailable, responseHeaders)
        }

        val error = assertThrows(ProviderTemporarilyUnavailableException::class.java) {
            runBlocking {
                client(engine, delays).use { client ->
                    client.generateStructuredJson(
                        config = AiProviderConfig(
                            kind = AiProviderKind.EXA_GEMINI,
                            endpoint = GEMINI_API_ENDPOINT,
                            model = DEFAULT_GEMINI_NUTRITION_MODEL,
                            timeoutMillis = 5_000,
                        ),
                        credential = AiRuntimeCredential.from("gemini-secret"),
                        systemPrompt = "Return JSON.",
                        userPrompt = "Extract nutrition.",
                    )
                }
            }
        }

        assertEquals("Google Gemini", error.providerName)
        assertEquals(503, error.statusCode)
        assertEquals(4, requests)
        assertEquals(listOf(750L, 1_500L, 3_000L), delays)
    }

    private fun client(engine: MockEngine, delays: MutableList<Long>): ExaGeminiHttpClient {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        return ExaGeminiHttpClient(
            json = json,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
                expectSuccess = true
            },
            retryDelay = { delays += it },
        )
    }
}
