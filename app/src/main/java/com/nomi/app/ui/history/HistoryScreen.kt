package com.nomi.app.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiTextField
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
    onQueryChanged: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onFoodClick: (Long) -> Unit,
    onCopyMeal: (HistoryDay) -> Unit,
    onCopyDay: (HistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    onSaveMeal: (HistoryDay) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
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
                title = { Text(nomiString("History", "Verlauf")) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = nomiString("Choose date", "Datum auswählen"),
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
                    label = nomiString("Search history", "Verlauf durchsuchen"),
                    placeholder = nomiString("Toast, McDonald's, Banana…", "Toastbrot, McDonald's, Banane…"),
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
                            nomiString("Your logged days will appear here.", "Deine protokollierten Tage erscheinen hier."),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (state.query.isBlank()) {
                                nomiString("Start by logging a meal on Today.", "Trage zuerst unter Heute eine Mahlzeit ein.")
                            } else {
                                nomiString(
                                    "No foods match “${state.query}”.",
                                    "Keine Lebensmittel passen zu „${state.query}“.",
                                )
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
                            onCopyMeal = { onCopyMeal(day) },
                            onCopyDay = { onCopyDay(day) },
                            onSaveMeal = { onSaveMeal(day) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(day.entries, key = { "${day.date}-${it.id}" }) { entry ->
                        val categoryLabel = when (entry.mealCategory) {
                            MealCategory.BREAKFAST -> nomiString("Breakfast", "Frühstück")
                            MealCategory.LUNCH -> nomiString("Lunch", "Mittagessen")
                            MealCategory.DINNER -> nomiString("Dinner", "Abendessen")
                            MealCategory.SNACKS -> nomiString("Snacks", "Snacks")
                        }
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
            confirmLabel = nomiString("Select", "Auswählen"),
            dismissLabel = nomiString("Cancel", "Abbrechen"),
            showModeToggle = false,
        )
    }
}

@Composable
private fun DayHeader(
    day: HistoryDay,
    onCopyMeal: () -> Unit,
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
                    nomiString(
                        "${day.calories.roundToInt()} / ${day.calorieTarget.roundToInt()} kcal · " +
                            "P ${day.proteinGrams.roundToInt()} · C ${day.carbohydrateGrams.roundToInt()} · F ${day.fatGrams.roundToInt()}",
                        "${day.calories.roundToInt()} / ${day.calorieTarget.roundToInt()} kcal · " +
                            "E ${day.proteinGrams.roundToInt()} · K ${day.carbohydrateGrams.roundToInt()} · F ${day.fatGrams.roundToInt()}",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onCopyMeal,
                label = { Text(nomiString("Copy meal", "Mahlzeit kopieren")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            )
            AssistChip(
                onClick = onCopyDay,
                label = { Text(nomiString("Copy day", "Tag kopieren")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            )
            AssistChip(
                onClick = onSaveMeal,
                label = { Text(nomiString("Save meal", "Mahlzeit speichern")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            )
        }
    }
}
