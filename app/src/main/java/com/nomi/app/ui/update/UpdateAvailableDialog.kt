package com.nomi.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.update.UpdateAvailability

/**
 * The "a newer Nomi exists" dialog.
 *
 * Built on Nomi's own [NomiDialog] rather than a bare `AlertDialog`, so it carries the same
 * surface shape, hairline border, pitch-black handling and button hierarchy as every other Nomi
 * dialog. It states the version, offers one primary action that opens the exact release page in
 * the user's browser, and a secondary way out. It never downloads or installs anything: that is
 * the user's decision, made in their browser, on purpose.
 *
 * The summary is bounded and already stripped of Markdown by
 * [com.nomi.app.update.UpdateCheck.summarize], so a long changelog cannot turn this into a
 * scrolling document.
 */
@Composable
fun UpdateAvailableDialog(
    availability: UpdateAvailability.Available,
    onViewUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    NomiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Default.SystemUpdate,
        title = nomiString("Nomi update available"),
        subtitle = nomiFormat("Nomi {0} is available.", availability.version),
        confirmLabel = nomiString("View update"),
        onConfirm = onViewUpdate,
        dismissLabel = nomiString("Later"),
        onDismissAction = onDismiss,
        contentSpacing = 12.dp,
    ) {
        if (availability.summary.isNotBlank()) {
            // A release body is untrusted text from the internet: bounded height, and it scrolls
            // rather than growing the dialog past the screen.
            Column(
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    text = availability.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
