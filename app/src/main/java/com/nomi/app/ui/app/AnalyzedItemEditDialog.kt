package com.nomi.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AnalyzedFoodItem

@Composable
fun AnalyzedItemEditDialog(
    item: AnalyzedFoodItem,
    onDismiss: () -> Unit,
    onSave: (AnalyzedFoodItem) -> Unit,
) {
    var quantity by remember(item) { mutableStateOf(item.quantity.toString()) }
    var unit by remember(item) { mutableStateOf(item.unit) }
    var calories by remember(item) { mutableStateOf(item.calories.toString()) }
    var protein by remember(item) { mutableStateOf(item.proteinGrams.toString()) }
    var carbs by remember(item) { mutableStateOf(item.carbohydrateGrams.toString()) }
    var fat by remember(item) { mutableStateOf(item.fatGrams.toString()) }
    val parsed = listOf(quantity, calories, protein, carbs, fat).map { it.replace(',', '.').toDoubleOrNull() }
    val valid = unit.isNotBlank() && parsed.all { it != null && it >= 0.0 } && (parsed.firstOrNull() ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(quantity, { quantity = it }, "Amount", Modifier.weight(1f))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(24) },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                DecimalField(calories, { calories = it }, "Calories", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
                    DecimalField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f))
                    DecimalField(fat, { fat = it }, "Fat g", Modifier.weight(1f))
                }
                Text("Values are saved as an immutable snapshot for this log entry.")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            quantity = parsed[0]!!,
                            unit = unit.trim(),
                            calories = parsed[1]!!,
                            proteinGrams = parsed[2]!!,
                            carbohydrateGrams = parsed[3]!!,
                            fatGrams = parsed[4]!!,
                            isEstimate = true,
                            assumptions = item.assumptions + "Adjusted before saving",
                        ),
                    )
                    onDismiss()
                },
                enabled = valid,
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DecimalField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            onValueChanged(next.filter { it.isDigit() || it == '.' || it == ',' })
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}
