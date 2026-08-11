package com.nomi.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Corrects the eaten amount of an entry that is already saved.
 *
 * The new calorie figure is previewed live, because the correction is plain proportional
 * arithmetic on the stored values rather than a new research request.
 */
@Composable
fun LoggedAmountEditDialog(
    state: LoggedAmountEditUiState,
    onAmountChanged: (String) -> Unit,
    onCorrectionChanged: (String) -> Unit,
    onInterpretCorrection: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = nomiLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(nomiString("How much did you eat?", "Wie viel hast du gegessen?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = onAmountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.error == LoggedAmountEditError.INVALID_AMOUNT,
                    label = { Text(nomiString("Amount", "Menge")) },
                    suffix = { Text(state.unit) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                )
                Text(
                    nomiString(
                        "Or describe the change",
                        "Oder beschreibe die \u00c4nderung",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.correctionText,
                    onValueChange = onCorrectionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving && !state.isInterpreting,
                    minLines = 2,
                    maxLines = 3,
                    isError = state.error == LoggedAmountEditError.INVALID_CORRECTION ||
                        state.error == LoggedAmountEditError.INTERPRETATION_FAILED,
                    label = { Text(nomiString("What changed?", "Was hat sich ge\u00e4ndert?")) },
                    placeholder = {
                        Text(
                            nomiString(
                                "For example: I ate 60 g less",
                                "Zum Beispiel: Ich habe 60 g weniger gegessen",
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onInterpretCorrection() }),
                )
                FilledTonalButton(
                    onClick = onInterpretCorrection,
                    enabled = state.canInterpret,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isInterpreting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    }
                    Text(
                        nomiString(
                            if (state.isInterpreting) "Calculating..." else "Calculate amount",
                            if (state.isInterpreting) "Wird berechnet..." else "Menge berechnen",
                        ),
                    )
                }
                state.interpretation?.let { interpretation ->
                    Text(
                        interpretation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val previewCalories = state.previewCalories
                Text(
                    text = when {
                        previewCalories == null -> nomiString(
                            "Previously ${formatLoggedAmount(state.originalAmount, locale)} " +
                                "${state.unit} · ${state.originalCalories.roundToInt()} kcal",
                            "Bisher ${formatLoggedAmount(state.originalAmount, locale)} " +
                                "${state.unit} · ${state.originalCalories.roundToInt()} kcal",
                        )
                        else -> nomiString(
                            "${state.originalCalories.roundToInt()} kcal becomes " +
                                "${previewCalories.roundToInt()} kcal",
                            "${state.originalCalories.roundToInt()} kcal werden " +
                                "${previewCalories.roundToInt()} kcal",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.error?.let { error ->
                    Text(
                        text = error.localizedMessage(state.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSave) {
                Text(nomiString("Save", "Speichern"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(nomiString("Cancel", "Abbrechen"))
            }
        },
    )
}

@Composable
private fun LoggedAmountEditError.localizedMessage(unit: String): String = when (this) {
    LoggedAmountEditError.INVALID_AMOUNT ->
        nomiString("Enter an amount in $unit", "Gib eine Menge in $unit ein")
    LoggedAmountEditError.INVALID_CORRECTION ->
        nomiString(
            "Describe only how the amount changed.",
            "Beschreibe nur, wie sich die Menge ge\u00e4ndert hat.",
        )
    LoggedAmountEditError.INTERPRETATION_FAILED ->
        nomiString(
            "Nomi couldn't understand that amount change.",
            "Nomi konnte diese Mengen\u00e4nderung nicht verstehen.",
        )
    LoggedAmountEditError.ENTRY_GONE ->
        nomiString("That entry is no longer available", "Dieser Eintrag ist nicht mehr vorhanden")
    LoggedAmountEditError.SAVE_FAILED ->
        nomiString("Nomi couldn't change that amount", "Nomi konnte die Menge nicht ändern")
}

private fun formatLoggedAmount(amount: Double, locale: Locale): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        String.format(locale, "%.1f", amount)
    }
