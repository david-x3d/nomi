package com.nomi.app.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import com.nomi.app.ui.theme.nomiPageContainerColor
import com.nomi.app.ui.theme.nomiPageMotionSpec
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onRangeChanged: (ProgressRange) -> Unit,
    onAddWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The title collapses into the bar as you read down, which is what gives a Material screen
    // its sense of depth without adding a single element to it.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pageContainerColor = nomiPageContainerColor(
        accent = MaterialTheme.colorScheme.secondaryContainer,
        strength = 0.09f,
    )
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(nomiString("Progress")) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageContainerColor,
                    scrolledContainerColor = pageContainerColor,
                ),
            )
        },
        containerColor = pageContainerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(pageContainerColor),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ProgressRange.entries) { range ->
                        FilterChip(
                            selected = range == state.range,
                            onClick = { onRangeChanged(range) },
                            label = { Text(range.label()) },
                        )
                    }
                }
            }
            item(key = "progress-range-content") {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it.range },
                    transitionSpec = {
                        val direction = if (targetState.range.ordinal > initialState.range.ordinal) 1 else -1
                        (
                            fadeIn(animationSpec = nomiFadeMotionSpec()) +
                                slideInVertically(animationSpec = nomiPageMotionSpec()) { height ->
                                    direction * (height / 20)
                                }
                            ).togetherWith(
                            fadeOut(animationSpec = nomiFadeMotionSpec()) +
                                slideOutVertically(animationSpec = nomiPageMotionSpec()) { height ->
                                    -direction * (height / 24)
                                },
                        )
                    },
                    label = "Progress range",
                ) { animatedState ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WeightSection(
                            state = animatedState,
                            onAddWeight = onAddWeight,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        ConsistencySection(animatedState, Modifier.padding(horizontal = 16.dp))
                        AnimatedVisibility(
                            visible = animatedState.nutrition.isNotEmpty(),
                            enter = fadeIn(animationSpec = nomiFadeMotionSpec()) + expandVertically(
                                animationSpec = nomiLayoutMotionSpec(),
                            ),
                            exit = fadeOut(animationSpec = nomiFadeMotionSpec()) + shrinkVertically(
                                animationSpec = nomiLayoutMotionSpec(),
                            ),
                        ) {
                            NutritionAverages(animatedState, Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProgressRange.label(): String = when (this) {
    ProgressRange.SEVEN_DAYS -> nomiString("7 days")
    ProgressRange.THIRTY_DAYS -> nomiString("30 days")
    ProgressRange.THREE_MONTHS -> nomiString("3 months")
    ProgressRange.SIX_MONTHS -> nomiString("6 months")
    ProgressRange.ONE_YEAR -> nomiString("1 year")
    ProgressRange.ALL -> nomiString("All")
}

@Composable
private fun WeightSection(
    state: ProgressUiState,
    onAddWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    NomiCard(
        modifier = modifier.animateContentSize(
            animationSpec = nomiLayoutMotionSpec(),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nomiString("Weight"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                    targetState = state.weights.lastOrNull()?.kilograms,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 3 })
                            .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 3 })
                    },
                    label = "Current weight",
                ) { kilograms ->
                    Text(
                        text = kilograms?.let { "${formatWeight(it, locale)} kg" } ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            FilledTonalButton(onClick = onAddWeight) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = nomiString("Add"),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }
        }
        if (state.weights.size >= 2) {
            WeightChart(
                points = state.weights,
                targetKg = state.targetWeightKg,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.startingWeightKg?.let {
                    WeightMilestone(
                        label = nomiString("Starting"),
                        value = "${formatWeight(it, locale)} kg",
                        modifier = Modifier.weight(1f),
                    )
                }
                state.weights.lastOrNull()?.let {
                    WeightMilestone(
                        label = nomiString("Current"),
                        value = "${formatWeight(it.kilograms, locale)} kg",
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                }
                state.targetWeightKg?.let {
                    WeightMilestone(
                        label = nomiString("Goal"),
                        value = "${formatWeight(it, locale)} kg",
                        modifier = Modifier.weight(1f),
                        alignment = TextAlign.End,
                    )
                }
            }
        } else {
            Text(
                text = nomiString("Log a little more to see your weight trend."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Label above value, both typographically ranked. The three used to be single strings with a
 * newline in them, which left the numbers unaligned and unreadable at a glance.
 */
@Composable
private fun WeightMilestone(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    alignment: TextAlign = TextAlign.Start,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = alignment,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = alignment,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WeightChart(
    points: List<WeightPoint>,
    targetKg: Double?,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val chartReveal = remember { Animatable(0f) }
    LaunchedEffect(points, targetKg) {
        chartReveal.snapTo(0f)
        chartReveal.animateTo(1f, animationSpec = nomiProgressMotionSpec())
    }
    val min = (points.minOf { it.kilograms }.let { if (targetKg != null) minOf(it, targetKg) else it } - 1).toFloat()
    val max = (points.maxOf { it.kilograms }.let { if (targetKg != null) maxOf(it, targetKg) else it } + 1).toFloat()
    val summary = nomiFormat(
        "Weight trend from {0} to {1} kilograms across {2} measurements",
        formatWeight(points.first().kilograms, locale),
        formatWeight(points.last().kilograms, locale),
        points.size,
    )
    Canvas(modifier = modifier.semantics { contentDescription = summary }) {
        drawLine(
            track,
            Offset(0f, size.height),
            Offset(size.width, size.height),
            strokeWidth = 2.dp.toPx(),
        )
        val path = Path()
        val fillPath = Path()
        var lastX = 0f
        points.forEachIndexed { index, point ->
            val x = if (points.lastIndex == 0) 0f else index.toFloat() / points.lastIndex * size.width
            val settledY = size.height - ((point.kilograms.toFloat() - min) / (max - min)) * size.height
            val y = size.height + (settledY - size.height) * chartReveal.value
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
        }
        fillPath.lineTo(lastX, size.height)
        fillPath.close()
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.24f), primary.copy(alpha = 0.02f)),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(path, primary, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        // The marker is drawn after the translucent area so its dashes keep their own colour.
        targetKg?.let {
            val y = size.height - ((it.toFloat() - min) / (max - min)) * size.height
            drawLine(
                tertiary.copy(alpha = 0.72f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                ),
            )
        }
        points.forEachIndexed { index, point ->
            val x = if (points.lastIndex == 0) 0f else index.toFloat() / points.lastIndex * size.width
            val settledY = size.height - ((point.kilograms.toFloat() - min) / (max - min)) * size.height
            val y = size.height + (settledY - size.height) * chartReveal.value
            drawCircle(primary, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConsistencySection(state: ProgressUiState, modifier: Modifier = Modifier) {
    val fraction = if (state.totalDays == 0) 0f else state.loggingDays.toFloat() / state.totalDays
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "Logging consistency",
    )
    NomiCard(
        modifier = modifier.animateContentSize(
            animationSpec = nomiLayoutMotionSpec(),
        ),
        spacing = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nomiString("Consistency"),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = nomiFormat(
                        "{0} of {1} days logged",
                        state.loggingDays,
                        state.totalDays,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${(animatedFraction * 100).roundToInt()} %",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun NutritionAverages(state: ProgressUiState, modifier: Modifier = Modifier) {
    val days = state.nutrition.size.coerceAtLeast(1)
    NomiCard(
        modifier = modifier.animateContentSize(
            animationSpec = nomiLayoutMotionSpec(),
        ),
        spacing = 12.dp,
    ) {
        Text(
            text = nomiString("Daily averages"),
            style = MaterialTheme.typography.titleMedium,
        )
        AverageRow(
            label = nomiString("Calories"),
            value = "${state.nutrition.sumOf { it.calories }.div(days).roundToInt()} kcal",
            emphasized = true,
        )
        AverageRow(
            label = nomiString("Protein"),
            value = "${state.nutrition.sumOf { it.protein }.div(days).roundToInt()} g",
        )
        AverageRow(
            label = nomiString("Carbs"),
            value = "${state.nutrition.sumOf { it.carbohydrates }.div(days).roundToInt()} g",
        )
        AverageRow(
            label = nomiString("Fat"),
            value = "${state.nutrition.sumOf { it.fat }.div(days).roundToInt()} g",
        )
    }
}

/**
 * Label left, number right. These were one concatenated string per line, so the values sat
 * wherever the label happened to end and could not be compared down the column.
 */
@Composable
private fun AverageRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
        )
    }
}

private fun formatWeight(value: Double, locale: Locale): String =
    String.format(locale, "%.1f", value)
