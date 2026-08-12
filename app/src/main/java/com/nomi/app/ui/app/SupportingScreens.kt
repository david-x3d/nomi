package com.nomi.app.ui.app

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.settings.HealthConnectUiState
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import kotlin.math.roundToInt

@Composable
fun WeightEntryDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = weight.replace(',', '.').toDoubleOrNull()
    NomiDialog(
        onDismissRequest = onDismiss,
        title = nomiString("Log weight"),
        icon = Icons.Default.MonitorWeight,
        subtitle = nomiString("Your trend matters more than any single weigh-in."),
        confirmLabel = nomiString("Save"),
        onConfirm = { parsed?.let { onSave(it, note); onDismiss() } },
        confirmEnabled = parsed != null && parsed in 20.0..500.0,
        dismissLabel = nomiString("Cancel"),
    ) {
        NomiTextField(
            value = weight,
            onValueChange = { weight = it.filter { character -> character.isDigit() || character in ".," } },
            label = nomiString("Weight in kg"),
            suffix = "kg",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        NomiTextField(
            value = note,
            onValueChange = { note = it.take(120) },
            label = nomiString("Note (optional)"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryDetailScreen(
    entry: TodayFoodEntry?,
    onBack: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(nomiString("Food details")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back"))
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(nomiString("This entry is no longer available."), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onBack) { Text(nomiString("Go back")) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null)
                        Text(entry.name, style = MaterialTheme.typography.headlineLarge)
                        entry.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("${entry.amountText} · ${entry.mealCategory.localizedDisplayName()} · ${entry.time}")
                        if (entry.isEstimated) Text(nomiString("Estimated nutrition"), color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = nomiCardShape(),
                        elevation = nomiCardElevation(),
                        border = nomiCardBorder(),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(nomiString("Nutrition"), style = MaterialTheme.typography.titleLarge)
                            NutritionLine(nomiString("Calories"), "${entry.calories.roundToInt()} kcal")
                            NutritionLine(nomiString("Protein"), "${entry.proteinGrams.roundToInt()} g")
                            NutritionLine(nomiString("Carbohydrates"), "${entry.carbohydrateGrams.roundToInt()} g")
                            NutritionLine(nomiString("Fat"), "${entry.fatGrams.roundToInt()} g")
                            entry.sourceName?.let { source ->
                                NutritionLine(nomiString("Source"), source)
                                entry.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onDuplicate(entry.id); onBack() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text(nomiString("Duplicate"))
                        }
                        FilledTonalButton(
                            onClick = { onFavorite(entry.id) },
                            enabled = entry.foodId != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Text(nomiString("Favorite"))
                        }
                    }
                }
                item {
                    TextButton(onClick = { onDelete(entry.id); onBack() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(nomiString("Delete"))
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MealCategory.localizedDisplayName(): String = when (this) {
    MealCategory.BREAKFAST -> nomiString("Breakfast")
    MealCategory.LUNCH -> nomiString("Lunch")
    MealCategory.DINNER -> nomiString("Dinner")
    MealCategory.SNACKS -> nomiString("Snacks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(
    available: Boolean,
    connected: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    health: HealthConnectUiState = HealthConnectUiState(
        status = when {
            connected -> HealthConnectPermissionStatus.CONNECTED
            available -> HealthConnectPermissionStatus.DISCONNECTED
            else -> HealthConnectPermissionStatus.UNAVAILABLE
        },
    ),
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(Icons.Default.HealthAndSafety, contentDescription = null)
            Text(
                nomiString("Optional health sync"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                nomiString("Nomi reads weight from the last 30 days, reads today's steps and active calories, and writes only weights you enter in Nomi. Your nutrition log stays local."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (health.status) {
                    HealthConnectPermissionStatus.UNAVAILABLE -> nomiString("Health Connect isn't available on this device. Nomi works fully without it.")
                    HealthConnectPermissionStatus.UPDATE_REQUIRED -> nomiString("Health Connect must be installed or updated before Nomi can connect.")
                    HealthConnectPermissionStatus.DISCONNECTED -> nomiString("Nothing is shared until you approve all requested categories.")
                    HealthConnectPermissionStatus.PARTIAL -> nomiString("Some permissions are missing. Approve all four categories to finish connecting.")
                    HealthConnectPermissionStatus.CONNECTED -> nomiString("Connected. You can change access at any time in Health Connect.")
                },
            )

            if (health.isSyncing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(nomiString("Syncing health data..."))
                }
            }

            if (health.status == HealthConnectPermissionStatus.CONNECTED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = nomiCardShape(),
                    elevation = nomiCardElevation(),
                    border = nomiCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(nomiString("Today's activity"), style = MaterialTheme.typography.titleLarge)
                        NutritionLine(
                            nomiString("Steps"),
                            health.todaySteps?.toString() ?: nomiString("Not synced yet"),
                        )
                        NutritionLine(
                            nomiString("Active calories"),
                            health.todayActiveCaloriesKcal?.let { "${it.roundToInt()} kcal" }
                                ?: nomiString("Not synced yet"),
                        )
                    }
                }
                Button(onClick = onSyncNow, enabled = !health.isSyncing) {
                    Text(nomiString("Sync now"))
                }
            } else if (
                health.status == HealthConnectPermissionStatus.DISCONNECTED ||
                health.status == HealthConnectPermissionStatus.PARTIAL
            ) {
                Button(onClick = onConnect, enabled = available && !health.isSyncing) {
                    Text(
                        if (health.status == HealthConnectPermissionStatus.PARTIAL) {
                            nomiString("Complete permissions")
                        } else {
                            nomiString("Choose permissions")
                        },
                    )
                }
            }

            health.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                nomiString("Health data is used only for your local Nomi experience and is never sold."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    debugEnabled: Boolean,
    events: List<AiDebugEventEntity>,
    onBack: () -> Unit,
    onDebugEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(nomiString("AI debug")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back"))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(nomiString("Privacy-safe diagnostics"), style = MaterialTheme.typography.headlineSmall)
                    Text(nomiString("Events contain provider, model, timing, cache and validation status—never API keys or request headers."))
                    Button(onClick = { onDebugEnabledChanged(!debugEnabled) }) {
                        Text(if (debugEnabled) nomiString("Disable debug events") else nomiString("Enable debug events"))
                    }
                }
            }
            if (events.isEmpty()) {
                item { Text(nomiString("No debug events recorded."), modifier = Modifier.padding(20.dp)) }
            } else {
                items(events, key = { it.id }) { event ->
                    ListItem(
                        headlineContent = { Text("${event.pipeline} · ${event.validationStatus}") },
                        supportingContent = { Text("${event.providerId} / ${event.model} · ${event.durationMillis} ms") },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
