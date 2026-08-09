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
import java.net.URI
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

    /**
     * Completes nutrition research only through a provider path with explicit live search.
     * Citation URLs come from provider metadata rather than model-authored JSON.
     */
    internal suspend fun completeWebSearchJson(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        systemPrompt: String,
        userPrompt: String,
    ): WebSearchCompletion {
        if (config.kind == AiProviderKind.OPEN_ROUTER) {
            return completeOpenRouterResponsesResearch(
                config = config,
                credential = credential,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
            )
        }
        return completeResponse(
            config = config,
            credential = credential,
            messages = listOf(
                ChatMessage("system", JsonPrimitive(systemPrompt)),
                ChatMessage("user", JsonPrimitive(userPrompt)),
            ),
            requireWebSearch = true,
        ).webSearchCompletion()
    }

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
    ): String = completeResponse(config, credential, messages).structuredContent()

    private suspend fun completeResponse(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        messages: List<ChatMessage>,
        requireWebSearch: Boolean = false,
    ): ChatCompletionResponse {
        val endpoint = config.endpoint.trimEnd('/') + "/chat/completions"
        return httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            bearerAuth(credential.revealForRequest())
            config.extraHeaders.forEach { (name, value) -> header(name, value) }
            setBody(chatCompletionRequest(config, messages, requireWebSearch))
            timeout { requestTimeoutMillis = config.timeoutMillis }
        }.body<ChatCompletionResponse>()
    }

    private suspend fun completeOpenRouterResponsesResearch(
        config: AiProviderConfig,
        credential: AiRuntimeCredential,
        systemPrompt: String,
        userPrompt: String,
    ): WebSearchCompletion {
        val endpoint = config.endpoint.trimEnd('/') + "/responses"
        return httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            bearerAuth(credential.revealForRequest())
            config.extraHeaders.forEach { (name, value) -> header(name, value) }
            setBody(
                openRouterResponsesResearchRequest(config, systemPrompt, userPrompt),
            )
            timeout { requestTimeoutMillis = config.timeoutMillis }
        }.body<JsonObject>().openRouterResponsesWebCompletion()
    }

    override fun close() {
        httpClient.close()
    }
}

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    @SerialName("web_search_options") val webSearchOptions: WebSearchOptions? = null,
)

@Serializable
internal data class OpenRouterResponsesResearchRequest(
    val model: String,
    val instructions: String,
    val input: String,
    val tools: List<OpenRouterServerTool>,
    @SerialName("max_tool_calls") val maxToolCalls: Int,
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: JsonElement,
)

@Serializable
internal data class ResponseFormat(
    val type: String = "json_object",
    @SerialName("json_schema") val jsonSchema: JsonSchemaDefinition? = null,
)

@Serializable
internal data class JsonSchemaDefinition(
    val name: String,
    val schema: JsonObject,
)

@Serializable
internal data class WebSearchOptions(
    @SerialName("search_context_size") val searchContextSize: String = "high",
)

@Serializable
internal data class OpenRouterServerTool(
    val type: String,
    val parameters: OpenRouterServerToolParameters,
)

@Serializable
internal data class OpenRouterServerToolParameters(
    val engine: String,
    @SerialName("max_results") val maxResults: Int? = null,
    @SerialName("max_total_results") val maxTotalResults: Int? = null,
    @SerialName("max_uses") val maxUses: Int,
    @SerialName("search_context_size") val searchContextSize: String? = null,
    @SerialName("max_content_tokens") val maxContentTokens: Int? = null,
)

internal data class WebSearchCompletion(
    val content: String,
    val evidenceUrls: Set<String>,
    val fetchedUrls: Set<String> = emptySet(),
    val requiresFetchedBrandedSource: Boolean = false,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
    val citations: List<String>? = null,
    @SerialName("search_results") val searchResults: List<SearchResult>? = null,
)

@Serializable
private data class ChatChoice(val message: AssistantMessage)

@Serializable
private data class AssistantMessage(
    val content: JsonPrimitive? = null,
    val annotations: List<MessageAnnotation>? = null,
)

@Serializable
private data class MessageAnnotation(
    val type: String? = null,
    @SerialName("url_citation") val urlCitation: UrlCitation? = null,
)

@Serializable
private data class UrlCitation(val url: String? = null)

@Serializable
private data class SearchResult(val url: String? = null)

/**
 * OpenRouter variants can route to endpoints with different optional-parameter support.
 * Omitting legacy `json_object` mode keeps `:free`, `:online`, and chained variants eligible;
 * Nomi's JSON-only prompts and strict app-side decoding remain authoritative.
 */
internal fun AiProviderConfig.supportsJsonObjectResponseFormat(): Boolean {
    return when (kind) {
        AiProviderKind.PERPLEXITY,
        AiProviderKind.OPEN_ROUTER,
        -> false
        else -> true
    }
}

/**
 * OpenRouter variants can also differ in sampling-parameter support, so let the routed endpoint
 * use its default. Direct OpenAI gpt-5 and o-series models likewise require their default.
 */
internal fun AiProviderConfig.supportsCustomTemperature(): Boolean {
    if (kind == AiProviderKind.OPEN_ROUTER) return false
    val normalized = model.trim().lowercase(Locale.ROOT).removePrefix("openai/")
    return !(normalized.startsWith("gpt-5") || normalized.startsWith("o1") ||
        normalized.startsWith("o3") || normalized.startsWith("o4"))
}

internal fun chatCompletionRequest(
    config: AiProviderConfig,
    messages: List<ChatMessage>,
    requireWebSearch: Boolean = false,
): ChatCompletionRequest = ChatCompletionRequest(
    model = config.model,
    messages = messages,
    temperature = config.temperature.takeIf { config.supportsCustomTemperature() },
    responseFormat = when {
        // OpenRouter search spans providers with different structured-output constraints, so
        // its research path relies on the JSON-only prompt plus Nomi's app-side validation.
        requireWebSearch && config.kind == AiProviderKind.PERPLEXITY ->
            nutritionResearchResponseFormat()
        requireWebSearch && config.kind == AiProviderKind.OPEN_ROUTER -> null
        config.supportsJsonObjectResponseFormat() -> ResponseFormat()
        else -> null
    },
    webSearchOptions = WebSearchOptions().takeIf {
        requireWebSearch && config.kind == AiProviderKind.OPEN_AI
    },
).also {
    require(!requireWebSearch || config.kind != AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE) {
        "Configure Food research with Perplexity, OpenRouter, or OpenAI in Settings; " +
            "custom endpoints cannot guarantee live web search."
    }
    // OpenAI accepts web_search_options only on its search models and 400s otherwise.
    require(
        !requireWebSearch || config.kind != AiProviderKind.OPEN_AI ||
            config.model.contains("search", ignoreCase = true),
    ) {
        "Configure Food research with an OpenAI web-search model such as " +
            "gpt-4o-search-preview, or use Perplexity/OpenRouter."
    }
}

internal fun openRouterResponsesResearchRequest(
    config: AiProviderConfig,
    systemPrompt: String,
    userPrompt: String,
): OpenRouterResponsesResearchRequest {
    require(config.kind == AiProviderKind.OPEN_ROUTER)
    return OpenRouterResponsesResearchRequest(
        model = config.model,
        instructions = systemPrompt,
        input = userPrompt,
        tools = openRouterResearchTools(),
        maxToolCalls = 15,
    )
}

/**
 * OpenRouter's deprecated web plugin injects one set of snippets and cannot follow a discovered
 * product URL. Server tools let the model search again and fetch the actual manufacturer page.
 */
private fun openRouterResearchTools(): List<OpenRouterServerTool> = listOf(
    OpenRouterServerTool(
        type = "openrouter:web_search",
        parameters = OpenRouterServerToolParameters(
            engine = "exa",
            maxResults = 5,
            maxTotalResults = 15,
            maxUses = 3,
            searchContextSize = "high",
        ),
    ),
    OpenRouterServerTool(
        type = "openrouter:web_fetch",
        parameters = OpenRouterServerToolParameters(
            engine = "openrouter",
            maxUses = 6,
            maxContentTokens = 50_000,
        ),
    ),
)

private fun ChatCompletionResponse.webSearchCompletion(): WebSearchCompletion {
    val evidenceUrls = buildSet {
        citations.orEmpty().forEach { addValidWebUrl(it) }
        searchResults.orEmpty().forEach { result ->
            result.url?.let { addValidWebUrl(it) }
        }
        choices.forEach { choice ->
            choice.message.annotations.orEmpty().forEach { annotation ->
                if (annotation.type == null || annotation.type == "url_citation") {
                    annotation.urlCitation?.url?.let { addValidWebUrl(it) }
                }
            }
        }
    }
    check(evidenceUrls.isNotEmpty()) {
        "Configure Food research with a provider that returns live web-search citations; " +
            "this response contained none."
    }
    return WebSearchCompletion(
        content = structuredContent(),
        evidenceUrls = evidenceUrls,
    )
}

private fun JsonObject.openRouterResponsesWebCompletion(): WebSearchCompletion {
    val output = this["output"] as? JsonArray
        ?: throw IllegalStateException("OpenRouter returned no Responses output")
    val evidenceUrls = linkedSetOf<String>()
    val fetchedUrls = linkedSetOf<String>()
    val contentParts = mutableListOf<String>()

    output.filterIsInstance<JsonObject>().forEach { item ->
        when ((item["type"] as? JsonPrimitive)?.content) {
            "openrouter:web_search" -> {
                val sources = (item["action"] as? JsonObject)?.get("sources") as? JsonArray
                sources.orEmpty().filterIsInstance<JsonObject>().forEach { source ->
                    (source["url"] as? JsonPrimitive)?.content?.let(evidenceUrls::addValidWebUrl)
                }
            }
            "openrouter:web_fetch" -> {
                val status = (item["status"] as? JsonPrimitive)?.content
                val httpStatus = (item["httpStatus"] as? JsonPrimitive)?.content?.toIntOrNull()
                if (status == "completed" && (httpStatus == null || httpStatus in 200..299)) {
                    val url = (item["url"] as? JsonPrimitive)?.content
                    canonicalWebUrlOrNull(url)?.let { fetchedUrl ->
                        fetchedUrls += fetchedUrl
                        evidenceUrls += fetchedUrl
                    }
                }
            }
            "message" -> {
                val parts = item["content"] as? JsonArray
                parts.orEmpty().filterIsInstance<JsonObject>().forEach { part ->
                    if ((part["type"] as? JsonPrimitive)?.content == "output_text") {
                        (part["text"] as? JsonPrimitive)?.content?.let(contentParts::add)
                        val annotations = part["annotations"] as? JsonArray
                        annotations.orEmpty().filterIsInstance<JsonObject>().forEach { annotation ->
                            val nested = annotation["url_citation"] as? JsonObject
                            val url = (annotation["url"] as? JsonPrimitive)?.content
                                ?: (nested?.get("url") as? JsonPrimitive)?.content
                            url?.let(evidenceUrls::addValidWebUrl)
                        }
                    }
                }
            }
        }
    }

    check(evidenceUrls.isNotEmpty()) {
        "OpenRouter returned no web-search or web-fetch evidence."
    }
    val raw = contentParts.joinToString("\n").takeIf(String::isNotBlank)
        ?: throw IllegalStateException("OpenRouter returned no structured Responses content")
    return WebSearchCompletion(
        content = extractJsonDocument(raw),
        evidenceUrls = evidenceUrls,
        fetchedUrls = fetchedUrls,
        requiresFetchedBrandedSource = true,
    )
}

private fun MutableSet<String>.addValidWebUrl(value: String) {
    canonicalWebUrlOrNull(value)?.let { add(it) }
}

internal fun canonicalWebUrlOrNull(value: String?): String? {
    val uri = runCatching { URI(value?.trim().orEmpty()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)
    if (scheme !in setOf("http", "https") || host.isNullOrBlank()) return null
    val path = uri.path?.takeIf(String::isNotBlank) ?: "/"
    return runCatching {
        URI(scheme, uri.userInfo, host, uri.port, path, uri.query, null)
            .toASCIIString()
            .removeSuffix("/")
    }.getOrNull()
}

/** Decodes a search-backed response envelope for provider fixture tests. */
internal fun decodeWebSearchCompletionPayload(json: Json, payload: String): WebSearchCompletion =
    json.decodeFromString<ChatCompletionResponse>(payload).webSearchCompletion()
internal fun decodeOpenRouterResponsesResearchPayload(
    json: Json,
    payload: String,
): WebSearchCompletion = json.decodeFromString<JsonObject>(payload).openRouterResponsesWebCompletion()

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
