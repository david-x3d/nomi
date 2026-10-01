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
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.domain.DecimalInput
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiMessage
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
    var error by remember(item) { mutableStateOf<String?>(null) }
    val edited = EditedValues.parseOrNull(quantity, calories, protein, carbs, fat)
    val valid = unit.isNotBlank() && edited != null

    NomiDialog(
        onDismissRequest = onDismiss,
        title = item.name,
        icon = Icons.Default.Tune,
        subtitle = nomiString("Values are saved as an immutable snapshot for this log entry."),
        confirmLabel = nomiString("Apply"),
        onConfirm = confirm@{
            // The button is disabled while a field does not parse, so this is only a guard.
            val values = edited ?: return@confirm
            val nutrientsChanged = values.calories != item.calories ||
                values.proteinGrams != item.proteinGrams ||
                values.carbohydrateGrams != item.carbohydrateGrams ||
                values.fatGrams != item.fatGrams
            val amountChanged = values.quantity != item.quantity || unit.trim() != item.unit
            // Applying an untouched dialog is not a correction. It used to relabel a verified
            // source reading as a hand-made estimate.
            if (!nutrientsChanged && !amountChanged) {
                onDismiss()
            } else runCatching {
                // Both steps go through the normalizer so the item keeps a serving basis that
                // still describes its numbers, or none at all. Writing the fields straight onto
                // the item left the old basis attached, and validateBeforeSave then rejected the
                // whole meal on save.
                val corrected = if (nutrientsChanged) {
                    ServingNutritionNormalizer.applyUserNutrientCorrection(
                        item = item,
                        calories = values.calories,
                        proteinGrams = values.proteinGrams,
                        carbohydrateGrams = values.carbohydrateGrams,
                        fatGrams = values.fatGrams,
                    )
                } else {
                    item
                }
                onSave(
                    if (amountChanged) {
                        ServingNutritionNormalizer.applyUserAmountOverride(
                            item = corrected,
                            quantity = values.quantity,
                            unit = unit,
                        )
                    } else {
                        corrected
                    },
                )
                onDismiss()
            }.onFailure { failure ->
                // An impossible correction is refused by the normalizer. Thrown from a click
                // handler it took the whole app down instead of saying so.
                error = failure.message ?: "The serving amount could not be validated."
            }
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
        error?.let { message ->
            Text(
                text = nomiMessage(message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

/** The five numeric fields of the dialog, present only when every one of them is usable. */
private data class EditedValues(
    val quantity: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
) {
    companion object {
        /** Null unless all five parse, none is negative and the amount is above zero. */
        fun parseOrNull(
            quantity: String,
            calories: String,
            protein: String,
            carbs: String,
            fat: String,
        ): EditedValues? {
            val parsed = listOf(quantity, calories, protein, carbs, fat)
                .map { DecimalInput.parseOrNull(it)?.takeIf { value -> value >= 0.0 } ?: return null }
            if (parsed[0] <= 0.0) return null
            return EditedValues(parsed[0], parsed[1], parsed[2], parsed[3], parsed[4])
        }
    }
}
