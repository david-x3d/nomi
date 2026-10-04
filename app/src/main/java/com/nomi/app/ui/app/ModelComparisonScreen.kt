package com.nomi.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiSelectionRow
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.settings.SectionTitle
import com.nomi.app.ui.settings.SettingsCard
import com.nomi.app.ui.settings.SettingsSubpageScaffold
import kotlin.math.roundToInt

/**
 * Runs one meal through several OpenRouter models side by side, so a reader can be picked on
 * what it actually returns rather than on a benchmark score.
 */
@Composable
internal fun ModelComparisonScreen(
    state: ModelComparisonUiState,
    fullPageText: Boolean,
    researchModel: String?,
    onInputChanged: (String) -> Unit,
    onToggleModel: (String) -> Unit,
    onCustomModelChanged: (String) -> Unit,
    onAddCustomModel: () -> Unit,
    onCompare: () -> Unit,
    onUseModel: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubpageScaffold(
        title = nomiString("Compare OpenRouter models"),
        onBack = onBack,
        modifier = modifier.imePadding(),
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "intro") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        nomiString("Every model reads the same Exa search for the meal you type. Each model is one request on your OpenRouter key, under the same price ceiling as food research."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (fullPageText) {
                            nomiString("Read whole source pages is on: the models get each page's full text.")
                        } else {
                            nomiString("Read whole source pages is off: the models get Exa's excerpts.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NomiTextField(
                        value = state.input,
                        onValueChange = onInputChanged,
                        label = nomiString("Meal to look up"),
                        placeholder = nomiString("e.g. Hans im Glück Classic Burger"),
                        enabled = !state.isRunning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "models-title") {
                SectionTitle(nomiFormat("Models (up to {0})", MAX_COMPARED_MODELS))
            }
            items(state.models, key = { "model-$it" }) { model ->
                val selected = model in state.selected
                NomiSelectionRow(
                    title = model,
                    selected = selected,
                    onClick = { onToggleModel(model) },
                    enabled = !state.isRunning && (selected || state.canSelectMore()),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            item(key = "custom") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NomiTextField(
                        value = state.customModelInput,
                        onValueChange = onCustomModelChanged,
                        label = nomiString("Other model ID"),
                        placeholder = "vendor/model",
                        enabled = !state.isRunning,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = onAddCustomModel,
                        enabled = state.customModelInput.isNotBlank() && !state.isRunning,
                        shape = NomiShapes.Action,
                    ) { Text(nomiString("Add")) }
                }
            }
            item(key = "compare") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onCompare,
                        enabled = state.canRun,
                        shape = NomiShapes.Action,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            if (state.isRunning) {
                                nomiString("Comparing…")
                            } else {
                                nomiFormat("Compare {0} models", state.selected.size)
                            },
                        )
                    }
                    state.errorMessage?.let { NomiInlineError(it) }
                }
            }

            if (state.cards.isNotEmpty()) {
                item(key = "results-title") { SectionTitle(nomiString("Results")) }
                items(state.cards, key = { "card-${it.model}" }) { card ->
                    ComparisonCard(
                        card = card,
                        inUse = card.model == researchModel,
                        onUse = { onUseModel(card.model) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ComparisonCard(card: ModelComparisonCard, inUse: Boolean, onUse: () -> Unit) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.model,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (card.status == ModelComparisonStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    card.durationMillis?.let { millis ->
                        Text(
                            "%.1f s".format(millis / 1000.0),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (card.status) {
                ModelComparisonStatus.RUNNING -> Text(
                    nomiString("Researching…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelComparisonStatus.FAILED -> Text(
                    card.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                ModelComparisonStatus.DONE -> card.analysis?.let { analysis ->
                    AnalysisSummary(analysis)
                }
            }
            if (card.status != ModelComparisonStatus.RUNNING) {
                if (inUse) {
                    Text(
                        nomiString("Used for food research"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    FilledTonalButton(onClick = onUse, shape = NomiShapes.Action) {
                        Text(nomiString("Use for food research"))
                    }
                }
            }
        }
    }
}

/** The meal's totals, whether every item was verified, and where the numbers came from. */
@Composable
private fun AnalysisSummary(analysis: FoodAnalysis) {
    val items = analysis.items
    Text(
        nomiFormat(
            "{0} kcal · {1} g protein · {2} g carbs · {3} g fat",
            items.sumOf { it.calories }.roundToInt(),
            items.sumOf { it.proteinGrams }.oneDecimal(),
            items.sumOf { it.carbohydrateGrams }.oneDecimal(),
            items.sumOf { it.fatGrams }.oneDecimal(),
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    val verified = items.isNotEmpty() &&
        items.all { it.verificationStatus == NutritionVerificationStatus.VERIFIED && !it.isEstimate }
    Text(
        if (verified) nomiString("Verified from a source") else nomiString("Estimate"),
        style = MaterialTheme.typography.labelLarge,
        color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
    )
    items.forEach { item ->
        val source = item.sourceDomain ?: item.sourceUrl?.let(::hostOf)
        Text(
            listOfNotNull(
                "${item.quantity.oneDecimal()} ${item.unit} ${item.name}",
                source,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Double.oneDecimal(): String =
    if (this == Math.floor(this)) toLong().toString() else "%.1f".format(this)

private fun hostOf(url: String): String? =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()
