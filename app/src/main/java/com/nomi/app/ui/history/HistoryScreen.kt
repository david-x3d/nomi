package com.nomi.app.ui.history


import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.today.MealCategory
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
    onCopyMeal: (HistoryDay, MealCategory) -> Unit,
    onCopyDay: (HistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    onSaveMeal: (HistoryDay, String) -> Unit,
    onBack: () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }
    // The name is asked for, never invented: saveHistoryDayAsMeal refuses a blank name, so a
    // silent default would look like a broken chip. The field is pre-filled with a date-derived
    // suggestion the user can accept with one tap or retype.
    var pendingSave by remember { mutableStateOf<HistoryDay?>(null) }
    // The title collapses into the bar as the list scrolls, the same way it does on Progress
    // and Settings, so the three top-level screens behave alike.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(nomiString("History")) },
                navigationIcon = {
                    // History is pushed on top of Today, so the bar carries the same way back the
                    // other full-screen destinations do.
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = nomiString("Back"),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = nomiString("Choose date"),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                            // silently producing a double entry.
                            showCopyActions = !day.isSameDayAs(today),
                            onCopyMeal = { mealCategory -> onCopyMeal(day, mealCategory) },
                            onCopyDay = { onCopyDay(day) },
                            onSaveMeal = { pendingSave = day },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(day.entries, key = { "${day.date}-${it.id}" }) { entry ->
                        val categoryLabel = localizedMealName(entry.mealCategory)
                        ListItem(
                            headlineContent = {
                                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text("$categoryLabel · ${entry.amountText}") },
                            trailingContent = { Text("${entry.calories.roundToInt()} kcal") },
                            modifier = Modifier
                                .animateItem()
                                .animateContentSize(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                )
                                .fillMaxWidth()
                                .clickable { onFoodClick(entry.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
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

    pendingSave?.let { day ->
        SaveMealNameDialog(
            day = day,
            onDismiss = { pendingSave = null },
            onConfirm = { name ->
                pendingSave = null
                onSaveMeal(day, name)
            },
        )
    }
}

/**
 * Asks for the name the day will be filed under in the food library.
 *
 * The whole day is saved as one meal, so the name is the only thing the user gets to choose about
 * it; the day itself is already fixed by the day header that was tapped.
 */
@Composable
private fun SaveMealNameDialog(
    day: HistoryDay,
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
        title = nomiString("Save day as a meal"),
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeader(
    day: HistoryDay,
    showCopyActions: Boolean,
    onCopyMeal: (MealCategory) -> Unit,
    onCopyDay: () -> Unit,
    onSaveMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    Column(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
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
        }
        // A day can hold four meals, so a single Row of chips would run off the edge on a long
        // translation. FlowRow wraps instead of clipping.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // One chip per meal actually eaten that day. The repository copies a single meal
            // category at a time, so naming the meal in the chip is what makes the action
            // unambiguous - a single day-wide "copy meal" would have to guess.
            if (showCopyActions) {
                day.mealCategories.forEach { mealCategory ->
                    AssistChip(
                        onClick = { onCopyMeal(mealCategory) },
                        label = { Text(nomiFormat("Copy {0}", localizedMealName(mealCategory))) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    )
                }
                AssistChip(
                    onClick = onCopyDay,
                    label = { Text(nomiString("Copy day")) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                )
            }
            AssistChip(
                onClick = onSaveMeal,
                label = { Text(nomiString("Save meal")) },
                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
            )
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
