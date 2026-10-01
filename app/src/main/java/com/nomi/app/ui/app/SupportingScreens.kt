package com.nomi.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.domain.UnitFormatter
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.settings.HealthConnectUiState
import com.nomi.app.ui.settings.SectionTitle
import com.nomi.app.ui.settings.SettingsCard
import com.nomi.app.ui.settings.SettingsIconTile
import com.nomi.app.ui.settings.SettingsSubpageScaffold
import com.nomi.app.ui.settings.ToggleSetting
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

/**
 * Health Connect, drawn with the rows and cards the rest of Settings uses.
 *
 * The first card says what state the connection is in, because that decides what the page is for:
 * something to switch on, something to finish, or something to check on. The one action that
 * moves it forward is the filled button; syncing again is the quieter one beside it.
 */
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
    val canSyncGrantedCategories =
        health.status == HealthConnectPermissionStatus.CONNECTED ||
            health.status == HealthConnectPermissionStatus.PARTIAL
    val canConnect =
        health.status == HealthConnectPermissionStatus.DISCONNECTED ||
            health.status == HealthConnectPermissionStatus.PARTIAL
    SettingsSubpageScaffold(
        title = "Health Connect",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "status") {
                SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
                    ListItem(
                        headlineContent = {
                            Text(
                                nomiString("Optional health sync"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                when (health.status) {
                                    HealthConnectPermissionStatus.UNAVAILABLE -> nomiString("Health Connect isn't available on this device. Nomi works fully without it.")
                                    HealthConnectPermissionStatus.UPDATE_REQUIRED -> nomiString("Health Connect must be installed or updated before Nomi can connect.")
                                    HealthConnectPermissionStatus.DISCONNECTED -> nomiString("Nothing is shared until you approve the required categories.")
                                    HealthConnectPermissionStatus.PARTIAL -> nomiString("Allowed categories keep syncing. Approve the missing permissions to enable everything.")
                                    HealthConnectPermissionStatus.CONNECTED -> nomiString("Connected. You can change access at any time in Health Connect.")
                                },
                            )
                        },
                        leadingContent = {
                            SettingsIconTile(
                                when (health.status) {
                                    HealthConnectPermissionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                    HealthConnectPermissionStatus.PARTIAL -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                            ) {
                                Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item(key = "what") {
                PageNote(
                    nomiString("Nomi reads today's steps and active calories, imports your accessible weight history, and sends pending weights plus your complete food log to Health Connect."),
                )
            }

            if (canSyncGrantedCategories) {
                item { SectionTitle(nomiString("Today's activity")) }
                item(key = "activity") {
                    SettingsCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
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
                                    "${it.roundToInt().formatted(locale)} kcal",
                                )
                            }
                            NutritionLine(
                                nomiString("Food entries shared"),
                                health.sharedNutritionEntryCount?.toString()
                                    ?: nomiString("Not synced yet"),
                            )
                        }
                    }
                }
                item(key = "estimate-note") {
                    PageNote(
                        nomiString(
                            "Step calories are estimated locally from your current weight and height " +
                                "when available. They are not added to Health Connect active calories.",
                        ),
                    )
                }
            }

            if (canConnect || canSyncGrantedCategories) {
                item(key = "actions") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (canConnect) {
                            Button(
                                onClick = onConnect,
                                enabled = available && !health.isSyncing,
                                shape = NomiShapes.Action,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                Text(
                                    if (health.status == HealthConnectPermissionStatus.PARTIAL) {
                                        nomiString("Complete permissions")
                                    } else {
                                        nomiString("Choose permissions")
                                    },
                                )
                            }
                        }
                        if (canSyncGrantedCategories) {
                            FilledTonalButton(
                                onClick = onSyncNow,
                                enabled = !health.isSyncing,
                                shape = NomiShapes.Action,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                if (health.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(nomiString("Syncing health data..."))
                                } else {
                                    Text(nomiString("Sync now"))
                                }
                            }
                        }
                    }
                }
            }

            health.message?.let { message ->
                item(key = "message") { PageNote(nomiMessage(message)) }
            }
            item(key = "privacy") {
                PageNote(nomiString("Health data is used only for your local Nomi experience and is never sold."))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A sentence that belongs to the page rather than to a card on it. */
@Composable
private fun PageNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/**
 * The AI debug log.
 *
 * Recording is a switch, like every other on-or-off in Settings. It used to be a button whose
 * label flipped between "Enable" and "Disable", which made the current state something to work
 * out from the word that was not shown.
 */
@Composable
fun DeveloperScreen(
    debugEnabled: Boolean,
    events: List<AiDebugEventEntity>,
    onBack: () -> Unit,
    onDebugEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubpageScaffold(
        title = nomiString("AI debug"),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "record") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    ToggleSetting(
                        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        title = nomiString("Record debug events"),
                        supporting = nomiString("Events contain provider, model, timing, cache and validation status—never API keys or request headers."),
                        checked = debugEnabled,
                        onCheckedChange = onDebugEnabledChanged,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item { SectionTitle(nomiString("Recent events")) }
            if (events.isEmpty()) {
                item(key = "empty") { PageNote(nomiString("No debug events recorded.")) }
            } else {
                items(events, key = { it.id }) { event ->
                    SettingsCard {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${event.pipeline} · ${event.validationStatus}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                            supportingContent = {
                                Text("${event.providerId} / ${event.model} · ${event.durationMillis} ms")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
