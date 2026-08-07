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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.nomi.app.ui.localization.nomiString
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(nomiString("Log weight", "Gewicht eintragen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { character -> character.isDigit() || character in ".," } },
                    label = { Text(nomiString("Weight in kg", "Gewicht in kg")) },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text(nomiString("Note (optional)", "Notiz (optional)")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    nomiString("Your trend matters more than any single weigh-in.", "Dein Verlauf ist wichtiger als eine einzelne Messung."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onSave(it, note); onDismiss() } },
                enabled = parsed != null && parsed in 20.0..500.0,
            ) { Text(nomiString("Save", "Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nomiString("Cancel", "Abbrechen")) } },
    )
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
                title = { Text(nomiString("Food details", "Lebensmitteldetails")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back", "Zurück"))
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(nomiString("This entry is no longer available.", "Dieser Eintrag ist nicht mehr verfügbar."), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onBack) { Text(nomiString("Go back", "Zurück")) }
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
                        if (entry.isEstimated) Text(nomiString("Estimated nutrition", "Geschätzte Nährwerte"), color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(nomiString("Nutrition", "Nährwerte"), style = MaterialTheme.typography.titleLarge)
                            NutritionLine(nomiString("Calories", "Kalorien"), "${entry.calories.roundToInt()} kcal")
                            NutritionLine(nomiString("Protein", "Protein"), "${entry.proteinGrams.roundToInt()} g")
                            NutritionLine(nomiString("Carbohydrates", "Kohlenhydrate"), "${entry.carbohydrateGrams.roundToInt()} g")
                            NutritionLine(nomiString("Fat", "Fett"), "${entry.fatGrams.roundToInt()} g")
                            entry.sourceName?.let { source ->
                                NutritionLine(nomiString("Source", "Quelle"), source)
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
                            Text(nomiString("Duplicate", "Duplizieren"))
                        }
                        FilledTonalButton(
                            onClick = { onFavorite(entry.id) },
                            enabled = entry.foodId != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Text(nomiString("Favorite", "Favorit"))
                        }
                    }
                }
                item {
                    TextButton(onClick = { onDelete(entry.id); onBack() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(nomiString("Delete", "Löschen"))
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
    MealCategory.BREAKFAST -> nomiString("Breakfast", "Frühstück")
    MealCategory.LUNCH -> nomiString("Lunch", "Mittagessen")
    MealCategory.DINNER -> nomiString("Dinner", "Abendessen")
    MealCategory.SNACKS -> nomiString("Snacks", "Snacks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(
    available: Boolean,
    connected: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back", "Zurück"))
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
            Text(nomiString("Optional health sync", "Optionale Gesundheitssynchronisierung"), style = MaterialTheme.typography.headlineMedium)
            Text(
                nomiString("Nomi can read and write weight and read steps and active calories. Your nutrition log stays local to Nomi.", "Nomi kann Gewicht lesen und schreiben sowie Schritte und aktive Kalorien lesen. Dein Ernährungstagebuch bleibt lokal in Nomi."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    !available -> nomiString("Health Connect isn't available on this device. Nomi works fully without it.", "Health Connect ist auf diesem Gerät nicht verfügbar. Nomi funktioniert auch ohne Health Connect vollständig.")
                    connected -> nomiString("Connected. You can change access at any time in Health Connect.", "Verbunden. Du kannst den Zugriff jederzeit in Health Connect ändern.")
                    else -> nomiString("Nothing is shared until you approve the requested categories.", "Es werden keine Daten geteilt, bevor du die angeforderten Kategorien freigibst.")
                },
            )
            Button(onClick = onConnect, enabled = available && !connected) {
                Text(if (connected) nomiString("Connected", "Verbunden") else nomiString("Choose permissions", "Berechtigungen auswählen"))
            }
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
                title = { Text(nomiString("AI debug", "KI-Debug")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back", "Zurück"))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(nomiString("Privacy-safe diagnostics", "Datenschutzfreundliche Diagnose"), style = MaterialTheme.typography.headlineSmall)
                    Text(nomiString("Events contain provider, model, timing, cache and validation status—never API keys or request headers.", "Ereignisse enthalten Anbieter, Modell, Dauer, Cache- und Prüfstatus – niemals API-Schlüssel oder Anfrage-Header."))
                    Button(onClick = { onDebugEnabledChanged(!debugEnabled) }) {
                        Text(if (debugEnabled) nomiString("Disable debug events", "Debug-Ereignisse deaktivieren") else nomiString("Enable debug events", "Debug-Ereignisse aktivieren"))
                    }
                }
            }
            if (events.isEmpty()) {
                item { Text(nomiString("No debug events recorded.", "Noch keine Debug-Ereignisse aufgezeichnet."), modifier = Modifier.padding(20.dp)) }
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
