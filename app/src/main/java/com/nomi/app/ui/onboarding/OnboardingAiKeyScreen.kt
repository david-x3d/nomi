package com.nomi.app.ui.onboarding

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ui.components.NomiSecretField
import com.nomi.app.ui.components.NomiSecureWindow
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.settings.KeyPageLink
import com.nomi.app.ui.settings.keyPageName
import com.nomi.app.ui.settings.keyPageUrl

/**
 * The step that asks for the one thing Nomi cannot work without.
 *
 * Every other answer in onboarding shapes a plan; this one decides whether the first meal can be
 * logged at all. Without it the journey ended on "Start tracking" and the first sentence typed
 * came back as an error pointing at a setting nobody had been shown.
 *
 * The key is checked with a real request before it is kept, so a mistyped one is caught here, by
 * the field it was typed into, rather than at the first meal. It can still be skipped: someone
 * without a key yet should reach their plan, and Today says what is missing until it is added.
 *
 * The typed key lives in this composable only. It is not part of the onboarding state, which is
 * persisted between launches as a draft.
 */
@Composable
internal fun AiKeyScreen(
    keyStored: Boolean,
    onConnectKey: (key: String, onResult: (success: Boolean, message: String) -> Unit) -> Unit,
    onContinue: () -> Unit,
) {
    NomiSecureWindow()
    val uriHandler = LocalUriHandler.current
    val haptics = rememberNomiHaptics()
    // A fresh install runs every task on OpenRouter, which is the only state onboarding sees.
    val provider = AiProviderKind.OPEN_ROUTER
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (key.isBlank()) {
            onContinue()
            return
        }
        checking = true
        failure = null
        onConnectKey(key) { success, message ->
            checking = false
            if (success) {
                key = ""
                haptics.confirmed()
                onContinue()
            } else {
                haptics.failed()
                failure = message
            }
        }
    }

    val keyName = nomiFormat("{0} API key", provider.keyPageName())
    QuestionPage(
        title = nomiString("Connect your AI key"),
        supportingText = nomiString(
            "Nomi has no account and no AI credits of its own. It looks food up through " +
                "OpenRouter with a key that belongs to you, and one key covers everything.",
        ),
        error = failure,
        onContinue = ::submit,
        note = nomiString("Your key is saved. You're ready to log.").takeIf { keyStored },
        continueLabel = when {
            checking -> nomiString("Checking…")
            key.isBlank() && keyStored -> nomiString("Continue")
            else -> nomiString("Check and save key")
        },
        continueEnabled = !checking && (key.isNotBlank() || keyStored),
        secondaryAction = if (keyStored) {
            null
        } else {
            {
                TextButton(
                    onClick = onContinue,
                    enabled = !checking,
                    modifier = Modifier.testTag("onboarding_skip_ai_key"),
                ) {
                    Text(nomiString("Skip for now"))
                }
            }
        },
    ) {
        NomiSecretField(
            value = key,
            onValueChange = {
                key = it
                failure = null
            },
            label = if (keyStored) nomiFormat("{0} (stored securely)", keyName) else keyName,
            placeholder = nomiString("Leave blank to keep existing key").takeIf { keyStored },
            enabled = !checking,
            keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank()) submit() }),
            modifier = Modifier.testTag("onboarding_ai_key"),
        )
        provider.keyPageUrl()?.let { url ->
            KeyPageLink(
                label = nomiFormat("Get a key from {0}", provider.keyPageName()),
                onClick = { runCatching { uriHandler.openUri(url) } },
            )
        }
        Text(
            text = nomiString(
                "The key is stored encrypted on this phone and is only sent to the provider. " +
                    "You can switch to another provider in Settings.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
