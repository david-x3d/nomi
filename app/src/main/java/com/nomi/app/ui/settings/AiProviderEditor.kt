package com.nomi.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.data.preferences.DEFAULT_EXA_OPENROUTER_MODEL
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_MODEL
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_RESEARCH_MODEL
import com.nomi.app.data.remote.ai.GEMINI_API_ENDPOINT
import com.nomi.app.ui.components.NomiFieldShape
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiSecretField
import com.nomi.app.ui.components.NomiSecureWindow
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiMessage
import com.nomi.app.ui.localization.nomiString
import java.net.URI

data class AiProviderEditorState(
    val purpose: String,
    val provider: AiProviderKind,
    val endpoint: String,
    val model: String,
    val apiKeyInput: String = "",
    val searchApiKeyInput: String = "",
    val hasStoredApiKey: Boolean = false,
    val hasStoredSearchApiKey: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isSaving: Boolean = false,
    val isRemovingKey: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * One task's provider, on a page of its own.
 *
 * It used to be a dialog, which is a shape for a question with one answer. This is a form - a
 * provider, a model, one or two keys, a test - and in a dialog the two buttons that finish it
 * scrolled away under the keyboard. Here the form scrolls and Test and Save stay where they are.
 *
 * [onProviderSelected] is separate from [onStateChanged] because switching provider changes which
 * stored key applies, and only the host can look that up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiProviderEditorScreen(
    state: AiProviderEditorState,
    onStateChanged: (AiProviderEditorState) -> Unit,
    onProviderSelected: (AiProviderKind) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onRemoveStoredKey: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The app-wide preferred OpenRouter provider, edited here too for OpenRouter tasks. */
    openRouterPreferredProvider: String = "",
    onOpenRouterPreferredProviderChanged: (String) -> Unit = {},
) {
    NomiSecureWindow()
    val uriHandler = LocalUriHandler.current
    val busy = state.isTesting || state.isSaving || state.isRemovingKey
    val configurationError = state.configurationError(
        blankModelMessage = nomiString("Enter a model name."),
        missingEndpointMessage = nomiString("Enter an API endpoint."),
        invalidEndpointMessage = nomiString("Enter a valid HTTPS API endpoint."),
    )
    val usesExaGemini = state.provider == AiProviderKind.EXA_GEMINI
    val usesExaOpenRouter = state.provider == AiProviderKind.EXA_OPEN_ROUTER
    val usesExa = usesExaGemini || usesExaOpenRouter
    val hasReasoningKey = state.hasStoredApiKey || state.apiKeyInput.isNotBlank()
    val hasSearchKey = !usesExa ||
        state.hasStoredSearchApiKey || state.searchApiKeyInput.isNotBlank()
    val canTest = configurationError == null && hasReasoningKey && hasSearchKey && !busy

    SettingsSubpageScaffold(
        title = state.purpose.localizedPurpose(),
        onBack = { if (!busy) onBack() },
        modifier = modifier.imePadding(),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = canTest,
                    shape = NomiShapes.Action,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        if (state.isTesting) nomiString("Testing…") else nomiString("Test connection"),
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = configurationError == null && !busy,
                    shape = NomiShapes.Action,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        if (state.isSaving) nomiString("Saving…") else nomiString("Save"),
                        maxLines = 1,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormLabel(nomiString("Provider"))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                providersFor(state.purpose).forEach { provider ->
                    FilterChip(
                        selected = state.provider == provider,
                        onClick = { if (state.provider != provider) onProviderSelected(provider) },
                        enabled = !busy,
                        shape = NomiShapes.Action,
                        label = { Text(provider.localizedDisplayName()) },
                    )
                }
            }
            recommendedProviderFor(state.purpose)?.let { recommended ->
                Text(
                    text = nomiFormat("Recommended: {0}", recommended.localizedDisplayName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A built-in provider's address is Nomi's business; showing it as a field nobody can
            // type into only made the form longer.
            if (state.provider.canonicalEndpoint() == null) {
                NomiTextField(
                    value = state.endpoint,
                    onValueChange = { value ->
                        val targetChanged = value.secretEndpointKey() != state.endpoint.secretEndpointKey()
                        onStateChanged(
                            state.copy(
                                endpoint = value,
                                hasStoredApiKey = if (targetChanged) false else state.hasStoredApiKey,
                                testResult = null,
                                errorMessage = null,
                            ),
                        )
                    },
                    label = nomiString("API endpoint"),
                    enabled = !busy,
                    isError = configurationError?.contains("endpoint", ignoreCase = true) == true,
                    supportingText = nomiString("OpenAI-compatible base URL (https:// optional)"),
                )
            }

            NomiTextField(
                value = state.model,
                onValueChange = {
                    onStateChanged(state.copy(model = it, testResult = null, errorMessage = null))
                },
                label = nomiString("Model"),
                enabled = !busy,
                isError = state.model.isBlank(),
                // An empty field says what is missing in its own caption, where the eye already
                // is, rather than in a second red box further down the form.
                supportingText = if (state.model.isBlank()) {
                    nomiString("Enter a model name.")
                } else {
                    nomiString("The model ID, exactly as the provider lists it.")
                },
            )
            val suggestions = state.provider.modelSuggestions(state.purpose)
                .filterNot { it == state.model.trim() }
            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = {
                                onStateChanged(
                                    state.copy(model = suggestion, testResult = null, errorMessage = null),
                                )
                            },
                            enabled = !busy,
                            shape = NomiShapes.Action,
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

            val keyName = when {
                usesExaGemini || state.provider == AiProviderKind.GEMINI -> nomiString("Google Gemini API key")
                usesExaOpenRouter -> nomiString("OpenRouter API key")
                else -> nomiString("API key")
            }
            NomiSecretField(
                value = state.apiKeyInput,
                onValueChange = {
                    onStateChanged(state.copy(apiKeyInput = it, testResult = null, errorMessage = null))
                },
                label = if (state.hasStoredApiKey) nomiFormat("{0} (stored securely)", keyName) else keyName,
                placeholder = nomiString("Leave blank to keep existing key").takeIf { state.hasStoredApiKey },
                enabled = !busy,
                imeAction = if (usesExa) ImeAction.Next else ImeAction.Done,
            )
            state.provider.keyPageUrl()?.let { url ->
                KeyPageLink(
                    label = nomiFormat("Get a key from {0}", state.provider.keyPageName()),
                    onClick = { runCatching { uriHandler.openUri(url) } },
                )
            }
            if (state.provider == AiProviderKind.OPEN_ROUTER || usesExaOpenRouter) {
                // Kept locally while typing, so a save echoing back never moves the cursor.
                var slug by rememberSaveable { mutableStateOf(openRouterPreferredProvider) }
                NomiTextField(
                    value = slug,
                    onValueChange = {
                        slug = it
                        onOpenRouterPreferredProviderChanged(it)
                    },
                    label = nomiString("Preferred OpenRouter provider"),
                    placeholder = "baseten",
                    supportingText = nomiString("Every OpenRouter request asks this provider first, for every task. If it is unavailable, OpenRouter uses the next one. Leave blank to let OpenRouter choose."),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (usesExa) {
                NomiSecretField(
                    value = state.searchApiKeyInput,
                    onValueChange = {
                        onStateChanged(state.copy(searchApiKeyInput = it, testResult = null, errorMessage = null))
                    },
                    label = if (state.hasStoredSearchApiKey) {
                        nomiFormat("{0} (stored securely)", nomiString("Exa API key"))
                    } else {
                        nomiString("Exa API key")
                    },
                    placeholder = nomiString("Leave blank to keep existing key")
                        .takeIf { state.hasStoredSearchApiKey },
                    enabled = !busy,
                )
                KeyPageLink(
                    label = nomiFormat("Get a key from {0}", "Exa"),
                    onClick = { runCatching { uriHandler.openUri(EXA_KEY_PAGE_URL) } },
                )
                Text(
                    if (usesExaOpenRouter) {
                        nomiString("Exa retrieves sources through Exa's official API; an OpenRouter model reads them. Nomi only uses OpenRouter endpoints that cost at most $1 per million input and $5 per million output tokens, and caps every answer's length. For a hard spending limit, set a credit limit on the key at OpenRouter. Both keys stay encrypted on this device.")
                    } else {
                        nomiString("Exa retrieves sources through Exa's official API; Gemini runs directly through Google's Gemini API. Both keys stay encrypted on this device.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.hasStoredApiKey || state.hasStoredSearchApiKey) {
                TextButton(onClick = onRemoveStoredKey, enabled = !busy) {
                    Text(
                        if (state.isRemovingKey) {
                            nomiString("Removing stored key…")
                        } else {
                            nomiString("Remove stored key")
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            configurationError?.takeIf { state.model.isNotBlank() }?.let { NomiInlineError(it) }
            state.testResult?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NomiFieldShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        nomiMessage(it),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            state.errorMessage?.let { NomiInlineError(it) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/** The way to the page where a key is made, for someone who arrived without one. */
@Composable
internal fun KeyPageLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}

private val SEARCHING_PROVIDERS = listOf(
    AiProviderKind.OPEN_ROUTER,
    AiProviderKind.PERPLEXITY,
    AiProviderKind.OPEN_AI,
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE,
)

/**
 * The providers a task can run on, the recommended one first.
 *
 * Research and Fallback have to search the web. Exa + Gemini does that for research; Gemini on
 * its own cannot, so it is only offered to the tasks that read a sentence or a photo.
 */
private fun providersFor(purpose: String): List<AiProviderKind> = when (purpose) {
    "Food research" ->
        listOf(AiProviderKind.EXA_GEMINI, AiProviderKind.EXA_OPEN_ROUTER) + SEARCHING_PROVIDERS
    "Fallback" -> SEARCHING_PROVIDERS
    else -> listOf(AiProviderKind.GEMINI) + SEARCHING_PROVIDERS
}

/** What a fresh install runs this task on. Fallback is optional and has no recommendation. */
private fun recommendedProviderFor(purpose: String): AiProviderKind? = when (purpose) {
    "Food research" -> AiProviderKind.EXA_GEMINI
    "Fallback" -> null
    else -> AiProviderKind.GEMINI
}

private fun AiProviderEditorState.configurationError(
    blankModelMessage: String,
    missingEndpointMessage: String,
    invalidEndpointMessage: String,
): String? = when {
    model.isBlank() -> blankModelMessage
    provider == AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE && endpoint.isBlank() -> missingEndpointMessage
    provider == AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE && !endpoint.isValidHttpsEndpoint() -> invalidEndpointMessage
    else -> null
}

/**
 * The same form pointed at another provider.
 *
 * A key already typed is kept: pasting the key first and choosing the provider second is the
 * natural order for someone coming back from the provider's website, and wiping the field
 * punished it. Whether a key is already stored for the new provider is not known here, so both
 * flags drop to false until the host has looked.
 */
internal fun AiProviderEditorState.switchedTo(provider: AiProviderKind): AiProviderEditorState = copy(
    provider = provider,
    endpoint = provider.canonicalEndpoint().orEmpty(),
    model = provider.suggestedModel(purpose),
    hasStoredSearchApiKey = false,
    hasStoredApiKey = false,
    testResult = null,
    errorMessage = null,
)

private fun AiProviderKind.canonicalEndpoint(): String? = when (this) {
    AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
    AiProviderKind.EXA_GEMINI,
    AiProviderKind.GEMINI,
    -> GEMINI_API_ENDPOINT
    AiProviderKind.OPEN_ROUTER,
    AiProviderKind.EXA_OPEN_ROUTER,
    -> "https://openrouter.ai/api/v1"
    AiProviderKind.OPEN_AI -> "https://api.openai.com/v1"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> null
}

private const val SUGGESTED_GEMINI_MODEL = "gemini-3.8-flash"

private fun AiProviderKind.suggestedModel(purpose: String): String = when (this) {
    AiProviderKind.PERPLEXITY -> if (purpose == "Fallback") "sonar-pro" else "sonar"
    AiProviderKind.OPEN_ROUTER -> if (purpose == "Food research") {
        DEFAULT_OPENROUTER_RESEARCH_MODEL
    } else {
        DEFAULT_OPENROUTER_MODEL
    }
    AiProviderKind.OPEN_AI -> if (purpose == "Fallback") "gpt-5.2" else ""
    AiProviderKind.EXA_GEMINI -> SUGGESTED_GEMINI_MODEL
    AiProviderKind.EXA_OPEN_ROUTER -> DEFAULT_EXA_OPENROUTER_MODEL
    AiProviderKind.GEMINI -> SUGGESTED_GEMINI_MODEL
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> ""
}

/**
 * Cheap OpenRouter readers with structured output, the default first. All of them sit well under
 * the research price ceiling, which refuses anything dearer anyway.
 */
private val SUGGESTED_EXA_OPENROUTER_MODELS = listOf(
    DEFAULT_EXA_OPENROUTER_MODEL,
    "openai/gpt-6-luna",
    "qwen/qwen3.8-flash",
)

/**
 * Models offered as one-tap answers under the field, so an empty field is never a dead end.
 *
 * Research and Fallback need web search, so OpenAI includes its search model there. Gemini
 * offers the same model for every task; selecting a suggestion is an explicit user choice.
 */
private fun AiProviderKind.modelSuggestions(purpose: String): List<String> {
    val searches = purpose == "Food research" || purpose == "Fallback"
    return when (this) {
        AiProviderKind.OPEN_ROUTER ->
            listOf(if (purpose == "Food research") DEFAULT_OPENROUTER_RESEARCH_MODEL else DEFAULT_OPENROUTER_MODEL)
        AiProviderKind.PERPLEXITY -> listOf("sonar", "sonar-pro")
        AiProviderKind.OPEN_AI ->
            if (searches) listOf("gpt-4o-search-preview", "gpt-5.2") else listOf("gpt-5.2")
        AiProviderKind.EXA_GEMINI -> listOf(SUGGESTED_GEMINI_MODEL)
        AiProviderKind.EXA_OPEN_ROUTER -> SUGGESTED_EXA_OPENROUTER_MODELS
        AiProviderKind.GEMINI -> listOf(SUGGESTED_GEMINI_MODEL)
        AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> emptyList()
    }
}

/** Where a key for this provider is made. A custom endpoint has no such page. */
internal fun AiProviderKind.keyPageUrl(): String? = when (this) {
    AiProviderKind.OPEN_ROUTER,
    AiProviderKind.EXA_OPEN_ROUTER,
    -> "https://openrouter.ai/keys"
    AiProviderKind.PERPLEXITY -> "https://www.perplexity.ai/settings/api"
    AiProviderKind.OPEN_AI -> "https://platform.openai.com/api-keys"
    AiProviderKind.EXA_GEMINI,
    AiProviderKind.GEMINI,
    -> "https://aistudio.google.com/apikey"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> null
}

/** Who issues the key, which for Exa + Gemini's first field is Google rather than the pair. */
internal fun AiProviderKind.keyPageName(): String = when (this) {
    AiProviderKind.OPEN_ROUTER,
    AiProviderKind.EXA_OPEN_ROUTER,
    -> "OpenRouter"
    AiProviderKind.PERPLEXITY -> "Perplexity"
    AiProviderKind.OPEN_AI -> "OpenAI"
    AiProviderKind.EXA_GEMINI,
    AiProviderKind.GEMINI,
    -> "Google AI Studio"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> ""
}

internal const val EXA_KEY_PAGE_URL = "https://dashboard.exa.ai/api-keys"

@Composable
internal fun AiProviderKind.localizedDisplayName(): String = when (this) {
    AiProviderKind.PERPLEXITY -> "Perplexity"
    AiProviderKind.EXA_GEMINI -> "Exa + Gemini"
    AiProviderKind.EXA_OPEN_ROUTER -> "Exa + OpenRouter"
    AiProviderKind.OPEN_ROUTER -> "OpenRouter"
    AiProviderKind.OPEN_AI -> "OpenAI"
    AiProviderKind.GEMINI -> "Google Gemini"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> nomiString("Custom")
}

@Composable
internal fun String.localizedPurpose(): String = when (this) {
    "Food research" -> nomiString("Food research")
    "Food interpretation" -> nomiString("Food interpretation")
    "Portion changes" -> nomiString("Portion changes")
    "Photo recognition" -> nomiString("Photo recognition")
    "Fallback" -> nomiString("Fallback")
    else -> this
}

private fun String.isValidHttpsEndpoint(): Boolean {
    val uri = runCatching { URI(asHttpsEndpoint()) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}

private fun String.asHttpsEndpoint(): String = trim().let { endpoint ->
    if ("://" in endpoint) endpoint else "https://$endpoint"
}

private fun String.secretEndpointKey(): String = trim().trimEnd('/').lowercase()
