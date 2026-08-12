package com.nomi.app.ui.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.NomiSheetHeader
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import kotlin.math.roundToInt

@Composable
fun PortionEditSheet(
    state: PortionEditUiState,
    onCorrectionChanged: (String) -> Unit,
    onInterpret: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onResearch: () -> Unit = {},
) {
    NomiSheet(onDismissRequest = onDismiss) {
        NomiSheetHeader(
            title = nomiString("Change this food"),
            subtitle = "${state.current.currentQuantity} ${state.current.currentUnit}",
            icon = Icons.Default.EditNote,
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NomiTextField(
                value = state.correction,
                onValueChange = onCorrectionChanged,
                label = nomiString("What should I change?"),
                placeholder = nomiString("Half, or actually it was tuna"),
                singleLine = false,
                minLines = 2,
            )
            FilledTonalButton(
                onClick = onInterpret,
                enabled = state.correction.isNotBlank() && !state.isProcessing,
                shape = NomiShapes.Action,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isProcessing) {
                        nomiString("Interpreting…")
                    } else {
                        nomiString("Preview change")
                    },
                )
            }
            state.errorMessage?.let { NomiInlineError(it) }
            // An edit that changes the food cannot be answered by arithmetic, so it asks
            // before spending a web search rather than quietly running one.
            if (state.needsResearch) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            nomiString("That changes the food, not just the amount"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            state.researchReason ?: nomiString("Nomi needs to look up nutrition for the corrected food."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onResearch,
                            enabled = !state.isProcessing,
                            shape = NomiShapes.Action,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                if (state.isProcessing) {
                                    nomiString("Looking it up…")
                                } else {
                                    nomiString("Look it up again")
                                },
                            )
                        }
                    }
                }
            }
            state.proposed?.let { proposed ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PortionCard(
                        title = nomiString("Before"),
                        quantity = "${state.current.currentQuantity} ${state.current.currentUnit}",
                        calories = state.current.calories,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PortionCard(
                        title = nomiString("After"),
                        quantity = "${proposed.newQuantity} ${proposed.newUnit}",
                        calories = state.current.calories * proposed.multiplier,
                        highlighted = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    proposed.interpretation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onApply,
                    shape = NomiShapes.Action,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(nomiString("Apply"))
                }
            }
        }
    }
}

/**
 * One side of the before/after comparison. The "after" card takes the accent container so the
 * pair reads as a change with a direction rather than as two equal readings.
 */
@Composable
private fun PortionCard(
    title: String,
    quantity: String,
    calories: Double,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val locale = nomiLocale()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title.uppercase(locale),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(quantity, style = MaterialTheme.typography.titleMedium)
            Text("${calories.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
