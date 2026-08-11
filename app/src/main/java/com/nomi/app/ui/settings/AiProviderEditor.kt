package com.nomi.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_MODEL
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_RESEARCH_MODEL
import com.nomi.app.data.remote.ai.DEFAULT_GEMINI_NUTRITION_MODEL
import com.nomi.app.data.remote.ai.GEMINI_API_ENDPOINT
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiFieldShape
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
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

@Composable
fun AiProviderEditorDialog(
    state: AiProviderEditorState,
    onStateChanged: (AiProviderEditorState) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onRemoveStoredKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val busy = state.isTesting || state.isSaving || state.isRemovingKey
    val configurationError = state.configurationError(
        blankModelMessage = nomiString("Enter a model name.", "Gib einen Modellnamen ein."),
        missingEndpointMessage = nomiString("Enter an API endpoint.", "Gib einen API-Endpoint ein."),
        invalidEndpointMessage = nomiString("Enter a valid HTTPS API endpoint.", "Gib einen gültigen HTTPS-API-Endpoint ein."),
    )
    val hasReasoningKey = state.hasStoredApiKey || state.apiKeyInput.isNotBlank()
    val hasSearchKey = state.provider != AiProviderKind.EXA_GEMINI ||
        state.hasStoredSearchApiKey || state.searchApiKeyInput.isNotBlank()
    val canTest = configurationError == null && hasReasoningKey && hasSearchKey && !busy
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    NomiDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = state.purpose.localizedPurpose(),
        icon = Icons.Outlined.Key,
        confirmLabel = if (state.isSaving) {
            nomiString("Saving…", "Wird gespeichert…")
        } else {
            nomiString("Save", "Speichern")
        },
        onConfirm = onSave,
        confirmEnabled = configurationError == null && !busy,
        dismissLabel = nomiString("Cancel", "Abbrechen"),
        onDismissAction = { if (!busy) onDismiss() },
    ) {
        Text(
            nomiString("Provider", "Anbieter"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        providerRows(state.purpose).forEach { providers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = state.provider == provider,
                        onClick = {
                            if (state.provider != provider) {
                                onStateChanged(state.switchTo(provider))
                            }
                        },
                        enabled = !busy,
                        shape = NomiShapes.Action,
                        label = { Text(provider.localizedDisplayName()) },
                    )
                }
            }
        }
        NomiTextField(
            value = state.provider.canonicalEndpoint() ?: state.endpoint,
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
            label = nomiString("API endpoint", "API-Endpunkt"),
            readOnly = state.provider.canonicalEndpoint() != null,
            enabled = !busy,
            isError = configurationError?.contains("endpoint", ignoreCase = true) == true,
            supportingText = if (state.provider.canonicalEndpoint() != null) {
                nomiString("Built-in provider endpoint managed by Nomi", "Nomi verwaltet den Endpunkt dieses integrierten Anbieters.")
            } else {
                nomiString("OpenAI-compatible base URL (https:// optional)", "OpenAI-kompatible Basis-URL (https:// optional)")
            },
        )
        NomiTextField(
            value = state.model,
            onValueChange = {
                onStateChanged(
                    state.copy(model = it, testResult = null, errorMessage = null),
                )
            },
            label = nomiString("Model", "Modell"),
            enabled = !busy,
            isError = state.model.isBlank(),
        )
        val keyName = if (state.provider == AiProviderKind.EXA_GEMINI) {
            "Google Gemini API key"
        } else {
            nomiString("API key", "API-Schlüssel")
        }
        NomiTextField(
            value = state.apiKeyInput,
            onValueChange = {
                onStateChanged(
                    state.copy(apiKeyInput = it, testResult = null, errorMessage = null),
                )
            },
            label = if (state.hasStoredApiKey) "$keyName (stored securely)" else keyName,
            placeholder = if (state.hasStoredApiKey) {
                nomiString("Leave blank to keep existing key", "Leer lassen, um den vorhandenen Schlüssel zu behalten")
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !busy,
        )
        if (state.provider == AiProviderKind.EXA_GEMINI) {
            NomiTextField(
                value = state.searchApiKeyInput,
                onValueChange = {
                    onStateChanged(state.copy(searchApiKeyInput = it, testResult = null, errorMessage = null))
                },
                label = if (state.hasStoredSearchApiKey) "Exa API key (stored securely)" else "Exa API key",
                placeholder = if (state.hasStoredSearchApiKey) "Leave blank to keep existing key" else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
            )
            Text(
                "Exa retrieves sources through Exa's official API; Gemini runs directly through Google's Gemini API. Both keys stay encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.hasStoredApiKey || state.hasStoredSearchApiKey) {
            TextButton(
                onClick = onRemoveStoredKey,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRemovingKey) nomiString("Removing stored key…", "Gespeicherter Schlüssel wird entfernt…") else nomiString("Remove stored key", "Gespeicherten Schlüssel entfernen"))
            }
        }
        configurationError?.let { NomiInlineError(it) }
        OutlinedButton(
            onClick = onTestConnection,
            enabled = canTest,
            shape = NomiShapes.Action,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (state.isTesting) nomiString("Testing…", "Wird getestet…") else nomiString("Test connection", "Verbindung testen"))
        }
        state.testResult?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = NomiFieldShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        state.errorMessage?.let { NomiInlineError(it) }
    }
}

private val BASE_PROVIDER_ROWS = listOf(
    listOf(AiProviderKind.PERPLEXITY, AiProviderKind.OPEN_ROUTER),
    listOf(AiProviderKind.OPEN_AI, AiProviderKind.CODEX_EASY),
    listOf(AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE),
)

private fun providerRows(purpose: String) = BASE_PROVIDER_ROWS +
    listOfNotNull(listOf(AiProviderKind.EXA_GEMINI).takeIf { purpose == "Food research" })

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

private fun AiProviderEditorState.switchTo(provider: AiProviderKind): AiProviderEditorState = copy(
    provider = provider,
    endpoint = provider.canonicalEndpoint().orEmpty(),
    searchApiKeyInput = "",
    model = provider.suggestedModel(purpose),
    hasStoredSearchApiKey = false,
    apiKeyInput = "",
    hasStoredApiKey = false,
    testResult = null,
    errorMessage = null,
)

private fun AiProviderKind.canonicalEndpoint(): String? = when (this) {
    AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
    AiProviderKind.EXA_GEMINI -> GEMINI_API_ENDPOINT
    AiProviderKind.OPEN_ROUTER -> "https://openrouter.ai/api/v1"
    AiProviderKind.OPEN_AI -> "https://api.openai.com/v1"
    AiProviderKind.CODEX_EASY -> "https://codex-easy.ai/v1"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> null
}

private fun AiProviderKind.suggestedModel(purpose: String): String = when (this) {
    AiProviderKind.PERPLEXITY -> if (purpose == "Fallback") "sonar-pro" else "sonar"
    AiProviderKind.OPEN_ROUTER -> if (purpose == "Food research") {
        DEFAULT_OPENROUTER_RESEARCH_MODEL
    } else {
        DEFAULT_OPENROUTER_MODEL
    }
    AiProviderKind.OPEN_AI -> if (purpose == "Fallback") "gpt-5.2" else ""
    AiProviderKind.EXA_GEMINI -> DEFAULT_GEMINI_NUTRITION_MODEL
    // Codex Easy relays whatever models the account is entitled to, so the name is left to
    // the user rather than guessed; `/v1/models` on the same key lists them.
    AiProviderKind.CODEX_EASY,
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE,
    -> ""
}

@Composable
private fun AiProviderKind.localizedDisplayName(): String = when (this) {
    AiProviderKind.PERPLEXITY -> "Perplexity"
    AiProviderKind.EXA_GEMINI -> "Exa + Gemini"
    AiProviderKind.OPEN_ROUTER -> "OpenRouter"
    AiProviderKind.OPEN_AI -> "OpenAI"
    AiProviderKind.CODEX_EASY -> "Codex Easy"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> nomiString("Custom", "Benutzerdefiniert")
}

@Composable
private fun String.localizedPurpose(): String = when (this) {
    "Food research" -> nomiString("Food research", "Lebensmittelrecherche")
    "Food interpretation" -> nomiString("Food interpretation", "Lebensmittelinterpretation")
    "Portion changes" -> nomiString("Portion changes", "Portionsänderungen")
    "Photo recognition" -> nomiString("Photo recognition", "Fotoerkennung")
    "Fallback" -> nomiString("Fallback", "Intelligenter Fallback")
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
