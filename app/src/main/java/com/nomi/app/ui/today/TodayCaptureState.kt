package com.nomi.app.ui.today

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nomi.app.ui.capture.PhotoCaptureSubject

/** Camera and barcode overlays share one back action and one transition from the composer. */
internal class TodayCaptureState(
    val pickPhoto: () -> Unit,
    subject: PhotoCaptureSubject? = null,
    showBarcode: Boolean = false,
) {
    var subject by mutableStateOf(subject)
    var showBarcode by mutableStateOf(showBarcode)
    val isActive get() = subject != null || showBarcode

    fun close() {
        subject = null
        showBarcode = false
    }
}

@Composable
internal fun rememberTodayCaptureState(
    onPhotoSelected: (Uri, String, PhotoCaptureSubject) -> Unit,
    composer: TodayComposerState,
    list: LazyListState,
): TodayCaptureState {
    val context = LocalContext.current
    val onSelected by rememberUpdatedState(onPhotoSelected)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onSelected(it, context.contentResolver.getType(it) ?: "image/*", PhotoCaptureSubject.MEAL) }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val state = rememberSaveable(
        saver = listSaver<TodayCaptureState, Any>(
            save = { listOf(it.subject?.name.orEmpty(), it.showBarcode) },
            restore = {
                TodayCaptureState(
                    pickPhoto,
                    (it[0] as String).takeIf(String::isNotEmpty)?.let(PhotoCaptureSubject::valueOf),
                    it[1] as Boolean,
                )
            },
        ),
    ) { TodayCaptureState(pickPhoto) }
    BackHandler(enabled = state.isActive, onBack = state::close)
    LaunchedEffect(state.subject, state.showBarcode) {
        if (state.isActive) {
            composer.openCapture()
            list.animateScrollToItem(1)
        }
    }
    return state
}
