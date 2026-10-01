package com.nomi.app.ui.app

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nomi.app.integration.camera.MealImagePreprocessor
import com.nomi.app.integration.camera.PreparedMealImage
import com.nomi.app.integration.camera.deleteOwnedCameraCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns a picked or captured photo into the bounded, metadata-free upload the providers get.
 *
 * Every capture path - a plate, a label, a menu page, from Today or from a full-screen camera -
 * needs the same four things: decode off the main thread, delete Nomi's own temporary capture
 * whatever happens, hand the bytes on, and say so when the image cannot be read. They used to
 * be written out at each of the five call sites.
 */
internal class MealImageLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    fun load(uri: Uri, onPrepared: (PreparedMealImage) -> Unit) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use(MealImagePreprocessor::prepare)
                            ?: error("The selected image could not be opened")
                    } finally {
                        deleteOwnedCameraCapture(context.applicationContext, uri)
                    }
                }
            }.onSuccess(onPrepared)
                .onFailure { onError(it.message ?: "Nomi couldn't read that image") }
        }
    }
}

@Composable
internal fun rememberMealImageLoader(onError: (String) -> Unit): MealImageLoader {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return remember(context, scope) { MealImageLoader(context, scope) { currentOnError(it) } }
}
