package com.nomi.app.ui.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nomi.app.data.backup.BackupInspection
import com.nomi.app.di.AppContainer
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.localization.nomiString
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What the backup rows in Settings can do: three document pickers and the restore itself. */
internal class BackupActions(
    val export: () -> Unit,
    val exportDiary: () -> Unit,
    val import: () -> Unit,
    val restore: (BackupInspection) -> Unit,
)

/**
 * Wires the system document pickers to the backup and diary services.
 *
 * Exports write straight to the picked document. An import is only inspected: the file's
 * contents are handed to [onInspected] so the user can see what would be replaced before
 * anything is, and [BackupActions.restore] then does the replacing. [onRestored] runs once that
 * has gone through, so the caller can refresh whatever read the old data.
 *
 * The restore runs in this function's scope rather than the confirmation dialog's: the dialog
 * leaves the screen the moment it is confirmed, and its scope would take the import with it.
 */
@Composable
internal fun rememberBackupActions(
    container: AppContainer,
    showMessage: (String) -> Unit,
    onInspected: (BackupInspection) -> Unit,
    onRestored: () -> Unit,
): BackupActions {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val selectedDocumentOpenError = nomiString("The selected document could not be opened")
    val backupExportedMessage = nomiString("Backup exported")
    val backupExportFailedMessage = nomiString("Nomi couldn't export the backup")
    val diaryExportedMessage = nomiString("Diary exported")
    val diaryExportFailedMessage = nomiString("Nomi couldn't export the diary")
    val invalidBackupMessage = nomiString("That isn't a valid Nomi backup")
    val backupRestoredMessage = nomiString("Backup restored")
    val backupRestoreFailedMessage = nomiString("Nomi couldn't restore that backup")

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openOutputStream(uri, "wt")?.use { container.backupService.exportTo(it) }
                    ?: error(selectedDocumentOpenError)
            }.onSuccess { showMessage(backupExportedMessage) }
                .onFailure { showMessage(it.message ?: backupExportFailedMessage) }
        }
    }
    val diaryExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openOutputStream(uri, "wt")?.use { container.diaryExportService.exportTo(it) }
                    ?: error(selectedDocumentOpenError)
            }.onSuccess { showMessage(diaryExportedMessage) }
                .onFailure { showMessage(it.message ?: diaryExportFailedMessage) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openInputStream(uri)?.use { container.backupService.inspect(it) }
                    ?: error(selectedDocumentOpenError)
            }.onSuccess(onInspected)
                .onFailure { showMessage(it.message ?: invalidBackupMessage) }
        }
    }
    return BackupActions(
        export = { exportLauncher.launch("nomi-backup-${LocalDate.now()}.json") },
        exportDiary = { diaryExportLauncher.launch("nomi-diary-${LocalDate.now()}.json") },
        import = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
        restore = { inspection ->
            scope.launch {
                runCatching { container.backupService.importValidated(inspection) }
                    .onSuccess {
                        container.reminderScheduler.reconcileFrom(container.preferencesStore)
                        onRestored()
                        showMessage(backupRestoredMessage)
                    }
                    .onFailure { showMessage(it.message ?: backupRestoreFailedMessage) }
            }
        },
    )
}

/** The last question before a backup replaces what is on the phone. */
@Composable
internal fun BackupRestoreDialog(
    inspection: BackupInspection,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val summary = inspection.summary
    NomiDialog(
        onDismissRequest = onDismiss,
        title = nomiString("Replace local Nomi data?"),
        icon = Icons.Default.SettingsBackupRestore,
        subtitle = nomiString("API keys stay on this device and are never imported."),
        confirmLabel = nomiString("Replace data"),
        destructive = true,
        onConfirm = onConfirm,
        dismissLabel = nomiString("Cancel"),
    ) {
        // The counts are what the decision turns on, so they are a readable list rather
        // than a comma-separated sentence to parse under a destructive button.
        BackupSummaryLine(nomiString("Food logs"), summary.foodLogCount)
        BackupSummaryLine(nomiString("Foods"), summary.foodCount)
        BackupSummaryLine(nomiString("Saved meals"), summary.savedMealCount)
        BackupSummaryLine(nomiString("Weights"), summary.weightEntryCount)
        BackupSummaryLine(nomiString("Plans"), summary.nutritionPlanCount)
    }
}

/** One counted category from a validated backup envelope. */
@Composable
private fun BackupSummaryLine(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}
