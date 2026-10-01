package com.nomi.app.ui.app

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.ProviderSelection
import com.nomi.app.data.preferences.withSupportedModel
import com.nomi.app.data.remote.ai.EXA_API_ENDPOINT
import com.nomi.app.data.remote.ai.GEMINI_API_ENDPOINT
import com.nomi.app.ui.settings.AiProviderEditorState
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException

/*
 * How a stored provider choice becomes something a request can run on: which endpoint and model
 * it resolves to, which secret holds its key, and what it is called on screen. Nothing here
 * touches the network or the secret store.
 */

internal fun AiProviderEditorState.toProviderSelection(
    pipeline: ProviderPipeline = ProviderPipeline.FOOD_INTERPRETATION,
): ProviderSelection = ProviderSelection(
    providerId = provider.toProviderId(),
    model = model.trim(),
    endpoint = endpoint.asHttpsEndpoint(),
).withSupportedModel(pipeline)


private fun String.asHttpsEndpoint(): String = trim().let { endpoint ->
    if ("://" in endpoint) endpoint else "https://$endpoint"
}
private fun ProviderSelection.resolvedEndpoint(): String {
    val resolved = when (providerId.toProviderKind()) {
        AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
        AiProviderKind.OPEN_ROUTER -> "https://openrouter.ai/api/v1"
        AiProviderKind.OPEN_AI -> "https://api.openai.com/v1"
        AiProviderKind.EXA_GEMINI -> GEMINI_API_ENDPOINT
        // Codex Easy publishes both a bare host and a /v1 base; Nomi appends OpenAI request
        // paths, so the versioned base is the one that resolves to /v1/chat/completions.
        AiProviderKind.CODEX_EASY -> "https://codex-easy.ai/v1"
        AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> endpoint?.trim()?.takeIf(String::isNotBlank)
            ?: error("Enter a provider endpoint in Settings.")
    }.trimEnd('/')
    val uri = runCatching { URI(resolved) }.getOrNull()
    require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
        "AI endpoints must use a valid HTTPS URL."
    }
    return resolved
}
/**
 * [timeoutDisabled] comes from the user's "Never time out" setting: research that runs long is
 * then waited out instead of being cut off.
 */
internal fun ProviderSelection.toRuntimeConfig(timeoutDisabled: Boolean = false): AiProviderConfig {
    val kind = providerId.toProviderKind()
    require(model.isNotBlank()) { "Choose a model in Settings." }
    val defaults = AiProviderConfig(kind, resolvedEndpoint(), model.trim())
    return if (timeoutDisabled) defaults.copy(timeoutMillis = null) else defaults
}

internal fun ProviderSelection.cacheIdentity(): String = listOf(
    providerId.trim().lowercase(Locale.ROOT),
    model.trim(),
    runCatching { resolvedEndpoint() }.getOrElse { endpoint.orEmpty().trim() },
    advancedParametersJson.orEmpty().trim(),
).joinToString(separator = "\u001f")

internal fun String.toProviderKind(): AiProviderKind = when (lowercase(Locale.ROOT)) {
    "perplexity" -> AiProviderKind.PERPLEXITY
    "openrouter" -> AiProviderKind.OPEN_ROUTER
    "openai" -> AiProviderKind.OPEN_AI
    "exa-gemini" -> AiProviderKind.EXA_GEMINI
    "codex-easy" -> AiProviderKind.CODEX_EASY
    else -> AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE
}
private fun AiProviderKind.toProviderId(): String = when (this) {
    AiProviderKind.PERPLEXITY -> "perplexity"
    AiProviderKind.OPEN_ROUTER -> "openrouter"
    AiProviderKind.OPEN_AI -> "openai"
    AiProviderKind.EXA_GEMINI -> "exa-gemini"
    AiProviderKind.CODEX_EASY -> "codex-easy"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> "custom"
}
internal fun String.displayProviderName(): String = when (lowercase(Locale.ROOT)) {
    "perplexity" -> "Perplexity"
    "openrouter" -> "OpenRouter"
    "openai" -> "OpenAI"
    "exa-gemini" -> "Exa + Gemini"
    "codex-easy" -> "Codex Easy"
    else -> "custom provider"
}
internal fun ProviderPipeline.displayName(): String = when (this) {
    ProviderPipeline.FOOD_RESEARCH -> "Food research"
    ProviderPipeline.FOOD_INTERPRETATION -> "Food interpretation"
    ProviderPipeline.PORTION_CHANGE -> "Portion changes"
    ProviderPipeline.VISION -> "Photo recognition"
    ProviderPipeline.SMART_FALLBACK -> "Fallback"
}

/** Exa retrieval plus Gemini extraction is the one provider that needs two keys. */
internal val ProviderSelection.usesExaGemini: Boolean
    get() = providerId.equals("exa-gemini", ignoreCase = true)

/** The provider's public site, shown as the first "source" while its research is starting. */
internal fun ProviderSelection.website(): String? = when (providerId.toProviderKind()) {
    AiProviderKind.PERPLEXITY -> "https://www.perplexity.ai"
    AiProviderKind.OPEN_ROUTER -> "https://openrouter.ai"
    AiProviderKind.OPEN_AI -> "https://openai.com"
    AiProviderKind.EXA_GEMINI -> "https://exa.ai"
    AiProviderKind.CODEX_EASY -> "https://codex-easy.ai"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> endpoint
}

internal fun ProviderPipeline.requiresWebResearch(): Boolean =
    this == ProviderPipeline.FOOD_RESEARCH || this == ProviderPipeline.SMART_FALLBACK

private fun ProviderSelection.sharesCredentialWith(other: ProviderSelection): Boolean =
    providerId.equals(other.providerId, ignoreCase = true) &&
        runCatching { resolvedEndpoint() }.getOrNull()
            ?.equals(runCatching { other.resolvedEndpoint() }.getOrNull(), ignoreCase = true) == true

internal fun smartFallbackCredentialIds(
    selection: ProviderSelection,
    primary: ProviderSelection,
): List<String> = buildList {
    add(secretId(selection))
    // Only a fallback on the same provider account may reuse the research key.
    if (selection.sharesCredentialWith(primary)) add(secretId(primary))
}.distinct()

internal suspend fun <T> runWithSmartFallback(
    primary: suspend () -> T,
    fallback: suspend () -> T,
    onFallback: suspend (Throwable) -> Unit = {},
    onFallbackSuccess: suspend (T) -> Unit = {},
): T = try {
    primary()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (primaryError: Throwable) {
    onFallback(primaryError)
    try {
        fallback().also { onFallbackSuccess(it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (fallbackError: Throwable) {
        // The configured primary provider's error is the actionable one; a misconfigured
        // fallback must not mask it.
        primaryError.addSuppressed(fallbackError)
        throw primaryError
    }
}

/**
 * Keys are scoped to the provider account, not to the pipeline that happens to use it. All five
 * pipelines run on the same OpenRouter key by default, so entering it once in any of them
 * configures the rest; a second provider still gets its own separate secret.
 */
internal fun secretId(selection: ProviderSelection): String = providerSecretId(
    providerId = selection.providerId,
    endpoint = selection.resolvedEndpoint(),
)

internal fun exaSecretId(): String = providerSecretId("exa", EXA_API_ENDPOINT)

private fun providerSecretId(providerId: String, endpoint: String): String {
    val material = "${providerId.lowercase(Locale.ROOT)}|${endpoint.lowercase(Locale.ROOT)}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    val token = digest.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "provider:$token"
}

internal fun String.normalizedApiKeyCharsOrNull(): CharArray? =
    trim().takeIf(String::isNotEmpty)?.toCharArray()

internal fun providerConnectionTestIntent(): ParsedFoodIntent = ParsedFoodIntent(
    originalText = "100 g apple",
    items = listOf(
        ParsedFoodItem(
            name = "apple",
            quantity = 100.0,
            unit = "g",
            gramsEquivalent = 100.0,
        ),
    ),
)
