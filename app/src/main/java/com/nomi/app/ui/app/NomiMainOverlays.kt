package com.nomi.app.ui.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomi.app.R
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.data.share.ShareReceiveFailure
import com.nomi.app.ui.localization.fillTemplate
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.PortionEditSheet
import com.nomi.app.ui.settings.AiProviderEditorDialog
import com.nomi.app.ui.settings.AiProviderEditorState
import com.nomi.app.ui.share.NomiShareCoordinator
import com.nomi.app.ui.share.NomiShareEvent
import com.nomi.app.ui.share.ShareReceivedDialog
import com.nomi.app.ui.share.ShareReceivingDialog
import com.nomi.app.ui.share.ShareSendingDialog
import com.nomi.app.ui.update.UpdateAvailableDialog
import com.nomi.app.update.UpdateAvailability
import kotlinx.coroutines.flow.collectLatest

/*
 * The dialogs and sheets that sit above the main interface whatever screen is underneath:
 * the tap-to-share screens, the update prompt, the AI provider editor and the preview editors.
 */

/**
 * Everything a tap between two phones puts on screen: the three waiting dialogs, and the one
 * message that says how it ended.
 */
@Composable
internal fun ShareTapOverlays(
    shareCoordinator: NomiShareCoordinator,
    showMessage: (String) -> Unit,
) {
    val currentShowMessage by rememberUpdatedState(showMessage)

    // A finished share is reported as a message rather than left on screen, because the tap
    // screens have already been dismissed by the time the outcome is known. The wording is
    // resolved here, where the user's language is available, and the coordinator only names what
    // happened.
    val shareNoNfcMessage = nomiString("This phone has no NFC")
    val shareCannotEmulateMessage = nomiString("This phone can receive NFC shares but cannot send them")
    val shareNfcOffMessage = nomiString("Turn NFC on to share")
    val shareNothingSelectedMessage = nomiString("Tick at least one food to share")
    val shareNoTagMessage = nomiString("Nomi couldn't find a phone to read")
    val shareTransferFailedMessage = nomiString("The share was interrupted, try holding them closer")
    val shareCorruptedMessage = nomiString("That share arrived damaged, try again")
    val shareIncompatibleMessage = nomiString("That phone isn't a Nomi this version can read")
    val shareNotAShareMessage = nomiString("That wasn't a shared day")
    val shareOfferedMessage = nomiString("Held for the other phone to read")
    val shareAddedTemplate = nomiString("Added {0} shared foods to your day")

    fun shareMessageText(event: NomiShareEvent): String = when (event) {
        NomiShareEvent.Offered -> shareOfferedMessage
        is NomiShareEvent.Added -> fillTemplate(shareAddedTemplate, arrayOf<Any?>(event.foodCount))
        NomiShareEvent.NoNfcHardware -> shareNoNfcMessage
        NomiShareEvent.NfcTurnedOff -> shareNfcOffMessage
        NomiShareEvent.CannotEmulate -> shareCannotEmulateMessage
        NomiShareEvent.NothingSelected -> shareNothingSelectedMessage
        // The four ways a tap fails each get their own sentence. One "sharing failed" would send
        // the user off to guess, and the fixes are nothing alike: move the phones, hold still,
        // update the app, or the other phone simply had nothing to offer.
        is NomiShareEvent.Failed -> when (event.failure) {
            ShareReceiveFailure.NoTagFound -> shareNoTagMessage
            ShareReceiveFailure.TransferFailed -> shareTransferFailedMessage
            ShareReceiveFailure.Corrupted -> shareCorruptedMessage
            ShareReceiveFailure.Incompatible -> shareIncompatibleMessage
            ShareReceiveFailure.NotAShare -> shareNotAShareMessage
        }
    }

    LaunchedEffect(shareCoordinator) {
        shareCoordinator.events.collectLatest { event -> currentShowMessage(shareMessageText(event)) }
    }

    // The three tap screens are dialogs rather than destinations because none of them is a place
    // the user can be: they are the phone waiting to be touched, and a tap that walks away from
    // them has to be able to come back to the day rather than to a blank screen.
    val offerState = shareCoordinator.offered
    if (offerState != null) {
        ShareSendingDialog(
            foodCount = offerState.foodCount,
            kcal = offerState.kcal,
            onCancel = shareCoordinator::collapse,
        )
    }
    if (shareCoordinator.isReceiving) {
        ShareReceivingDialog(
            receivedBytes = shareCoordinator.receivedBytes,
            expectedBytes = shareCoordinator.expectedBytes,
            onCancel = shareCoordinator::collapse,
        )
    }
    shareCoordinator.received?.let { envelope ->
        ShareReceivedDialog(
            envelope = envelope,
            onAdd = shareCoordinator::addReceivedToDiary,
            onDiscard = shareCoordinator::discardReceived,
        )
    }
}

/** Offers a newer published release, once per app start, until it is dismissed. */
@Composable
internal fun UpdateDialogHost(viewModel: AppViewModel) {
    val context = LocalContext.current
    val updateAvailability by viewModel.update.collectAsStateWithLifecycle()
    // In a debug build the dialog can be forced on by a build-time resource, so it can be
    // screenshotted without publishing a release first. `rememberForcedUpdateAvailability`
    // returns null in a release build, so the real check below is the only thing a shipped APK
    // can show.
    //
    // The forced value has to be dismissed through composition state, not through
    // `viewModel.dismissUpdate()`: that clears the ViewModel's `update` flow, which the forced
    // dialog never went through, so tapping "Later" would have done nothing and the debug build
    // would be stuck behind a dialog that cannot be closed.
    var forcedUpdateDismissed by rememberSaveable { mutableStateOf(false) }
    val forcedUpdate = rememberForcedUpdateAvailability()?.takeUnless { forcedUpdateDismissed }
    (forcedUpdate ?: updateAvailability as? UpdateAvailability.Available)?.let { available ->
        val dismiss = {
            forcedUpdateDismissed = true
            viewModel.dismissUpdate()
        }
        UpdateAvailableDialog(
            availability = available,
            onViewUpdate = {
                dismiss()
                context.openReleasePage(available.releaseUrl)
            },
            onDismiss = dismiss,
        )
    }
}

/** The provider being edited from Settings, if any, and which pipeline slot it belongs to. */
@Stable
internal class AiProviderEditorSession {
    var index by mutableIntStateOf(-1)
        private set
    var editor by mutableStateOf<AiProviderEditorState?>(null)

    fun open(index: Int, state: AiProviderEditorState) {
        this.index = index
        editor = state
    }
}

/** Shows the provider editor while [session] has one open, and runs its three actions. */
@Composable
internal fun AiProviderEditorHost(viewModel: AppViewModel, session: AiProviderEditorSession) {
    session.editor?.let { editor ->
        AiProviderEditorDialog(
            state = editor,
            onStateChanged = { session.editor = it },
            onTestConnection = {
                session.editor = editor.copy(isTesting = true, testResult = null)
                viewModel.testProvider(session.index, editor) { result ->
                    session.editor = session.editor?.copy(isTesting = false, testResult = result)
                }
            },
            onSave = {
                session.editor = editor.copy(isSaving = true, errorMessage = null, testResult = null)
                viewModel.saveProvider(session.index, editor) { success, message ->
                    if (success) {
                        session.editor = null
                    } else {
                        session.editor = session.editor?.copy(
                            isSaving = false,
                            errorMessage = message,
                        )
                    }
                }
            },
            onRemoveStoredKey = {
                session.editor = editor.copy(
                    isRemovingKey = true,
                    errorMessage = null,
                    testResult = null,
                )
                viewModel.removeProviderKey(session.index, editor) { success, message ->
                    val current = session.editor
                    if (current != null) {
                        session.editor = current.copy(
                            isRemovingKey = false,
                            hasStoredApiKey = if (success) false else current.hasStoredApiKey,
                            apiKeyInput = if (success) "" else current.apiKeyInput,
                            testResult = if (success) message else null,
                            errorMessage = if (success) null else message,
                        )
                    }
                }
            },
            onDismiss = { session.editor = null },
        )
    }
}

/** The two editors that can sit on top of a preview: one item's numbers, or its portion. */
@Composable
internal fun LoggingEditingOverlays(
    viewModel: AppViewModel,
    editedItemIndex: Int?,
    onEditFinished: () -> Unit,
) {
    val loggingState by viewModel.loggingState.collectAsStateWithLifecycle()
    val portionEditState by viewModel.portionEditState.collectAsStateWithLifecycle()

    editedItemIndex?.let { index ->
        val preview = loggingState as? FoodLoggingUiState.Preview
        preview?.analysis?.items?.getOrNull(index)?.let { item: AnalyzedFoodItem ->
            AnalyzedItemEditDialog(
                item = item,
                onDismiss = onEditFinished,
                onSave = {
                    viewModel.updatePreviewItem(index, it)
                    onEditFinished()
                },
            )
        }
    }
    portionEditState?.let { state ->
        PortionEditSheet(
            state = state,
            onCorrectionChanged = viewModel::updatePortionCorrection,
            onInterpret = viewModel::interpretPortionCorrection,
            onApply = viewModel::applyPortionCorrection,
            onDismiss = viewModel::dismissPortionEdit,
            onResearch = viewModel::researchEditedItem,
        )
    }
}

/**
 * Opens the exact release page in the user's browser.
 *
 * The repository homepage is deliberately never substituted: a user who tapped "View update"
 * wants the release, not a page they then have to navigate. `runCatching` because a device with
 * no browser at all should do nothing rather than crash.
 */
private fun Context.openReleasePage(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * The update dialog a **debug** build has been told to force, or `null` in a release.
 *
 * The switch is a build-time resource rather than a flag, an intent extra or a preference, so
 * there is nothing in a shipped APK to flip: `nomi_debug_force_update_dialog` is a constant
 * `false` in `src/main/res/values`, and only the `debug` source set overrides it. That is why
 * this lives in the composition rather than in [com.nomi.app.ui.app.AppViewModel] - the ViewModel
 * deliberately holds no `Context`, and giving it one for a debug affordance would be a bad trade.
 */
@Composable
private fun rememberForcedUpdateAvailability(): UpdateAvailability.Available? {
    val context = LocalContext.current
    return remember(context) {
        if (!context.resources.getBoolean(R.bool.nomi_debug_force_update_dialog)) return@remember null
        val version = context.getString(R.string.nomi_debug_forced_update_version)
            .takeIf(String::isNotBlank) ?: return@remember null
        UpdateAvailability.Available(
            version = version,
            releaseUrl = "https://github.com/david-x3d/nomi/releases/latest",
            summary = "This text stands in for the release notes, which is the one part of the " +
                "dialog that cannot be faked - it comes from whatever GitHub returns. The dialog " +
                "itself, this summary's scroll area, and the two buttons are all real.",
        )
    }
}
