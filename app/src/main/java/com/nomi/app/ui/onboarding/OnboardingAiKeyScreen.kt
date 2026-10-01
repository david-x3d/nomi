package com.nomi.app.ui.onboarding

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
import androidx.compose.ui.text.input.ImeAction
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ui.components.NomiSecureWindow
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.settings.AiKeyField
import com.nomi.app.ui.settings.EXA_KEY_PAGE_URL
import com.nomi.app.ui.settings.KeyPageLink
import com.nomi.app.ui.settings.StoredKeyField
import com.nomi.app.ui.settings.keyPageName
import com.nomi.app.ui.settings.keyPageUrl
import com.nomi.app.ui.settings.localizedDisplayName

/**
 * The step that asks for the one thing Nomi cannot work without.
 *
 * Every other answer in onboarding shapes a plan; this one decides whether the first meal can be
 * logged at all. Without it the journey ended on "Start tracking" and the first sentence typed
 * came back as an error pointing at a setting nobody had been shown.
 *
 * A fresh install reads with Google Gemini and researches with Exa + Gemini, so it needs two
 * keys: one from Google and one from Exa. Both are checked with real requests before they are
 * kept, so a mistyped one is caught here, by the field it was typed into, rather than at the
 * first meal. The step can still be skipped: someone without keys yet should reach their plan,
 * and Today says what is missing until they are added.
 *
 * The typed keys live in this composable only. They are not part of the onboarding state, which
 * is persisted between launches as a draft.
 */
@Composable
internal fun AiKeyScreen(
    geminiKeyStored: Boolean,
    exaKeyStored: Boolean,
    onConnectKeys: ConnectAiKeys,
    onContinue: () -> Unit,
) {
    NomiSecureWindow()
    val uriHandler = LocalUriHandler.current
    val haptics = rememberNomiHaptics()
    val gemini = AiProviderKind.GEMINI
    var geminiKey by remember { mutableStateOf("") }
    var exaKey by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var failedField by remember { mutableStateOf<AiKeyField?>(null) }
    val allStored = geminiKeyStored && exaKeyStored
    val hasInput = geminiKey.isNotBlank() || exaKey.isNotBlank()

    fun submit() {
        if (!hasInput) {
            onContinue()
            return
        }
        checking = true
        failure = null
        failedField = null
        onConnectKeys(geminiKey, exaKey) { success, message, field ->
            checking = false
            if (success) {
                geminiKey = ""
                exaKey = ""
                haptics.confirmed()
                onContinue()
            } else {
                // A key that passed before the other failed is already stored, so its field is
                // cleared and shows as stored instead of asking for it again.
                if (field == AiKeyField.SEARCH) geminiKey = ""
                haptics.failed()
                failure = message
                failedField = field
            }
        }
    }

    QuestionPage(
        title = nomiString("Connect your AI keys"),
        supportingText = nomiString(
            "Nomi has no account and no AI credits of its own. Google Gemini reads what you " +
                "log and Exa finds the nutrition sources, each on a key that belongs to you.",
        ),
        error = failure,
        onContinue = ::submit,
        note = nomiString("Your keys are saved. You're ready to log.").takeIf { allStored },
        continueLabel = when {
            checking -> nomiString("Checking…")
            !hasInput && allStored -> nomiString("Continue")
            else -> nomiString("Check and save keys")
        },
        continueEnabled = !checking && (hasInput || allStored),
        secondaryAction = if (allStored) {
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
        StoredKeyField(
            value = geminiKey,
            onValueChange = {
                geminiKey = it
                failure = null
                failedField = null
            },
            name = nomiFormat("{0} API key", gemini.localizedDisplayName()),
            stored = geminiKeyStored,
            enabled = !checking,
            isError = failedField == AiKeyField.PRIMARY,
            imeAction = ImeAction.Next,
            onDone = {},
            modifier = Modifier.testTag("onboarding_gemini_key"),
        )
        gemini.keyPageUrl()?.let { url ->
            KeyPageLink(
                label = nomiFormat("Get a key from {0}", gemini.keyPageName()),
                onClick = { runCatching { uriHandler.openUri(url) } },
            )
        }
        StoredKeyField(
            value = exaKey,
            onValueChange = {
                exaKey = it
                failure = null
                failedField = null
            },
            name = nomiString("Exa API key"),
            stored = exaKeyStored,
            enabled = !checking,
            isError = failedField == AiKeyField.SEARCH,
            onDone = { if (hasInput) submit() },
            modifier = Modifier.testTag("onboarding_exa_key"),
        )
        KeyPageLink(
            label = nomiFormat("Get a key from {0}", "Exa"),
            onClick = { runCatching { uriHandler.openUri(EXA_KEY_PAGE_URL) } },
        )
        Text(
            text = nomiString(
                "The keys are stored encrypted on this phone and are only sent to their " +
                    "providers. You can switch to another provider in Settings.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
