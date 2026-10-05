package com.nomi.app.ui.today

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.capture.BarcodeCaptureScreen
import com.nomi.app.ui.capture.PhotoCaptureScreen
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.localization.nomiString

/** Renders the active inline capture; its state and effects are owned separately. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun InlineTodayCapture(
    capture: TodayCaptureState,
    onQuickMethod: (AddFoodMethod) -> Unit,
    onInlinePhotoSelected: (Uri, String, PhotoCaptureSubject) -> Unit,
    onInlineBarcodeDetected: (String) -> Unit,
) {
    if (capture.showBarcode) {
        BarcodeCaptureScreen(
            inline = true,
            onBack = { capture.showBarcode = false },
            onBarcodeDetected = { barcode ->
                capture.showBarcode = false
                onInlineBarcodeDetected(barcode)
            },
            onManualEntry = {
                capture.showBarcode = false
                onQuickMethod(AddFoodMethod.TYPE)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else capture.subject?.let { subject ->
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (subject != PhotoCaptureSubject.MENU) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = subject == PhotoCaptureSubject.MEAL,
                        onClick = { capture.subject = PhotoCaptureSubject.MEAL },
                        label = { Text(nomiString("Photo")) },
                        leadingIcon = {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                        },
                    )
                    FilterChip(
                        selected = subject == PhotoCaptureSubject.NUTRITION_LABEL,
                        onClick = {
                            capture.subject = PhotoCaptureSubject.NUTRITION_LABEL
                        },
                        label = { Text(nomiString("Nutrition label")) },
                        leadingIcon = {
                            Icon(Icons.Default.Article, contentDescription = null)
                        },
                    )
                }
            }
            PhotoCaptureScreen(
                subject = subject,
                inline = true,
                onBack = { capture.subject = null },
                onPhotoSelected = { uri, mimeType ->
                    capture.subject = null
                    onInlinePhotoSelected(uri, mimeType, subject)
                },
                onManualEntry = {
                    capture.subject = null
                    onQuickMethod(AddFoodMethod.TYPE)
                },
            )
        }
    }
}
