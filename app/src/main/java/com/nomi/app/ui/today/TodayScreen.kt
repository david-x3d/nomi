package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.theme.NomiTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onFoodClick: (Long) -> Unit,
    onAddFood: (AddFoodMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.date == LocalDate.now()) "Today" else "Food log")
                        Text(
                            text = state.date.format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                    .withLocale(nomiLocale()),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add food") },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DateNavigation(
                    date = state.date,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onToday = onToday,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.goalsCardStyle == GoalsCardStyle.RINGS) {
                item {
                    GoalsRingCard(
                        state = state,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                item {
                    CalorieHero(
                        consumed = state.caloriesConsumed,
                        target = state.calorieTarget,
                        fraction = state.calorieFraction,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    MacroSection(
                        protein = state.protein,
                        carbohydrates = state.carbohydrates,
                        fat = state.fat,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.entries.isEmpty()) {
                item {
                    EmptyToday(
                        onAdd = { showAddSheet = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            } else {
                MealCategory.entries.forEach { category ->
                    val entries = state.entriesFor(category)
                    if (entries.isNotEmpty()) {
                        item(key = "header-${category.name}") {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                        items(entries, key = TodayFoodEntry::id) { entry ->
                            FoodRow(entry = entry, onClick = { onFoodClick(entry.id) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showAddSheet) {
        AddFoodSheet(
            onDismiss = { showAddSheet = false },
            onSelect = { method ->
                showAddSheet = false
                onAddFood(method)
            },
        )
    }
}

@Composable
private fun DateNavigation(
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onPreviousDay) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        if (date != LocalDate.now()) {
            AssistChip(onClick = onToday, label = { Text("Back to today") })
        } else {
            Text("A fresh day", style = MaterialTheme.typography.labelLarge)
        }
        FilledTonalIconButton(
            onClick = onNextDay,
            enabled = date < LocalDate.now().plusDays(7),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun CalorieHero(
    consumed: Double,
    target: Double,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val difference = target - consumed
    val neutralProgressColor = if (difference >= 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .semantics {
                            contentDescription = "${consumed.roundToInt()} of ${target.roundToInt()} calories"
                        },
                    color = neutralProgressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedContent(targetState = consumed.roundToInt(), label = "calorie total") { value ->
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "of ${target.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (difference >= 0) {
                        "${difference.roundToInt()} kcal remaining"
                    } else {
                        "${abs(difference).roundToInt()} above target"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MacroSection(
    protein: MacroProgress,
    carbohydrates: MacroProgress,
    fat: MacroProgress,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Macros", style = MaterialTheme.typography.titleLarge)
            MacroRow("Protein", protein, MaterialTheme.colorScheme.primary)
            MacroRow("Carbohydrates", carbohydrates, MaterialTheme.colorScheme.secondary)
            MacroRow("Fat", fat, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun MacroRow(label: String, progress: MacroProgress, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                "${progress.consumedGrams.roundToInt()} / ${progress.targetGrams.roundToInt()} g",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
    }
}

@Composable
private fun EmptyToday(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Icons.Default.RestaurantMenu,
                contentDescription = null,
                modifier = Modifier.padding(18.dp),
            )
        }
        Text("Nothing logged yet.", style = MaterialTheme.typography.titleLarge)
        Text("Tell Nomi what you ate.", style = MaterialTheme.typography.bodyLarge)
        AssistChip(onClick = onAdd, label = { Text("Add your first food") })
    }
}

@Composable
private fun FoodRow(entry: TodayFoodEntry, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                entry.brand?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.amountText)
                    if (entry.isEstimated) Text("Estimated", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        trailingContent = {
            Text(
                text = "${entry.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.labelLarge,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodSheet(
    onDismiss: () -> Unit,
    onSelect: (AddFoodMethod) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Add food",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = "Logging should take seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        AddFoodMethod.entries.forEach { method ->
            ListItem(
                headlineContent = { Text(method.displayName) },
                supportingContent = {
                    Text(
                        when (method) {
                            AddFoodMethod.TYPE -> "Describe your meal in German or English"
                            AddFoodMethod.VOICE -> "Say what you ate"
                            AddFoodMethod.PHOTO -> "Take or choose a meal photo"
                            AddFoodMethod.BARCODE -> "Scan packaged food"
                            AddFoodMethod.LABEL -> "Read a printed nutrition table"
                            AddFoodMethod.RECENT -> "Log something again"
                            AddFoodMethod.FAVORITES -> "Your saved foods"
                            AddFoodMethod.SAVED_MEALS -> "Add a whole saved meal"
                        },
                    )
                },
                leadingContent = { Icon(method.icon(), contentDescription = null) },
                // Same reason as the settings sheet: an opaque ListItem would paint
                // colorScheme.surface over the sheet's own surfaceContainerLow.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(method) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun AddFoodMethod.icon(): ImageVector = when (this) {
    AddFoodMethod.TYPE -> Icons.Default.Keyboard
    AddFoodMethod.VOICE -> Icons.Default.Mic
    AddFoodMethod.PHOTO -> Icons.Default.CameraAlt
    AddFoodMethod.BARCODE -> Icons.Default.QrCodeScanner
    AddFoodMethod.LABEL -> Icons.Default.Article
    AddFoodMethod.RECENT -> Icons.Default.History
    AddFoodMethod.FAVORITES -> Icons.Default.Favorite
    AddFoodMethod.SAVED_MEALS -> Icons.Default.RestaurantMenu
}

@Preview(showBackground = true)
@Composable
private fun TodayPreview() {
    NomiTheme(dynamicColor = false) {
        TodayScreen(
            state = TodayUiState(
                caloriesConsumed = 1_238.0,
                calorieTarget = 1_820.0,
                protein = MacroProgress(96.0, 130.0),
                carbohydrates = MacroProgress(142.0, 195.0),
                fat = MacroProgress(42.0, 56.0),
                entries = listOf(
                    TodayFoodEntry(
                        id = 1,
                        name = "Harry Wholegrain Toast",
                        amountText = "3 slices",
                        calories = 264.0,
                        mealCategory = MealCategory.BREAKFAST,
                        time = LocalTime.of(8, 15),
                    ),
                ),
            ),
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onFoodClick = {},
            onAddFood = {},
        )
    }
}
