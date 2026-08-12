package com.nomi.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiString

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

    NomiDialog(
        onDismissRequest = onDismiss,
        title = item.name,
        icon = Icons.Default.Tune,
        subtitle = nomiString("Values are saved as an immutable snapshot for this log entry."),
        confirmLabel = nomiString("Apply"),
        onConfirm = {
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
        confirmEnabled = valid,
        dismissLabel = nomiString("Cancel"),
        contentSpacing = 10.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(quantity, { quantity = it }, nomiString("Amount"), Modifier.weight(1f))
            NomiTextField(
                value = unit,
                onValueChange = { unit = it.take(24) },
                label = nomiString("Unit"),
                modifier = Modifier.weight(1f),
            )
        }
        DecimalField(calories, { calories = it }, nomiString("Calories"), Modifier.fillMaxWidth())
        Text(
            text = nomiString("Macros in grams"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(protein, { protein = it }, nomiString("Protein"), Modifier.weight(1f))
            DecimalField(carbs, { carbs = it }, nomiString("Carbs"), Modifier.weight(1f))
            DecimalField(fat, { fat = it }, nomiString("Fat"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    NomiTextField(
        value = value,
        onValueChange = { next ->
            onValueChanged(next.filter { it.isDigit() || it == '.' || it == ',' })
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
