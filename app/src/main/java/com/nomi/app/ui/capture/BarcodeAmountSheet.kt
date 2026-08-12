package com.nomi.app.ui.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiFieldShape
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.NomiSheetHeader
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarcodeAmountSheet(
    state: BarcodeAmountUiState,
    onAmountChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onCalculate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NomiSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("barcode_amount_sheet"),
    ) {
        NomiSheetHeader(
            title = nomiString("How much did you eat?"),
            subtitle = listOfNotNull(state.sourceItem.brand, state.sourceItem.name)
                .joinToString(" · "),
            icon = Icons.Outlined.QrCodeScanner,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NomiTextField(
                value = state.amount,
                onValueChange = onAmountChanged,
                modifier = Modifier.testTag("barcode_amount_input"),
                label = nomiString("Amount eaten"),
                isError = state.amount.isNotBlank() && state.parsedAmount == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (state.canCalculate) onCalculate() },
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = nomiString("Unit"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.compatibleUnits.forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { onUnitChanged(unit) },
                            shape = NomiShapes.Action,
                            label = { Text(unit, maxLines = 1) },
                            modifier = Modifier.testTag("barcode_unit_$unit"),
                        )
                    }
                }
            }

            Surface(
                shape = NomiFieldShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                AnimatedContent(
                    targetState = "${state.amount.ifBlank { "—" }} ${state.unit}",
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "barcode amount summary",
                ) { amountLabel ->
                    Text(
                        text = nomiFormat(
                            "Nomi will normalize the source to per 100 first, then calculate {0}.",
                            amountLabel,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            state.servingLabel?.takeIf(String::isNotBlank)?.let { serving ->
                Text(
                    text = nomiFormat("Package serving: {0}", serving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = state.errorMessage != null) {
                NomiInlineError(
                    message = state.errorMessage.orEmpty(),
                    modifier = Modifier.testTag("barcode_amount_error"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = NomiShapes.Action,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(nomiString("Cancel"), maxLines = 1)
                }
                Button(
                    onClick = onCalculate,
                    enabled = state.canCalculate,
                    shape = NomiShapes.Action,
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 56.dp)
                        .testTag("barcode_calculate"),
                ) {
                    Text(nomiString("Calculate nutrition"), maxLines = 1)
                }
            }
        }
    }
}
