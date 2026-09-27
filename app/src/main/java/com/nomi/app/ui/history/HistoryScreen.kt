package com.nomi.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiMenu
import com.nomi.app.ui.components.NomiMenuItem
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
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
    var showDatePicker by remember { mutableStateOf(false) }    // The pick waiting for a name, resolved once at the moment the user confirmed it. Holding the
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
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.visibleDays.isEmpty()) {
                item(key = "empty-history") {
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .animateContentSize(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            )
                            .fillMaxWidth()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            nomiString("Your logged days will appear here."),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (state.query.isBlank()) {
                                nomiString("Start by logging a meal on Today.")
                            } else {
                                nomiFormat("No foods match “{0}”.", state.query)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                state.visibleDays.forEach { day ->
                    item(key = "summary-${day.date}") {
                        DayHeader(
                            day = day,
                            // Copying today onto today would only duplicate the plate, so the
                            // copy actions are hidden on the current day rather than offered and
                            // silently producing a double entry. Saving is not a copy, so it stays.
                            showCopyActions = !day.isSameDayAs(today),
                            onCopyDay = { onCopyDay(day) },
                            onStartSelection = { action -> onStartSelection(day, action) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(day.entries, key = { "${day.date}-${it.id}" }) { entry ->
                        val onThisDay = selection?.day == day.date
                        HistoryRow(
                            entry = entry,
                            // While a picker is open, the other days are context rather than
                            // targets: a tap there must not navigate away from a half-made
                            // selection, and it must not quietly join another day to it either.
                            selectable = onThisDay,
                            selected = selection?.isSelected(entry.id) == true,
                            onClick = when {
                                onThisDay -> ({ onToggleSelection(entry.id) })
                                selection != null -> null
                                else -> ({ onFoodClick(entry.id) })
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
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
 * The compact confirmation that sits at the bottom of the screen while a selection is open.
 *
 * It exists only during a selection, so the browsing screen keeps no permanent bar. The button
 * counts what is selected, which is what makes a second, unwanted tap visible before it is paid
 * for, and it stays disabled at zero rather than quietly doing nothing.
 */
@Composable
private fun SelectionBar(selection: HistorySelection, onConfirm: () -> Unit) {
    val count = selection.count
    val label = if (count == 0) {
        nomiString(selection.action.emptyConfirmLabelKey())
    } else {
        nomiFormat(selection.action.confirmLabelKey(count), count)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (count == 0) {
                    nomiString("Nothing selected")
                } else {
                    nomiFormat("{0} selected", count)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onConfirm, enabled = count > 0) {
                Text(label, maxLines = 1)
            }
        }
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

@Composable
private fun DayHeader(
    day: HistoryDay,
    showCopyActions: Boolean,
    onCopyDay: () -> Unit,
    onStartSelection: (HistorySelectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(day.date) { mutableStateOf(false) }
    val locale = nomiLocale()
    Column(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.date.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    nomiFormat(
                        "{0} / {1} kcal · P {2} · C {3} · F {4}",
                        day.calories.roundToInt(),
                        day.calorieTarget.roundToInt(),
                        day.proteinGrams.roundToInt(),
                        day.carbohydrateGrams.roundToInt(),
                        day.fatGrams.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // One overflow instead of a row of chips. v2.4.0 grew to five of them on a four-meal
            // day, which turned a browsable list into a toolbar; three actions do not need three
            // permanent targets to stay discoverable.
            Box {
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
    }
}

/**
 * One logged food.
 *
 * Outside a selection this is a link to that food's own detail screen. Inside one it becomes a
 * checkbox whose touch target is the whole row, because the row is what a user aims at: a 24 dp
 * tick in the corner of a 72 dp row is not a comfortable thing to hit repeatedly. The same gesture
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
    val categoryLabel = localizedMealName(entry.mealCategory)
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$categoryLabel · ${entry.amountText}") },
        trailingContent = if (selectable) {
            { SelectionCheck(visible = selected) }
        } else {
            { Text("${entry.calories.roundToInt()} kcal") }
        },
        colors = ListItemDefaults.colors(containerColor = container),
        // animateItem arrives from the list scope, where it is available; the row itself only owns
        // the size change its own content causes.
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth()
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
            ),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
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
