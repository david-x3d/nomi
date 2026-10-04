package com.nomi.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.domain.calculator.CalorieBiasAdjuster
import com.nomi.app.ui.components.NomiFieldShape
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiSecretField
import com.nomi.app.ui.components.NomiSecureWindow
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiMessage
import com.nomi.app.ui.localization.nomiString
import kotlin.math.roundToInt

/** One of the two keys a setup can ask for: the provider's own, and Exa's for web search. */
enum class AiKeyField { PRIMARY, SEARCH }

/** The key form on the AI page: what is typed, and what came of checking it. */
data class AiKeyEntryState(
    val input: String = "",
    /** The Exa key, which only the Gemini + Exa setup asks for. */
    val searchInput: String = "",
    val isChecking: Boolean = false,
    /** What the last check said, already worded for the user. */
    val message: String? = null,
    val failed: Boolean = false,
    /** The field a failed check belongs to, when the check could tell. */
    val failedField: AiKeyField? = null,
) {
    val hasInput: Boolean get() = input.isNotBlank() || searchInput.isNotBlank()
}

/**
 * Everything about the AI Nomi runs on, behind one row of Settings.
 *
 * The page answers its questions in the order people have them. Does it work - the status at the
 * top. What do I have to give it - the keys the setup needs, which is two for the recommended
 * Gemini + Exa pair and one for a single provider. Only then the provider and model of each task,
 * folded away until someone wants them, and below that the three settings that only matter to
 * someone tuning or debugging.
 */
@Composable
fun AiSettingsScreen(
    state: SettingsUiState,
    keyEntry: AiKeyEntryState,
    onKeyChanged: (String) -> Unit,
    onSearchKeyChanged: (String) -> Unit,
    onConnectKeys: () -> Unit,
    onProvider: (Int) -> Unit,
    onCalorieEstimateBiasChanged: (CalorieEstimateBias) -> Unit,
    onAiRequestTimeoutDisabledChanged: (Boolean) -> Unit,
    onExaFullPageTextChanged: (Boolean) -> Unit,
    onOpenRouterPreferredProviderChanged: (String) -> Unit,
    onCompareModels: () -> Unit,
    onDebug: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NomiSecureWindow()
    val uriHandler = LocalUriHandler.current
    val setup = state.aiKeySetup
    // With tasks spread over providers there are no keys to ask for in one place, so the list is
    // the page; otherwise it stays folded until asked for.
    val perTask = setup is AiKeySetup.PerTask
    var tasksExpanded by rememberSaveable(perTask) { mutableStateOf(perTask) }

    SettingsSubpageScaffold(
        title = nomiString("AI provider"),
        onBack = onBack,
        modifier = modifier.imePadding(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item(key = "status") {
                AiStatusCard(ready = !state.aiSetupNeeded, setup = setup)
            }

            if (!perTask) {
                item(key = "key") {
                    // On the page rather than in a card: a field is cut from the same tone as a
                    // card, and inside one it stopped looking like somewhere to type.
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val pair = setup as? AiKeySetup.GeminiWithExa
                        val single = (setup as? AiKeySetup.Single)?.provider
                        val provider = single?.provider ?: AiProviderKind.GEMINI
                        val keyStored = pair?.hasGeminiKey ?: (single?.hasPrimaryApiKey == true)
                        val submit = { if (keyEntry.hasInput) onConnectKeys() }
                        StoredKeyField(
                            value = keyEntry.input,
                            onValueChange = onKeyChanged,
                            name = nomiFormat("{0} API key", provider.localizedDisplayName()),
                            stored = keyStored,
                            enabled = !keyEntry.isChecking,
                            isError = keyEntry.failedField == AiKeyField.PRIMARY,
                            imeAction = if (pair != null) ImeAction.Next else ImeAction.Done,
                            onDone = submit,
                        )
                        provider.keyPageUrl()?.let { url ->
                            KeyPageLink(
                                label = nomiFormat("Get a key from {0}", provider.keyPageName()),
                                onClick = { runCatching { uriHandler.openUri(url) } },
                            )
                        }
                        if (pair != null) {
                            StoredKeyField(
                                value = keyEntry.searchInput,
                                onValueChange = onSearchKeyChanged,
                                name = nomiString("Exa API key"),
                                stored = pair.hasExaKey,
                                enabled = !keyEntry.isChecking,
                                isError = keyEntry.failedField == AiKeyField.SEARCH,
                                onDone = submit,
                            )
                            KeyPageLink(
                                label = nomiFormat("Get a key from {0}", "Exa"),
                                onClick = { runCatching { uriHandler.openUri(EXA_KEY_PAGE_URL) } },
                            )
                        }
                        Button(
                            onClick = onConnectKeys,
                            enabled = keyEntry.hasInput && !keyEntry.isChecking,
                            shape = NomiShapes.Action,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                when {
                                    keyEntry.isChecking -> nomiString("Checking…")
                                    pair != null -> nomiString("Check and save keys")
                                    else -> nomiString("Check and save key")
                                },
                            )
                        }
                        keyEntry.message?.let { message ->
                            if (keyEntry.failed) {
                                NomiInlineError(message)
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = NomiFieldShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) {
                                    Text(
                                        nomiMessage(message),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "tasks-header") {
                SettingsExpander(
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    title = nomiString("Provider and model per task"),
                    supporting = nomiString("Research, interpretation, portions, photos and fallback"),
                    expanded = tasksExpanded,
                    onToggle = { tasksExpanded = !tasksExpanded },
                )
            }
            if (tasksExpanded) {
                state.aiProviders.forEachIndexed { index, provider ->
                    item(key = "provider-$index") {
                        SettingsLink(
                            icon = { Icon(Icons.Default.Key, contentDescription = null) },
                            title = provider.purpose.localizedPurpose(),
                            // Nothing depends on Fallback, so one without a key is unused, not
                            // broken, and is not painted as a fault.
                            supporting = when {
                                provider.hasApiKey ->
                                    "${provider.provider.localizedDisplayName()} · ${provider.model}"
                                provider.purpose == "Fallback" ->
                                    "${provider.provider.localizedDisplayName()} · " +
                                        nomiString("Optional")
                                else ->
                                    "${provider.provider.localizedDisplayName()} · " +
                                        nomiString("API key missing")
                            },
                            supportingColor = if (provider.hasApiKey || provider.purpose == "Fallback") {
                                Color.Unspecified
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            onClick = { onProvider(index) },
                            iconColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            item { SectionTitle(nomiString("Estimates")) }
            item(key = "bias") {
                CalorieBiasSetting(
                    bias = state.calorieEstimateBias,
                    onBiasChanged = onCalorieEstimateBiasChanged,
                )
            }

            item { SectionTitle(nomiString("Food research")) }
            if (state.usesExaSearch) {
                item(key = "full-pages") {
                    ToggleSetting(
                        icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                        title = nomiString("Read whole source pages"),
                        supporting = nomiString("Exa gives the AI each page's full text instead of excerpts. Finds nutrition more reliably, but uses more Exa and AI credits."),
                        checked = state.exaFullPageText,
                        onCheckedChange = onExaFullPageTextChanged,
                        iconColor = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            item(key = "compare-models") {
                SettingsLink(
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                    title = nomiString("Compare OpenRouter models"),
                    supporting = nomiString("Look up one meal with up to four models through Exa and pick the best"),
                    onClick = onCompareModels,
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }

            if (state.usesOpenRouter) {
                item { SectionTitle("OpenRouter") }
                item(key = "openrouter-provider") {
                    // Kept locally while typing, so a save echoing back never moves the cursor.
                    var slug by rememberSaveable { mutableStateOf(state.openRouterPreferredProvider) }
                    NomiTextField(
                        value = slug,
                        onValueChange = {
                            slug = it
                            onOpenRouterPreferredProviderChanged(it)
                        },
                        label = nomiString("Preferred OpenRouter provider"),
                        placeholder = "baseten",
                        supportingText = nomiString("Every OpenRouter request asks this provider first, for every task. If it is unavailable, OpenRouter uses the next one. Leave blank to let OpenRouter choose."),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item { SectionTitle(nomiString("Troubleshooting")) }
            item(key = "timeout") {
                ToggleSetting(
                    icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null) },
                    title = nomiString("Never time out"),
                    supporting = nomiString("Wait as long as the provider needs instead of giving up after 45 seconds"),
                    checked = state.aiRequestTimeoutDisabled,
                    onCheckedChange = onAiRequestTimeoutDisabledChanged,
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            item(key = "debug") {
                SettingsLink(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    title = nomiString("AI debug"),
                    supporting = nomiString("Provider, timing, source and validation — never keys"),
                    onClick = onDebug,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Whether logging a meal will work, said before anything is asked for. */
@Composable
private fun AiStatusCard(ready: Boolean, setup: AiKeySetup) {
    val accent = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
        ListItem(
            headlineContent = {
                Text(
                    text = if (ready) nomiString("Ready to look up food") else nomiString("Add an API key to start"),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text = when (setup) {
                        is AiKeySetup.GeminiWithExa -> nomiString("Nomi has no AI credits of its own. Google Gemini reads what you log and Exa finds the nutrition sources, each on a key that is yours.")
                        is AiKeySetup.Single -> nomiFormat(
                            "Nomi has no AI credits of its own. Every lookup runs on your {0} key, and one key covers every task.",
                            setup.provider.provider.localizedDisplayName(),
                        )
                        AiKeySetup.PerTask -> nomiString("Nomi has no AI credits of its own. Each task runs on the provider and key chosen for it below.")
                    },
                )
            },
            leadingContent = {
                SettingsIconTile(accent) {
                    Icon(
                        imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                        contentDescription = null,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * A key field that knows whether a key is already stored: it says so in its label and offers to
 * keep it, so an empty field next to a stored key does not read as a missing one.
 */
@Composable
internal fun StoredKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    stored: Boolean,
    enabled: Boolean,
    isError: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
) {
    NomiSecretField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (stored) nomiFormat("{0} (stored securely)", name) else name,
        placeholder = nomiString("Leave blank to keep existing key").takeIf { stored },
        enabled = enabled,
        isError = isError,
        imeAction = imeAction,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

/** A settings row that folds the rows under it away instead of leading to another page. */
@Composable
private fun SettingsExpander(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = SettingsRowShape,
        color = Color.Transparent,
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(MaterialTheme.colorScheme.secondary, icon) },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = nomiString(if (expanded) "Collapse" else "Expand"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * Five discrete stops on one official Material slider.
 *
 * The setting is a scale with a natural middle, not five unrelated options, so a slider says what
 * a list cannot: that "no bias" is the centre and each step moves the same distance away from it.
 * The example underneath updates as the thumb moves, so the effect is visible before release.
 * The preference is only written on release; dragging must not fire a DataStore write per pixel.
 */
@Composable
private fun CalorieBiasSetting(
    bias: CalorieEstimateBias,
    onBiasChanged: (CalorieEstimateBias) -> Unit,
) {
    val entries = CalorieEstimateBias.entries
    var position by remember(bias) { mutableFloatStateOf(entries.indexOf(bias).toFloat()) }
    fun entryAt(value: Float) = entries[value.roundToInt().coerceIn(0, entries.lastIndex)]
    val selected = entryAt(position)
    SettingsCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconTile(MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Straighten, contentDescription = null)
                }
                Column {
                    Text(
                        nomiString("Calorie estimate bias"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = selected.localizedDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Slider(
                value = position,
                onValueChange = { position = it },
                // Read the stop at release rather than the one this composition captured. A tap on
                // the track reports its value and finishes inside the same frame, before any
                // recomposition, so the captured stop is still the previous one and would be
                // written back as if the tap had never happened.
                onValueChangeFinished = { onBiasChanged(entryAt(position)) },
                valueRange = 0f..entries.lastIndex.toFloat(),
                steps = entries.size - 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = nomiString("Lower"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = nomiString("Higher"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = selected.localizedSupportingText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Says what the setting does to a real number rather than naming it again, because "Overestimate"
 * on its own does not tell anyone how much.
 */
@Composable
private fun CalorieEstimateBias.localizedSupportingText(): String {
    val example = CalorieBiasAdjuster.scaleFor(uncertaintyPercent = 16.7, bias = this) * 600.0
    val rounded = example.roundToInt()
    return when (this) {
        CalorieEstimateBias.NONE -> nomiString("Estimates are logged as given. A 500-700 kcal meal counts as 600.")
        else -> nomiFormat(
            "{0} - a 500-700 kcal meal counts as {1}.",
            localizedDisplayName(),
            rounded,
        )
    }
}

@Composable
private fun CalorieEstimateBias.localizedDisplayName(): String = when (this) {
    CalorieEstimateBias.STRONGLY_UNDERESTIMATE ->
        nomiString("Underestimate more")
    CalorieEstimateBias.UNDERESTIMATE -> nomiString("Underestimate")
    CalorieEstimateBias.NONE -> nomiString("No bias")
    CalorieEstimateBias.OVERESTIMATE -> nomiString("Overestimate")
    CalorieEstimateBias.STRONGLY_OVERESTIMATE ->
        nomiString("Overestimate more")
}
