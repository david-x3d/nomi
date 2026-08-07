package com.nomi.app.ui.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortionEditSheet(
    state: PortionEditUiState,
    onCorrectionChanged: (String) -> Unit,
    onInterpret: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(nomiString("Change portion", "Portion ändern"), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.correction,
                onValueChange = onCorrectionChanged,
                label = { Text(nomiString("What should I change?", "Was soll ich ändern?")) },
                placeholder = { Text(nomiString("Actually it was 3 slices", "Tatsächlich waren es 3 Stück")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = onInterpret,
                enabled = state.correction.isNotBlank() && !state.isProcessing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text(
                    if (state.isProcessing) {
                        nomiString("Interpreting…", "Wird interpretiert…")
                    } else {
                        nomiString("Preview change", "Änderung prüfen")
                    },
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            state.proposed?.let { proposed ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PortionCard(
                        title = nomiString("Before", "Vorher"),
                        quantity = "${state.current.currentQuantity} ${state.current.currentUnit}",
                        calories = state.current.calories,
                        modifier = Modifier.weight(1f),
                    )
                    PortionCard(
                        title = nomiString("After", "Nachher"),
                        quantity = "${proposed.newQuantity} ${proposed.newUnit}",
                        calories = state.current.calories * proposed.multiplier,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(proposed.interpretation, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(nomiString("Apply", "Übernehmen"))
                }
            }
        }
    }
}

@Composable
private fun PortionCard(
    title: String,
    quantity: String,
    calories: Double,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title.uppercase(locale), style = MaterialTheme.typography.labelMedium)
            Text(quantity, style = MaterialTheme.typography.titleMedium)
            Text("${calories.roundToInt()} kcal")
        }
    }
}
