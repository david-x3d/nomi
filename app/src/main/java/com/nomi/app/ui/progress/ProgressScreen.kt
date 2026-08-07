package com.nomi.app.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onRangeChanged: (ProgressRange) -> Unit,
    onAddWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(nomiString("Progress", "Fortschritt")) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ProgressRange.entries) { range ->
                        FilterChip(
                            selected = range == state.range,
                            onClick = { onRangeChanged(range) },
                            label = {
                                Text(
                                    when (range) {
                                        ProgressRange.SEVEN_DAYS -> nomiString("7 days", "7 Tage")
                                        ProgressRange.THIRTY_DAYS -> nomiString("30 days", "30 Tage")
                                        ProgressRange.THREE_MONTHS -> nomiString("3 months", "3 Monate")
                                        ProgressRange.SIX_MONTHS -> nomiString("6 months", "6 Monate")
                                        ProgressRange.ONE_YEAR -> nomiString("1 year", "1 Jahr")
                                        ProgressRange.ALL -> nomiString("All", "Alle")
                                    },
                                )
                            },
                            leadingIcon = if (range == state.range) {
                                {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            } else {
                                null
                            },
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
                            fadeIn(animationSpec = tween(220)) +
                                slideInVertically(animationSpec = tween(340)) { height ->
                                    direction * (height / 12)
                                }
                            ).togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                                slideOutVertically(animationSpec = tween(260)) { height ->
                                    -direction * (height / 16)
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
                            enter = fadeIn(animationSpec = tween(220)) + expandVertically(),
                            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(),
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
private fun WeightSection(
    state: ProgressUiState,
    onAddWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    ElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(nomiString("Weight", "Gewicht"), style = MaterialTheme.typography.titleLarge)
                    AnimatedContent(
                        targetState = state.weights.lastOrNull()?.kilograms,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 3 })
                                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 3 })
                        },
                        label = "Current weight",
                    ) { kilograms ->
                        kilograms?.let {
                            Text(
                                "${formatWeight(it, locale)} kg",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
                Button(onClick = onAddWeight) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(nomiString("Add", "Hinzufügen"))
                }
            }
            if (state.weights.size >= 2) {
                WeightChart(
                    points = state.weights,
                    targetKg = state.targetWeightKg,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    state.startingWeightKg?.let {
                        Text("${nomiString("Starting", "Start")}\n${formatWeight(it, locale)} kg")
                    }
                    state.weights.lastOrNull()?.let {
                        Text("${nomiString("Current", "Aktuell")}\n${formatWeight(it.kilograms, locale)} kg")
                    }
                    state.targetWeightKg?.let {
                        Text("${nomiString("Goal", "Ziel")}\n${formatWeight(it, locale)} kg")
                    }
                }
            } else {
                Text(
                    nomiString(
                        "Log a little more to see your weight trend.",
                        "Trage noch etwas mehr ein, um deinen Gewichtsverlauf zu sehen.",
                    ),
                )
            }
        }
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
    val min = (points.minOf { it.kilograms }.let { if (targetKg != null) minOf(it, targetKg) else it } - 1).toFloat()
    val max = (points.maxOf { it.kilograms }.let { if (targetKg != null) maxOf(it, targetKg) else it } + 1).toFloat()
    val summary = nomiString(
        "Weight trend from ${formatWeight(points.first().kilograms, locale)} to " +
            "${formatWeight(points.last().kilograms, locale)} kilograms across ${points.size} measurements",
        "Gewichtsverlauf von ${formatWeight(points.first().kilograms, locale)} bis " +
            "${formatWeight(points.last().kilograms, locale)} Kilogramm über ${points.size} Messungen",
    )
    Canvas(modifier = modifier.semantics { contentDescription = summary }) {
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
        targetKg?.let {
            val y = size.height - ((it.toFloat() - min) / (max - min)) * size.height
            drawLine(tertiary, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.lastIndex == 0) 0f else index.toFloat() / points.lastIndex * size.width
            val y = size.height - ((point.kilograms.toFloat() - min) / (max - min)) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, point ->
            val x = if (points.lastIndex == 0) 0f else index.toFloat() / points.lastIndex * size.width
            val y = size.height - ((point.kilograms.toFloat() - min) / (max - min)) * size.height
            drawCircle(primary, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ConsistencySection(state: ProgressUiState, modifier: Modifier = Modifier) {
    val fraction = if (state.totalDays == 0) 0f else state.loggingDays.toFloat() / state.totalDays
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "Logging consistency",
    )
    Card(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(nomiString("Consistency", "Regelmäßigkeit"), style = MaterialTheme.typography.titleLarge)
            Text(
                nomiString(
                    "Logged ${state.loggingDays} of ${state.totalDays} days",
                    "An ${state.loggingDays} von ${state.totalDays} Tagen eingetragen",
                ),
            )
            LinearProgressIndicator(progress = { animatedFraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NutritionAverages(state: ProgressUiState, modifier: Modifier = Modifier) {
    val days = state.nutrition.size.coerceAtLeast(1)
    Card(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(nomiString("Daily averages", "Tagesdurchschnitt"), style = MaterialTheme.typography.titleLarge)
            Text(
                nomiString("Calories", "Kalorien") +
                    " ${state.nutrition.sumOf { it.calories }.div(days).roundToInt()} kcal",
            )
            Text(
                nomiString("Protein", "Eiweiß") +
                    " ${state.nutrition.sumOf { it.protein }.div(days).roundToInt()} g",
            )
            Text(
                nomiString("Carbs", "Kohlenhydrate") +
                    " ${state.nutrition.sumOf { it.carbohydrates }.div(days).roundToInt()} g",
            )
            Text(
                nomiString("Fat", "Fett") +
                    " ${state.nutrition.sumOf { it.fat }.div(days).roundToInt()} g",
            )
        }
    }
}

private fun formatWeight(value: Double, locale: Locale): String =
    String.format(locale, "%.1f", value)
