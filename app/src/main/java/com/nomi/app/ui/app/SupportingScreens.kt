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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.nomi.app.domain.UnitFormatter
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.settings.HealthConnectUiState
import com.nomi.app.ui.today.estimatedStepCaloriesText
import com.nomi.app.ui.today.formatted
import kotlin.math.roundToInt
import com.nomi.app.ui.localization.nomiMessage

@Composable
fun WeightEntryDialog(
    metric: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val locale = nomiLocale()
    // Parsed through the shared unit formatter, so the number a person types in pounds is stored
    // as kilograms. It used to be a bare parse of a field labelled "kg", which meant a real
    // 180 lb weigh-in was persisted as 180 kg and then fed the calorie calculator.
    val parsed = UnitFormatter.parseWeightToKilograms(weight, metric)
    NomiDialog(
        onDismissRequest = onDismiss,
        title = nomiString("Log weight"),
        icon = Icons.Default.MonitorWeight,
        subtitle = nomiString("Your trend matters more than any single weigh-in."),
        confirmLabel = nomiString("Save"),
        onConfirm = { parsed?.let { onSave(it, note); onDismiss() } },
        confirmEnabled = parsed != null && UnitFormatter.isPlausibleWeightKilograms(parsed),
        dismissLabel = nomiString("Cancel"),
    ) {
        NomiTextField(
            value = weight,
            onValueChange = { weight = it.filter { character -> character.isDigit() || character in ".," } },
            label = if (metric) nomiString("Weight in kg") else nomiString("Weight in lb"),
            suffix = UnitFormatter.weightUnit(metric),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        NomiTextField(
            value = note,
            onValueChange = { note = it.take(120) },
            label = nomiString("Note (optional)"),
        )
    }
}

@Composable
private fun NutritionLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
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
    val locale = nomiLocale()
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
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(Icons.Default.HealthAndSafety, contentDescription = null)
            Text(
                nomiString("Optional health sync"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                nomiString("Nomi reads today's steps and active calories, imports your accessible weight history, and sends pending weights plus your complete food log to Health Connect."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (health.status) {
                    HealthConnectPermissionStatus.UNAVAILABLE -> nomiString("Health Connect isn't available on this device. Nomi works fully without it.")
                    HealthConnectPermissionStatus.UPDATE_REQUIRED -> nomiString("Health Connect must be installed or updated before Nomi can connect.")
                    HealthConnectPermissionStatus.DISCONNECTED -> nomiString("Nothing is shared until you approve the required categories.")
                    HealthConnectPermissionStatus.PARTIAL -> nomiString("Allowed categories keep syncing. Approve the missing permissions to enable everything.")
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

            val canSyncGrantedCategories =
                health.status == HealthConnectPermissionStatus.CONNECTED ||
                    health.status == HealthConnectPermissionStatus.PARTIAL
            if (canSyncGrantedCategories) {
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
                            health.todaySteps?.formatted(locale) ?: nomiString("Not synced yet"),
                        )
                        NutritionLine(
                            nomiString("Estimated from steps"),
                            health.estimatedStepCaloriesKcal?.let {
                                estimatedStepCaloriesText(it, locale)
                            } ?: nomiString("Not synced yet"),
                        )
                        health.todayActiveCaloriesKcal?.let {
                            NutritionLine(
                                nomiString("Active calories"),
                                "${it.roundToInt().formatted(locale)} kcal"
                            )
                        }
                        NutritionLine(
                            nomiString("Food entries shared"),
                            health.sharedNutritionEntryCount?.toString()
                                ?: nomiString("Not synced yet"),
                        )
                    }
                }
                Text(
                    nomiString(
                        "Step calories are estimated locally from your current weight and height " +
                            "when available. They are not added to Health Connect active calories.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSyncNow, enabled = !health.isSyncing) {
                    Text(nomiString("Sync now"))
                }
            }

            if (
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
                    nomiMessage(message),
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
