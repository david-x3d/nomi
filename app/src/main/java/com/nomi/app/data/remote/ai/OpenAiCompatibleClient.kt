package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class OpenAiCompatibleClient(
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
    },
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout)
        expectSuccess = true
    },
) : AutoCloseable {

    suspend fun completeJson(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        systemPrompt: String,
        userPrompt: String,
    ): String = complete(
        config = config,
        credential = credential,
        messages = listOf(
            ChatMessage("system", JsonPrimitive(systemPrompt)),
            ChatMessage("user", JsonPrimitive(userPrompt)),
        ),
    )

    suspend fun completeVisionJson(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        prompt: String,
        base64Image: String,
        mediaType: String,
    ): String {
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", prompt)
            })
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", "data:$mediaType;base64,$base64Image")
                })
            })
        }
        return complete(
            config = config,
            credential = credential,
            messages = listOf(ChatMessage("user", content)),
        )
    }

    private suspend fun complete(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        messages: List<ChatMessage>,
    ): String {
        val endpoint = config.endpoint.trimEnd('/') + "/chat/completions"
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            bearerAuth(credential.revealForRequest())
            config.extraHeaders.forEach { (name, value) -> header(name, value) }
            setBody(chatCompletionRequest(config, messages))
            timeout { requestTimeoutMillis = config.timeoutMillis }
        }.body<ChatCompletionResponse>()

        return response.structuredContent()
    }

    override fun close() {
        httpClient.close()
    }
}

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: JsonElement,
)

@Serializable
internal data class ResponseFormat(val type: String = "json_object")

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: AssistantMessage)

@Serializable
private data class AssistantMessage(val content: JsonPrimitive? = null)

/**
 * Perplexity's Sonar API accepts text or JSON-schema response formats, but not OpenAI's
 * legacy `json_object` mode. OpenRouter forwards unsupported parameters to Perplexity and
 * rejects the request, so Sonar must rely on Nomi's explicit JSON-only prompts instead.
 */
internal fun AiProviderConfig.supportsJsonObjectResponseFormat(): Boolean {
    val normalizedModel = model.trim().lowercase(Locale.ROOT)
    return when (kind) {
        AiProviderKind.PERPLEXITY -> false
        AiProviderKind.OPEN_ROUTER -> !normalizedModel.startsWith("perplexity/")
        else -> true
    }
}

internal fun chatCompletionRequest(
    config: AiProviderConfig,
    messages: List<ChatMessage>,
): ChatCompletionRequest = ChatCompletionRequest(
    model = config.model,
    messages = messages,
    temperature = config.temperature,
    responseFormat = ResponseFormat().takeIf { config.supportsJsonObjectResponseFormat() },
)

private fun ChatCompletionResponse.structuredContent(): String {
    val raw = choices.firstOrNull()?.message?.content?.content
        ?: throw IllegalStateException("The AI provider returned no structured content")
    return extractJsonDocument(raw)
}

/** Decodes the same response envelope used by Ktor; kept internal for provider fixture tests. */
internal fun decodeChatCompletionPayload(json: Json, payload: String): String =
    json.decodeFromString<ChatCompletionResponse>(payload).structuredContent()

/**
 * Sonar can wrap prompt-requested JSON in a Markdown fence or append citations. Select the
 * first complete JSON object while respecting quoted braces, then let kotlinx.serialization
 * perform the strict schema validation.
 */
internal fun extractJsonDocument(raw: String): String {
    val content = raw.trim().removePrefix("\uFEFF")
    val start = content.indexOf('{')
    if (start < 0) return content

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until content.length) {
        when (content[index]) {
            '\\' -> if (inString) escaped = !escaped
            '"' -> {
                if (!escaped) inString = !inString
                escaped = false
            }
            '{' -> if (!inString) depth += 1
            '}' -> if (!inString) {
                depth -= 1
                if (depth == 0) return content.substring(start, index + 1)
            }
            else -> if (escaped) escaped = false
        }
    }
    return content
}
