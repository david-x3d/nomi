package com.nomi.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.usage.AiCostEstimator
import com.nomi.app.ai.usage.AiModelUsageRow
import com.nomi.app.ai.usage.AiTaskUsageRow
import com.nomi.app.ai.usage.AiUsageRange
import com.nomi.app.ai.usage.AiUsageSummary
import com.nomi.app.data.preferences.AiUsagePreferences
import com.nomi.app.ui.components.ExpressiveBadgeStyle
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiExpressiveBadge
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiUsageScreen(
    range: AiUsageRange,
    summary: AiUsageSummary,
    usagePreferences: AiUsagePreferences = AiUsagePreferences(),
    allTimeExaCostUsd: Double = 0.0,
    onRangeChanged: (AiUsageRange) -> Unit,
    onExaFreeTierEnabledChanged: (Boolean) -> Unit = {},
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    SettingsSubpageScaffold(
        title = nomiString("Usage & cost"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
            ),
        ) {
            item(key = "range-chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AiUsageRange.entries, key = AiUsageRange::name) { option ->
                        FilterChip(
                            selected = option == range,
                            onClick = { onRangeChanged(option) },
                            label = { Text(option.localizedLabel()) },
                        )
                    }
                }
            }

            settingsGroup(
                key = "overview",
                rows = listOf(
                    {
                        AnimatedContent(
                            targetState = range to summary,
                            label = "ai-usage-hero",
                        ) { (_, current) ->
                            UsageHeroCard(
                                summary = current,
                                usagePreferences = usagePreferences,
                                allTimeExaCostUsd = allTimeExaCostUsd,
                            )
                        }
                    },
                    {
                        ExaFreeTierCard(
                            usagePreferences = usagePreferences,
                            allTimeExaCostUsd = allTimeExaCostUsd,
                            onEnabledChanged = onExaFreeTierEnabledChanged,
                        )
                    },
                ),
            )

            if (summary.totalEvents == 0) {
                item(key = "empty-usage") {
                    Spacer(Modifier.height(12.dp))
                    UsageEmptyCard()
                }
            } else {
                val tokenRows = buildList<@Composable () -> Unit> {
                    add {
                        UsageMetricRow(
                            icon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = nomiString("Input tokens"),
                            supporting = nomiString("Prompts and web excerpts sent to models"),
                            value = AiCostEstimator.formatCompactTokens(summary.promptTokens),
                        )
                    }
                    add {
                        UsageMetricRow(
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                            iconColor = MaterialTheme.colorScheme.secondary,
                            title = nomiString("Output tokens"),
                            supporting = nomiString("Structured nutrition answers returned"),
                            value = AiCostEstimator.formatCompactTokens(summary.completionTokens),
                        )
                    }
                    if (summary.thoughtsTokens > 0L) {
                        add {
                            UsageMetricRow(
                                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                title = nomiString("Reasoning tokens"),
                                supporting = nomiString("Model thinking tokens billed on reasoning calls"),
                                value = AiCostEstimator.formatCompactTokens(summary.thoughtsTokens),
                            )
                        }
                    }
                    if (summary.exaSearches > 0) {
                        add {
                            UsageMetricRow(
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                title = nomiString("Exa web searches"),
                                supporting = nomiString("Focused nutrition source lookups"),
                                value = summary.exaSearches.toString(),
                            )
                        }
                    }
                }

                settingsGroup(
                    title = { nomiString("Tokens & search") },
                    key = "tokens",
                    rows = tokenRows,
                )

                if (summary.byModel.isNotEmpty()) {
                    settingsGroup(
                        title = { nomiString("By provider & model") },
                        key = "models",
                        rows = summary.byModel.map { row ->
                            { ModelUsageTile(row) }
                        },
                    )
                }

                if (summary.byTask.isNotEmpty()) {
                    settingsGroup(
                        title = { nomiString("By task") },
                        key = "tasks",
                        rows = summary.byTask.map { row ->
                            { TaskUsageTile(row) }
                        },
                    )
                }

                item(key = "clear-action") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { confirmClear = true },
                            shape = NomiShapes.Action,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(nomiString("Clear usage history"))
                        }
                        Text(
                            text = nomiString(
                                "Costs use provider-reported billing when available (such as OpenRouter and Exa) and standard published token rates otherwise. Stored locally on this device only.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(nomiString("Clear usage history?")) },
            text = {
                Text(
                    nomiString("This resets local token and cost counters. Your food diary and API keys are not affected."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    },
                ) {
                    Text(nomiString("Clear"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(nomiString("Cancel"))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UsageHeroCard(
    summary: AiUsageSummary,
    usagePreferences: AiUsagePreferences,
    allTimeExaCostUsd: Double,
    modifier: Modifier = Modifier,
) {
    val freeRate by animateFloatAsState(
        targetValue = summary.freeResolutionRate,
        animationSpec = nomiProgressMotionSpec(),
        label = "free-resolution-rate",
    )
    val freePercent = (summary.freeResolutionRate * 100f).roundToInt()
    val outOfPocketUsd = summary.outOfPocketCostUsd(
        exaFreeTierEnabled = usagePreferences.exaFreeTierEnabled,
        exaFreeCreditUsd = usagePreferences.exaFreeCreditUsd,
        allTimeExaCostUsd = allTimeExaCostUsd,
    )
    val exaCoveredUsd = summary.exaCoveredInRangeUsd(
        exaFreeTierEnabled = usagePreferences.exaFreeTierEnabled,
        exaFreeCreditUsd = usagePreferences.exaFreeCreditUsd,
        allTimeExaCostUsd = allTimeExaCostUsd,
    )

    SettingsCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (exaCoveredUsd > 0.0) {
                        nomiString("Estimated out-of-pocket")
                    } else {
                        nomiString("Estimated AI spend")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = AiCostEstimator.formatUsd(outOfPocketUsd),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                val badgeText = when {
                    summary.totalEvents == 0 -> nomiString("No requests yet")
                    exaCoveredUsd > 0.0 -> nomiFormat(
                        "{0} covered by Exa free tier",
                        AiCostEstimator.formatUsd(exaCoveredUsd),
                    )
                    summary.hasReportedCosts && !summary.hasEstimatedCosts -> nomiString("Provider reported")
                    summary.hasReportedCosts -> nomiString("Reported + estimated")
                    else -> nomiString("Estimated from token rates")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            SavingsRing(
                progress = freeRate,
                percent = freePercent,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = nomiString("On-device & cache efficiency"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = nomiFormat("{0}% free", freePercent),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            LinearWavyProgressIndicator(
                progress = { freeRate },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroStatColumn(
                label = nomiString("AI calls"),
                value = summary.aiCalls.toString(),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            HeroStatColumn(
                label = nomiString("Cache hits"),
                value = summary.cacheHits.toString(),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            HeroStatColumn(
                label = nomiString("Local parses"),
                value = summary.localParses.toString(),
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExaFreeTierCard(
    usagePreferences: AiUsagePreferences,
    allTimeExaCostUsd: Double,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCredit = usagePreferences.exaFreeCreditUsd.coerceAtLeast(0.01)
    val remainingUsd = (totalCredit - allTimeExaCostUsd).coerceAtLeast(0.0)
    val remainingFraction by animateFloatAsState(
        targetValue = (remainingUsd / totalCredit).toFloat().coerceIn(0f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "exa-free-tier-remaining",
    )
    val estimatedSearchesLeft = (remainingUsd / AiCostEstimator.EXA_SEARCH_DEFAULT_USD).roundToInt()

    SettingsCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NomiExpressiveBadge(
                icon = Icons.Default.CardGiftcard,
                style = ExpressiveBadgeStyle.FLOWER,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                padding = 10.dp,
                iconSize = 20.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = nomiString("Exa free tier"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (usagePreferences.exaFreeTierEnabled) {
                        nomiFormat(
                            "{0} left of {1} starter credit",
                            AiCostEstimator.formatUsd(remainingUsd),
                            AiCostEstimator.formatUsd(totalCredit),
                        )
                    } else {
                        nomiString("Count Exa searches as paid")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = usagePreferences.exaFreeTierEnabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        if (usagePreferences.exaFreeTierEnabled) {
            LinearWavyProgressIndicator(
                progress = { remainingFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (remainingUsd > 0.0) {
                        nomiFormat("~{0} free searches remaining", estimatedSearchesLeft)
                    } else {
                        nomiString("Starter credit used up")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = nomiFormat("{0} used", AiCostEstimator.formatUsd(allTimeExaCostUsd)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
    }
}

@Composable
private fun SavingsRing(
    progress: Float,
    percent: Int,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = (360f * progress).coerceIn(0f, 360f),
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = nomiString("saved"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStatColumn(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
        )
    }
}

@Composable
private fun UsageEmptyCard(modifier: Modifier = Modifier) {
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconTile(MaterialTheme.colorScheme.secondary) {
                Icon(Icons.Default.Payments, contentDescription = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = nomiString("No AI activity recorded yet"),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = nomiString("Log a meal and Nomi will track tokens, cache hits, local parses, and estimated spend here."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageMetricRow(
    icon: @Composable () -> Unit,
    iconColor: Color,
    title: String,
    supporting: String,
    value: String,
) {
    SettingsCard {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(iconColor, icon) },
            trailingContent = {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ModelUsageTile(row: AiModelUsageRow) {
    val share by animateFloatAsState(
        targetValue = row.costShare.coerceIn(0.03f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "model-share",
    )
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIconTile(MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.Memory, contentDescription = null)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.model,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = buildString {
                            append(row.providerId.replaceFirstChar { it.titlecase(Locale.ROOT) })
                            append(" · ")
                            append(nomiFormat("{0} calls", row.calls))
                            val tokens = row.promptTokens + row.completionTokens + row.thoughtsTokens
                            if (tokens > 0L) {
                                append(" · ")
                                append(AiCostEstimator.formatCompactTokens(tokens))
                                append(" tok")
                            }
                            if (row.exaSearches > 0) {
                                append(" · ")
                                append(nomiFormat("{0} searches", row.exaSearches))
                            }
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AiCostEstimator.formatUsd(row.costUsd),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun TaskUsageTile(row: AiTaskUsageRow) {
    val share by animateFloatAsState(
        targetValue = row.share.coerceIn(0.03f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "task-share",
    )
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIconTile(MaterialTheme.colorScheme.secondary) {
                        Icon(Icons.Default.DataUsage, contentDescription = null)
                    }
                    Column {
                        Text(
                            text = localizedTaskName(row.pipeline),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = nomiFormat(
                                "{0} calls · {1} tokens",
                                row.calls,
                                AiCostEstimator.formatCompactTokens(row.totalTokens),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = AiCostEstimator.formatUsd(row.costUsd),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }
    }
}

@Composable
private fun AiUsageRange.localizedLabel(): String = when (this) {
    AiUsageRange.SEVEN_DAYS -> nomiString("7 days")
    AiUsageRange.THIRTY_DAYS -> nomiString("30 days")
    AiUsageRange.ALL -> nomiString("All time")
}

@Composable
private fun localizedTaskName(pipeline: String): String = when (pipeline) {
    "FOOD_RESEARCH" -> nomiString("Food research")
    "FOOD_INTERPRETATION" -> nomiString("Food interpretation")
    "PORTION_CHANGE" -> nomiString("Portion changes")
    "VISION" -> nomiString("Photo & label recognition")
    "RESEARCH_FALLBACK" -> nomiString("Research fallback")
    else -> pipeline.lowercase(Locale.ROOT).replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}
