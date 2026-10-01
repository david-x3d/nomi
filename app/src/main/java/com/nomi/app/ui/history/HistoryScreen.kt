package com.nomi.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiFox
import com.nomi.app.ui.components.NomiFoxMood
import com.nomi.app.ui.components.NomiMenu
import com.nomi.app.ui.components.NomiMenuItem
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.hairlineOnPitchBlack
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import com.nomi.app.ui.theme.nomiPageContainerColor
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.rowDescription
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    today: LocalDate,
    onQueryChanged: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onFoodClick: (Long) -> Unit,
    onCopyDay: (HistoryDay) -> Unit,
    onStartSelection: (HistoryDay, HistorySelectionAction) -> Unit,
    onCancelSelection: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSaveMeal: (HistoryDay, List<Long>, String) -> Unit,
    onAddToToday: (HistoryDay, List<Long>) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    selection: HistorySelection? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    // The pick waiting for a name, resolved once at the moment the user confirmed it. Holding the
    // day and the log ids means the name step does not have to find them in live state again,
    // which could fail under the user's feet if the log changed while the dialog was open.
    var pendingSave by remember { mutableStateOf<PendingMealSave?>(null) }
    // Back has to mean "give the selection up" while the picker is open, exactly as the close icon
    // in the bar does. Without this, a swipe-back would leave History looking as though a pick were
    // still in progress when it came back.
    if (selection != null) {
        BackHandler(onBack = onCancelSelection)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The same canvas as the food library: both are reached from Today and read as its archive.
    val pageContainerColor = nomiPageContainerColor(
        accent = MaterialTheme.colorScheme.secondaryContainer,
        strength = 0.07f,
    )
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = pageContainerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageContainerColor,
                    scrolledContainerColor = pageContainerColor,
                ),
                // The picker is named after the action that opened it, and leaves through a close
                // icon rather than the back arrow: leaving the screen and giving up a selection are
                // two different intentions and should not share a gesture.
                title = { Text(nomiString(selection?.action?.titleKey() ?: "History")) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selection != null) onCancelSelection() else onBack() },
                    ) {
                        Icon(
                            imageVector = if (selection != null) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = nomiString(
                                if (selection != null) "Cancel selection" else "Back",
                            ),
                        )
                    }
                },
                actions = {
                    // Browsing keeps only the date picker up here. A day's own actions live with the
                    // day, so History reads as a browser rather than a control panel.
                    if (selection == null) {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = nomiString("Choose date"),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            selection?.let { active ->
                SelectionBar(
                    selection = active,
                    onConfirm = {
                        when (active.action) {
                            // Adding to today is finished in one step, so it goes straight through
                            // and the picker closes behind the new entries.
                            HistorySelectionAction.ADD_TO_TODAY -> {
                                val day = state.visibleDays.firstOrNull { it.date == active.day }
                                if (day != null) {
                                    onAddToToday(day, day.logIdsFor(active.selectedRowIds))
                                    onCancelSelection()
                                }
                            }
                            // Saving a meal still needs a name, which is the one thing tapping a
                            // row cannot supply.
                            HistorySelectionAction.SAVE_MEAL -> {
                                val day = state.visibleDays.firstOrNull { it.date == active.day }
                                    ?: return@SelectionBar
                                pendingSave = PendingMealSave(
                                    day = day,
                                    logIds = day.logIdsFor(active.selectedRowIds),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(pageContainerColor),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (selection == null) {
                item {
                    NomiTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        label = nomiString("Search history"),
                        placeholder = nomiString("Toast, McDonald's, Banana…"),
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                item(key = "selection-hint") {
                    Text(
                        text = nomiString(selection.action.hintKey()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (state.visibleDays.isEmpty()) {
                item(key = "empty-history") {
                    EmptyHistory(query = state.query, modifier = Modifier.animateItem())
                }
            } else {
                items(state.visibleDays, key = { "day-${it.date}" }) { day ->
                    DayCard(
                        day = day,
                        // Copying today onto today would only duplicate the plate, so the
                        // copy actions are hidden on the current day rather than offered and
                        // silently producing a double entry. Saving is not a copy, so it stays.
                        showCopyActions = !day.isSameDayAs(today),
                        selection = selection,
                        onCopyDay = { onCopyDay(day) },
                        onStartSelection = { action -> onStartSelection(day, action) },
                        onFoodClick = onFoodClick,
                        onToggleSelection = onToggleSelection,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        NomiDatePickerDialog(
            state = pickerState,
            onDismissRequest = { showDatePicker = false },
            onConfirm = {
                pickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                showDatePicker = false
            },
            confirmLabel = nomiString("Select"),
            dismissLabel = nomiString("Cancel"),
            showModeToggle = false,
        )
    }

    pendingSave?.let { pending ->
        SaveMealNameDialog(
            day = pending.day,
            selectedCount = pending.logIds.size,
            onDismiss = { pendingSave = null },
            onConfirm = { name ->
                pendingSave = null
                onSaveMeal(pending.day, pending.logIds, name)
                onCancelSelection()
            },
        )
    }
}

/**
 * A pick waiting for a name: the day it came from and the exact log rows it will save.
 *
 * Resolved when the user confirms rather than looked up again afterwards, so the name step cannot
 * come back to an empty list because a log moved underneath it.
 */
private data class PendingMealSave(val day: HistoryDay, val logIds: List<Long>)

/**
 * The compact confirmation that floats at the bottom of the screen while a selection is open.
 *
 * It exists only during a selection, so the browsing screen keeps no permanent bar. The button
 * counts what is selected, which is what makes a second, unwanted tap visible before it is paid
 * for, and it stays disabled at zero rather than quietly doing nothing.
 *
 * A pill over the canvas rather than an edge-to-edge slab: the days keep scrolling behind it, so
 * the picker still reads as the same page with one thing added.
 */
@Composable
private fun SelectionBar(selection: HistorySelection, onConfirm: () -> Unit) {
    val count = selection.count
    val label = if (count == 0) {
        nomiString(selection.action.emptyConfirmLabelKey())
    } else {
        nomiFormat(selection.action.confirmLabelKey(count), count)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            border = hairlineOnPitchBlack(),
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (count == 0) {
                        nomiString("Nothing selected")
                    } else {
                        nomiFormat("{0} selected", count)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onConfirm, enabled = count > 0) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

/**
 * What History shows when there is no day to list: the fox, and one line on why.
 *
 * An empty log and a search with no hits are different situations, so they get different words -
 * telling someone who has months of entries that their days "will appear here" would be wrong.
 */
@Composable
private fun EmptyHistory(query: String, modifier: Modifier = Modifier) {
    val searching = query.isNotBlank()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f))
                // The words underneath say everything; the fox is company, not content.
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            NomiFox(
                mood = if (searching) NomiFoxMood.CONCERNED else NomiFoxMood.RESTING,
                size = 98.dp,
            )
        }
        Text(
            text = nomiString(
                if (searching) "Nothing found" else "Your logged days will appear here.",
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (searching) {
                nomiFormat("No foods match “{0}”.", query)
            } else {
                nomiString("Start by logging a meal on Today.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Asks for the name the picked foods will be filed under in the food library.
 *
 * Only the picked rows are saved, at the portions that were logged, so the name is the only thing
 * left to choose. It arrives pre-filled with a date-derived suggestion that one tap accepts.
 */
@Composable
private fun SaveMealNameDialog(
    day: HistoryDay,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val locale = nomiLocale()
    // Hoisted out of the remember block: remember's calculation lambda is not a composable
    // context, so nomiString cannot be called inside it.
    val defaultName = defaultSavedMealName(day.date, locale, nomiString("Meal"))
    var name by remember(day.date) { mutableStateOf(defaultName) }
    NomiDialog(
        onDismissRequest = onDismiss,
        title = nomiString("Save as a meal"),
        subtitle = day.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale),
        ),
        icon = Icons.Default.BookmarkAdd,
        confirmLabel = nomiString("Save"),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { if (name.isNotBlank()) onConfirm(name.trim()) },
        dismissLabel = nomiString("Cancel"),
    ) {
        NomiTextField(
            value = name,
            onValueChange = { name = it },
            label = nomiString("Meal name"),
            singleLine = true,
        )
        Text(
            text = nomiFormat("{0} of this day's foods will be saved.", selectedCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One logged day: what it added up to, and under that the foods it was made of.
 *
 * The day is the unit History works in - it is what gets copied, saved and picked from - so it is
 * also the unit on screen: one card, with its own total and its own actions, instead of a header
 * floating over a list that runs into the next day.
 */
@Composable
private fun DayCard(
    day: HistoryDay,
    showCopyActions: Boolean,
    selection: HistorySelection?,
    onCopyDay: () -> Unit,
    onStartSelection: (HistorySelectionAction) -> Unit,
    onFoodClick: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onThisDay = selection?.day == day.date
    // While a picker is open, the other days are context rather than targets: a tap there must
    // not navigate away from a half-made selection, and it must not quietly join another day to
    // it either. They step back so it is visible which day is being picked from.
    val rowAlpha by animateFloatAsState(
        targetValue = if (selection != null && !onThisDay) 0.5f else 1f,
        animationSpec = nomiFadeMotionSpec(),
        label = "history_day_rows",
    )
    NomiCard(
        modifier = modifier.animateContentSize(animationSpec = nomiLayoutMotionSpec()),
        contentPadding = PaddingValues(top = 12.dp, bottom = 10.dp),
        spacing = 0.dp,
    ) {
        DaySummary(
            day = day,
            showCopyActions = showCopyActions,
            onCopyDay = onCopyDay,
            onStartSelection = onStartSelection,
        )
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .graphicsLayer { alpha = rowAlpha },
        ) {
            day.entries.forEach { entry ->
                HistoryRow(
                    entry = entry,
                    selectable = onThisDay,
                    selected = selection?.isSelected(entry.id) == true,
                    onClick = when {
                        onThisDay -> ({ onToggleSelection(entry.id) })
                        selection != null -> null
                        else -> ({ onFoodClick(entry.id) })
                    },
                )
            }
        }
    }
}

@Composable
private fun DaySummary(
    day: HistoryDay,
    showCopyActions: Boolean,
    onCopyDay: () -> Unit,
    onStartSelection: (HistorySelectionAction) -> Unit,
) {
    var menuExpanded by remember(day.date) { mutableStateOf(false) }
    val locale = nomiLocale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.date.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale),
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            // One overflow instead of a row of chips. v2.4.0 grew to five of them on a four-meal
            // day, which turned a browsable list into a toolbar; three actions do not need three
            // permanent targets to stay discoverable.
            Box(modifier = Modifier.padding(end = 8.dp)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = nomiString("Actions for this day"),
                    )
                }
                NomiMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (showCopyActions) {
                        NomiMenuItem(
                            text = nomiString("Copy day"),
                            leadingIcon = Icons.Default.ContentCopy,
                            onClick = {
                                menuExpanded = false
                                onCopyDay()
                            },
                        )
                        NomiMenuItem(
                            text = nomiString("Add items to today"),
                            leadingIcon = Icons.Default.Add,
                            onClick = {
                                menuExpanded = false
                                onStartSelection(HistorySelectionAction.ADD_TO_TODAY)
                            },
                        )
                    }
                    NomiMenuItem(
                        text = nomiString("Save meal"),
                        leadingIcon = Icons.Default.BookmarkAdd,
                        onClick = {
                            menuExpanded = false
                            onStartSelection(HistorySelectionAction.SAVE_MEAL)
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The same reading as the goals card on Today: what was eaten against the target, and
            // one bar for how far along that is. The bar stops at full instead of changing colour
            // past it, because Nomi reports a day and does not grade it.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = day.calories.roundToInt().toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (day.calorieTarget > 0.0) {
                            " / ${day.calorieTarget.roundToInt()} kcal"
                        } else {
                            " kcal"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (day.calorieTarget > 0.0) {
                    LinearProgressIndicator(
                        progress = { (day.calories / day.calorieTarget).toFloat().coerceIn(0f, 1f) },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clearAndSetSemantics {},
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Order and colours follow the rings on Today, so a macro is the same colour
                // wherever it is met.
                MacroValue(
                    label = nomiString("Carbs"),
                    grams = day.carbohydrateGrams,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MacroValue(
                    label = nomiString("Protein"),
                    grams = day.proteinGrams,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                MacroValue(
                    label = nomiString("Fat"),
                    grams = day.fatGrams,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacroValue(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = nomiFormat("{0} g", grams.roundToInt()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val HistoryRowShape = RoundedCornerShape(20.dp)

/**
 * One logged food, written the way Today writes it: the food on one line, its meal and amount
 * under it, the calories at the end.
 *
 * Outside a selection this is a link to that food's own detail screen. Inside one it becomes a
 * checkbox whose touch target is the whole row, because the row is what a user aims at: a 24 dp
 * tick in the corner of a 64 dp row is not a comfortable thing to hit repeatedly. The same gesture
 * therefore means "pick it" only while a picker is open, and nothing about the browsing screen has
 * to change to support it.
 */
@Composable
private fun HistoryRow(
    entry: TodayFoodEntry,
    selectable: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    val supporting = listOf(
        localizedMealName(entry.mealCategory),
        if (entry.amountText.isNotBlank() || entry.amount > 0.0) {
            entry.quantityDisplay(locale).withContext
        } else {
            ""
        },
    ).filter(String::isNotBlank).joinToString(" · ")
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = nomiFadeMotionSpec(),
        label = "history_row_container",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        // The row is inset from the card's edge and rounded, so both the selected tone and the
        // press ripple are a shape inside the card rather than a band cut off by it.
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(HistoryRowShape)
            .background(container)
            .then(
                when {
                    selectable -> Modifier.toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onClick?.invoke() },
                    )
                    // No handler at all while another day's picker is open, so the row is
                    // genuinely inert rather than quietly doing something else.
                    onClick == null -> Modifier
                    else -> Modifier.clickable { onClick.invoke() }
                },
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.rowDescription(),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor,
            )
        }
        if (selectable) {
            SelectionCheck(visible = selected)
        } else {
            Text(
                text = "${entry.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * The check the selected row carries.
 *
 * The slot is always laid out, so a row's text cannot jump sideways as items are picked, and the
 * tick fades in rather than appearing, so a tap reads as a response instead of a redraw. The state
 * is carried by the row's own container colour and by its checkbox role as well, so this is a
 * reinforcement rather than the only signal.
 */
@Composable
private fun SelectionCheck(visible: Boolean) {
    // Nomi's own spec, not Material's motion scheme: this is the one that collapses to a snap when
    // the system animation scale is zero, so a disabled-animations user sees the tick change state
    // rather than watch it fade.
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = nomiFadeMotionSpec(),
        label = "history_selection_check",
    )
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (alpha > 0.01f) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { this.alpha = alpha },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun localizedMealName(mealCategory: MealCategory): String = when (mealCategory) {
    MealCategory.BREAKFAST -> nomiString("Breakfast")
    MealCategory.LUNCH -> nomiString("Lunch")
    MealCategory.DINNER -> nomiString("Dinner")
    MealCategory.SNACKS -> nomiString("Snacks")
}
