package com.nomi.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.nomi.app.ui.localization.nomiString
import java.net.URI

data class AiProviderEditorState(
    val purpose: String,
    val provider: AiProviderKind,
    val endpoint: String,
    val model: String,
    val apiKeyInput: String = "",
    val hasStoredApiKey: Boolean = false,
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
    val canTest = configurationError == null &&
        (state.hasStoredApiKey || state.apiKeyInput.isNotBlank()) && !busy
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(state.purpose.localizedPurpose()) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(nomiString("Provider", "Anbieter"), style = MaterialTheme.typography.labelLarge)
                PROVIDER_ROWS.forEach { providers ->
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
                                label = { Text(provider.localizedDisplayName()) },
                            )
                        }
                    }
                }
                OutlinedTextField(
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
                    label = { Text(nomiString("API endpoint", "API-Endpunkt")) },
                    readOnly = state.provider.canonicalEndpoint() != null,
                    enabled = !busy,
                    isError = configurationError?.contains("endpoint", ignoreCase = true) == true,
                    supportingText = if (state.provider.canonicalEndpoint() != null) {
                        { Text(nomiString("Built-in provider endpoint managed by Nomi", "Nomi verwaltet den Endpunkt dieses integrierten Anbieters.")) }
                    } else {
                        { Text(nomiString("OpenAI-compatible base URL (https:// optional)", "OpenAI-kompatible Basis-URL (https:// optional)")) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.model,
                    onValueChange = {
                        onStateChanged(
                            state.copy(model = it, testResult = null, errorMessage = null),
                        )
                    },
                    label = { Text(nomiString("Model", "Modell")) },
                    enabled = !busy,
                    isError = state.model.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = {
                        onStateChanged(
                            state.copy(apiKeyInput = it, testResult = null, errorMessage = null),
                        )
                    },
                    label = { Text(if (state.hasStoredApiKey) nomiString("API key (stored securely)", "API-Schlüssel (sicher gespeichert)") else nomiString("API key", "API-Schlüssel")) },
                    placeholder = { if (state.hasStoredApiKey) Text(nomiString("Leave blank to keep existing key", "Leer lassen, um den vorhandenen Schlüssel zu behalten")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hasStoredApiKey) {
                    TextButton(
                        onClick = onRemoveStoredKey,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isRemovingKey) nomiString("Removing stored key…", "Gespeicherter Schlüssel wird entfernt…") else nomiString("Remove stored key", "Gespeicherten Schlüssel entfernen"))
                    }
                }
                configurationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = canTest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isTesting) nomiString("Testing…", "Wird getestet…") else nomiString("Test connection", "Verbindung testen"))
                }
                state.testResult?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = configurationError == null && !busy,
            ) {
                Text(if (state.isSaving) nomiString("Saving…", "Wird gespeichert…") else nomiString("Save", "Speichern"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(nomiString("Cancel", "Abbrechen")) }
        },
    )
}

private val PROVIDER_ROWS = listOf(
    listOf(AiProviderKind.PERPLEXITY, AiProviderKind.OPEN_ROUTER),
    listOf(AiProviderKind.OPEN_AI, AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE),
)

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
    model = provider.suggestedModel(purpose),
    apiKeyInput = "",
    hasStoredApiKey = false,
    testResult = null,
    errorMessage = null,
)

private fun AiProviderKind.canonicalEndpoint(): String? = when (this) {
    AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
    AiProviderKind.OPEN_ROUTER -> "https://openrouter.ai/api/v1"
    AiProviderKind.OPEN_AI -> "https://api.openai.com/v1"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> null
}

private fun AiProviderKind.suggestedModel(purpose: String): String = when (this) {
    AiProviderKind.PERPLEXITY -> if (purpose == "Fallback") "sonar-pro" else "sonar"
    AiProviderKind.OPEN_ROUTER -> if (purpose == "Fallback") {
        "~openai/gpt-latest"
    } else {
        DEFAULT_OPENROUTER_MODEL
    }
    AiProviderKind.OPEN_AI -> if (purpose == "Fallback") "gpt-5.2" else ""
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE,
    -> ""
}

@Composable
private fun AiProviderKind.localizedDisplayName(): String = when (this) {
    AiProviderKind.PERPLEXITY -> "Perplexity"
    AiProviderKind.OPEN_ROUTER -> "OpenRouter"
    AiProviderKind.OPEN_AI -> "OpenAI"
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
