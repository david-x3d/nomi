package com.nomi.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiFieldShape
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
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
    NomiDialog(
        onDismissRequest = onDismiss,
        title = nomiString("How much did you eat?"),
        icon = Icons.Default.Scale,
        subtitle = state.name,
        confirmLabel = nomiString("Save"),
        onConfirm = onConfirm,
        confirmEnabled = state.canSave,
        dismissLabel = nomiString("Cancel"),
    ) {
        NomiTextField(
            value = state.amountText,
            onValueChange = onAmountChanged,
            isError = state.error == LoggedAmountEditError.INVALID_AMOUNT,
            label = nomiString("Amount"),
            suffix = state.unit,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
        // Both inputs answer the same question, so a labelled rule says they are alternatives
        // rather than a second thing left to fill in.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = nomiString("or describe it"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        NomiTextField(
            value = state.correctionText,
            onValueChange = onCorrectionChanged,
            enabled = !state.isSaving && !state.isInterpreting,
            singleLine = false,
            minLines = 2,
            maxLines = 3,
            isError = state.error == LoggedAmountEditError.INVALID_CORRECTION ||
                state.error == LoggedAmountEditError.INTERPRETATION_FAILED,
            label = nomiString("What changed?"),
            placeholder = nomiString("For example: I ate 60 g less"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onInterpretCorrection() }),
        )
        FilledTonalButton(
            onClick = onInterpretCorrection,
            enabled = state.canInterpret,
            shape = NomiShapes.Action,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.isInterpreting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                nomiString(if (state.isInterpreting) "Calculating..." else "Calculate amount"),
            )
        }
        state.interpretation?.let { interpretation ->
            Text(
                interpretation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The arithmetic is the whole reason to tap Save, so it gets a surface of its own
        // rather than a line in the run of body text.
        val previewCalories = state.previewCalories
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = NomiFieldShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = when {
                    previewCalories == null -> nomiFormat(
                        "Previously {0} {1} · {2} kcal",
                        formatLoggedAmount(state.originalAmount, locale),
                        state.unit,
                        state.originalCalories.roundToInt(),
                    )
                    else -> nomiFormat(
                        "{0} kcal becomes {1} kcal",
                        state.originalCalories.roundToInt(),
                        previewCalories.roundToInt(),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        state.error?.let { error ->
            NomiInlineError(error.localizedMessage(state.unit))
        }
    }
}

@Composable
private fun LoggedAmountEditError.localizedMessage(unit: String): String = when (this) {
    LoggedAmountEditError.INVALID_AMOUNT ->
        nomiFormat("Enter an amount in {0}", unit)
    LoggedAmountEditError.INVALID_CORRECTION ->
        nomiString("Describe only how the amount changed.")
    LoggedAmountEditError.INTERPRETATION_FAILED ->
        nomiString("Nomi couldn't understand that amount change.")
    LoggedAmountEditError.ENTRY_GONE ->
        nomiString("That entry is no longer available")
    LoggedAmountEditError.SAVE_FAILED ->
        nomiString("Nomi couldn't change that amount")
}

private fun formatLoggedAmount(amount: Double, locale: Locale): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        String.format(locale, "%.1f", amount)
    }
