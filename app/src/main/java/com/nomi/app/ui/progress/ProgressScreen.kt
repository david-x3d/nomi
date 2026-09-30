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
import androidx.compose.material3.HorizontalDivider
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
import com.nomi.app.domain.UnitFormatter
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import com.nomi.app.ui.theme.nomiPageContainerColor
import com.nomi.app.ui.theme.nomiPageMotionSpec
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import com.nomi.app.ui.today.formatted
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressScreen(
    state: ProgressUiState,
    metric: Boolean,
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
                        // The streak is not filtered by the range, so it sits above everything the
                        // range controls and answers "how am I doing" before "over what window".
                        StreakCard(animatedState, Modifier.padding(horizontal = 16.dp))
                        WeightSection(
                            state = animatedState,
                            metric = metric,
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

/**
 * The page's one hero: how long the streak is, and where that sits in the current 30-day cycle.
 *
 * Both numbers are on screen at once and they never contradict each other. The big figure is the
 * user's real total and is never reduced; the ring and the figures underneath it are a *cycle* that
 * starts again every 30 days, so someone on day 67 reads "67 day streak" and "7 / 30, 23 days to 90"
 * and both are true. Presenting only the cycle would have made a long streak look like a fresh one,
 * and presenting only the total would have given nothing to look forward to.
 *
 * A ring of 30 segments rather than a bar, because 30 is a countable number of days: at a glance the
 * reader counts filled segments instead of estimating a fraction. The milestone trail underneath
 * keeps the next few rungs visible, which is what turns "67" into something with a horizon.
 */
@Composable
private fun StreakCard(state: ProgressUiState, modifier: Modifier = Modifier) {
    val locale = nomiLocale()
    val milestone = state.milestone
    NomiCard(
        modifier = modifier.animateContentSize(animationSpec = nomiLayoutMotionSpec()),
        spacing = 20.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = nomiString("Streak"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                    targetState = milestone.streakDays,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 3 })
                            .togetherWith(
                            fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 3 },
                            )
                    },
                    label = "Streak days",
                ) { days ->
                    Text(
                        text = days.formatted(locale),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = nomiString("days in a row"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // One line of prose instead of another number: what the streak means to the next
                // milestone, and what happens if it is let go.
                Text(
                    text = streakCaption(milestone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StreakCycleRing(cycleDay = milestone.cycleDay)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressStat(
                label = nomiString("Days in this cycle"),
                value = nomiFormat("{0} / {1}", milestone.cycleDay, STREAK_MILESTONE_DAYS),
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
            ProgressStat(
                label = nomiString("Next milestone"),
                value = nomiFormat("{0} days", milestone.nextMilestoneDays),
                modifier = Modifier.weight(1f),
                alignment = TextAlign.End,
            )
        }
        ProgressStat(
            label = nomiString("Longest streak"),
            value = nomiFormat("{0} days", maxOf(state.longestStreakDays, milestone.streakDays)),
            modifier = Modifier.fillMaxWidth(),
        )
        MilestoneTrail(
            milestones = milestoneTrail(milestone.streakDays),
            streakDays = milestone.streakDays,
            nextMilestoneDays = milestone.nextMilestoneDays,
        )
    }
}

/**
 * The one sentence under the streak.
 *
 * It deliberately says something the numbers do not: an empty streak says how to start one, a
 * running one says how far the next milestone is, and a milestone day is called out because it is
 * the one day worth noticing.
 */
@Composable
private fun streakCaption(milestone: StreakMilestone): String = when {
    milestone.streakDays == 0 -> nomiString("Log something today to start one.")
    milestone.isMilestoneDay -> nomiFormat(
        "{0} days reached. The next one starts tomorrow.",
        milestone.nextMilestoneDays,
    )
    else -> nomiFormat("{0} days to your next milestone.", milestone.daysUntilNextMilestone)
}

@Composable
private fun WeightSection(
    state: ProgressUiState,
    metric: Boolean,
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
                        text = kilograms?.let { UnitFormatter.formatWeight(it, metric, locale) } ?: "—",
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
                metric = metric,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
            )
            // The change, stated once. The chart shows the shape of the line; this is the sentence
            // the shape is for, and it is the number people actually come to this page for.
            Text(
                text = weightChangeSummary(state, metric, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.startingWeightKg?.let {
                    ProgressStat(
                        label = nomiString("Starting"),
                        value = UnitFormatter.formatWeight(it, metric, locale),
                        modifier = Modifier.weight(1f),
                    )
                }
                state.weights.lastOrNull()?.let {
                    ProgressStat(
                        label = nomiString("Current"),
                        value = UnitFormatter.formatWeight(it.kilograms, metric, locale),
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                }
                state.targetWeightKg?.let {
                    ProgressStat(
                        label = nomiString("Goal"),
                        value = UnitFormatter.formatWeight(it, metric, locale),
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
 * How much the weight actually moved over what is on screen, as one sentence.
 *
 * Signed on purpose: "lost" and "gained" are the words people use, and a minus sign in front of a
 * kilogram is ambiguous when a goal is to gain.
 */
@Composable
private fun weightChangeSummary(state: ProgressUiState, metric: Boolean, locale: Locale): String {
    val first = state.weights.firstOrNull()?.kilograms ?: return ""
    val last = state.weights.lastOrNull()?.kilograms ?: return ""
    val delta = last - first
    if (kotlin.math.abs(delta) < 0.05) return nomiString("Your weight has held steady.")
    val amount = UnitFormatter.formatNumber(kotlin.math.abs(delta), metric, locale)
    val unit = UnitFormatter.weightUnit(metric)
    return if (delta < 0) {
        nomiFormat("{0} {1} down over this range.", amount, unit)
    } else {
        nomiFormat("{0} {1} up over this range.", amount, unit)
    }
}

@Composable
private fun WeightChart(
    points: List<WeightPoint>,
    metric: Boolean,
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
        UnitFormatter.formatNumber(UnitFormatter.weightValue(points.first().kilograms, metric, locale), metric, locale) + " " + UnitFormatter.weightUnit(metric),
        UnitFormatter.formatNumber(UnitFormatter.weightValue(points.last().kilograms, metric, locale), metric, locale) + " " + UnitFormatter.weightUnit(metric),
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

/**
 * How reliably the range was logged, and when.
 *
 * The wavy bar answers "how much", the strip below it answers "when" - thirty columns, one per
 * equal slice of the chosen range, each as tall as the fraction of its days that were logged. On the
 * default 30-day range every column is a single day, so the strip doubles as a calendar of the
 * month; on a year it is 30 two-week slices, and the shape still reads.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConsistencySection(state: ProgressUiState, modifier: Modifier = Modifier) {
    val locale = nomiLocale()
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
        if (state.nutrition.isNotEmpty()) {
            // No caption under the strip: the heading above it already says how many days were
            // logged, and repeating it in the same card is the kind of doubled number this screen
            // was reworked to stop doing.
            LoggingActivityStrip(
                columns = loggingActivityColumns(
                    loggedDates = state.nutrition.map { it.date },
                    rangeStart = state.rangeStart,
                    rangeDays = state.totalDays,
                ),
                description = nomiFormat("Logging activity across {0} days", state.totalDays),
            )
        }
    }
}

@Composable
private fun NutritionAverages(state: ProgressUiState, modifier: Modifier = Modifier) {
    val days = state.nutrition.size.coerceAtLeast(1)
    NomiCard(
        modifier = modifier.animateContentSize(
            animationSpec = nomiLayoutMotionSpec(),
        ),
        spacing = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nomiString("Daily averages"),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                // Says what the numbers below are an average of, which is the part a bare
                // four-row list never told the reader.
                text = nomiFormat("Averaged over {0} logged days", state.nutrition.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressStat(
            label = nomiString("Calories"),
            value = "${state.nutrition.sumOf { it.calories }.div(days).roundToInt()} kcal",
            emphasized = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                Triple(nomiString("Protein"), state.nutrition.sumOf { it.protein }, "g"),
                Triple(nomiString("Carbs"), state.nutrition.sumOf { it.carbohydrates }, "g"),
                Triple(nomiString("Fat"), state.nutrition.sumOf { it.fat }, "g"),
            ).forEach { (label, total, unit) ->
                ProgressStat(
                    label = label,
                    value = "${total.div(days).roundToInt()} $unit",
                    modifier = Modifier.weight(1f),
                    valueStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
