package com.nomi.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.rowDescription
import java.time.LocalDate
import kotlin.math.roundToInt

/** Select foods from the displayed day through the bottom plus menu. */
@Composable
fun ShareMenuSection(
    dayEntries: List<TodayFoodEntry>,
    day: LocalDate,
    onFinished: () -> Unit,
) {
    val coordinator = LocalNomiShareCoordinator.current
    val foods = remember(dayEntries) {
        dayEntries.flatMap { it.shareableFoods() }.distinctBy { it.id }
    }
    var selectedIds by remember(day) { mutableStateOf(emptySet<Long>()) }
    var includeTotals by remember(day) { mutableStateOf(true) }

    when (coordinator.stage) {
        NomiShareStage.Collapsed -> {
            DropdownMenuItem(
                text = { Text(nomiString("Share")) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                enabled = foods.isNotEmpty(),
                onClick = {
                    selectedIds = emptySet()
                    coordinator.open()
                },
            )
            DropdownMenuItem(
                text = { Text(nomiString("Receive a shared day")) },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                onClick = {
                    coordinator.startReceiving()
                    if (coordinator.isReceiving) onFinished()
                },
            )
        }

        NomiShareStage.PickingFoods -> {
            ShareBackToFoods(nomiString("Back")) { coordinator.closeMenu() }
            ShareCheckRow(
                label = nomiString("Select all"),
                detail = "${foods.count { it.id in selectedIds }} / ${foods.size}",
                checked = foods.isNotEmpty() && foods.all { it.id in selectedIds },
                onToggle = {
                    selectedIds = if (foods.all { it.id in selectedIds }) emptySet()
                    else foods.map { it.id }.toSet()
                },
            )
            foods.forEach { food ->
                ShareCheckRow(
                    label = food.rowDescription(),
                    detail = food.shareableDetail(),
                    checked = food.id in selectedIds,
                    onToggle = { selectedIds = selectedIds.toggled(food.id) },
                )
            }
            ShareCheckRow(
                label = nomiString("Include totals"),
                // Shown from the ticked foods alone, so the number here is the number that will be
                // in the file rather than a figure for the whole day the user never asked about.
                detail = nomiFormat(
                    "{0} in total",
                    "${foods.selectedKcal(selectedIds).roundToInt()} kcal",
                ),
                checked = includeTotals,
                onToggle = { includeTotals = !includeTotals },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(nomiString("Tap to share")) },
                leadingIcon = { Icon(Icons.Default.Nfc, contentDescription = null) },
                // Nothing ticked means nothing to send, and a row that cannot be pressed answers
                // the question sooner than a tap that comes back with an error.
                enabled = foods.any { it.id in selectedIds },
                onClick = {
                    coordinator.offer(
                        day = day,
                        foods = foods,
                        selectedIds = selectedIds,
                        includeTotals = includeTotals,
                    )
                    if (coordinator.stage == NomiShareStage.Sending) onFinished()
                },
            )
        }

        // The tap itself is a screen of its own, so nothing to draw here.
        NomiShareStage.Sending,
        NomiShareStage.Receiving,
        NomiShareStage.Received,
        -> Unit
    }
}

/** The line that takes a user back to the foods, used by the sending screen. */
@Composable
internal fun ShareBackToFoods(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * A tickable line in the menu.
 *
 * The whole line is the target rather than the box alone, and the [Checkbox] is told to do nothing
 * so the row's own toggle is the only thing handling the tap. Without that, touching the box would
 * flip it twice and read as broken.
 */
@Composable
internal fun ShareCheckRow(
    label: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = rememberNomiHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = {
                    haptics.toggled()
                    onToggle()
                },
            )
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** The amount and calories as the row already shows them, so a tick reads like the line it came from. */
private fun TodayFoodEntry.shareableDetail(): String {
    val calories = "${calories.roundToInt()} kcal"
    val amount = amountText.trim()
    return if (amount.isEmpty()) calories else "$amount · $calories"
}

internal fun Set<Long>.toggled(id: Long): Set<Long> =
    if (id in this) this - id else this + id

/** What the ticked foods add up to, so the totals row can show the number the file will carry. */
internal fun List<TodayFoodEntry>.selectedKcal(selectedIds: Set<Long>): Double =
    filter { it.id in selectedIds }.sumOf(TodayFoodEntry::calories)
