package com.nomi.app.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.nomi.app.domain.Micronutrient
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.NomiSheetHeader
import com.nomi.app.ui.components.WebsiteFavicon
import com.nomi.app.ui.components.WebsiteFaviconUrl
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomiFoodDetailScreen(
    entry: TodayFoodEntry?,
    enabledMicronutrients: List<Micronutrient>,
    onBack: () -> Unit,
    onFavorite: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEditAmount: (TodayFoodEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = nomiString("Nutrition details"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = nomiString("Close nutrition details"),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { entry?.let(onEditAmount) },
                        enabled = entry != null,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = nomiString("Correct the eaten amount"),
                        )
                    }
                    // The overflow raises a sheet, not a menu: the actions need a line of
                    // explanation each, which a menu row has no room for.
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = entry != null,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = nomiString("Food entry actions"),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        if (entry == null) {
            LoadingNutrition(modifier = Modifier.padding(innerPadding))
        } else {
            NutritionContent(
                entry = entry,
                enabledMicronutrients = enabledMicronutrients,
                onEditAmount = { onEditAmount(entry) },
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 40.dp,
                ),
            )
        }
    }

    if (menuExpanded && entry != null) {
        FoodEntryActionsSheet(
            entry = entry,
            onDismiss = { menuExpanded = false },
            onEditAmount = {
                menuExpanded = false
                onEditAmount(entry)
            },
            onFavorite = {
                menuExpanded = false
                onFavorite(entry.id)
            },
            onDuplicate = {
                menuExpanded = false
                onDuplicate(entry.id)
            },
            onDelete = {
                menuExpanded = false
                onDelete(entry.id)
                onBack()
            },
        )
    }
}

@Composable
private fun FoodEntryActionsSheet(
    entry: TodayFoodEntry,
    onDismiss: () -> Unit,
    onEditAmount: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    NomiSheet(onDismissRequest = onDismiss) {
        NomiSheetHeader(
            title = nomiString("Entry actions"),
            subtitle = entry.name,
            icon = Icons.Default.Restaurant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FoodEntryActionRow(
                icon = Icons.Default.Edit,
                title = nomiString("Change amount"),
                description = nomiString("Recalculate this entry if you ate more or less"),
                onClick = onEditAmount,
            )
            FoodEntryActionRow(
                icon = Icons.Default.Favorite,
                title = nomiString("Add to favorites"),
                description = if (entry.foodId != null) {
                    nomiString("Keep this food ready for faster logging")
                } else {
                    nomiString("Available after this food is saved to your library")
                },
                enabled = entry.foodId != null,
                onClick = onFavorite,
            )
            FoodEntryActionRow(
                icon = Icons.Default.ContentCopy,
                title = nomiString("Duplicate entry"),
                description = nomiString("Add another copy to this day"),
                onClick = onDuplicate,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            FoodEntryActionRow(
                icon = Icons.Default.Delete,
                title = nomiString("Delete entry"),
                description = nomiString("Remove it from this day"),
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun FoodEntryActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    // A destructive row is a full errorContainer card with onErrorContainer text, which is the
    // pairing those two roles are contrast-checked as. The previous mix - saturated error text
    // on a 38%-alpha errorContainer - was neither red enough to warn nor legible enough to read.
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val iconColor = when {
        !enabled -> contentColor
        destructive -> MaterialTheme.colorScheme.onError
        else -> contentColor
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.72f),
        shape = nomiCardShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (destructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                nomiCardContainerColor()
            },
            contentColor = contentColor,
            disabledContainerColor = nomiCardContainerColor(),
            disabledContentColor = contentColor,
        ),
        elevation = nomiCardElevation(),
        // The filled error surface carries its own edge; the neutral card border drawn over it
        // is what made the row look like two overlapping shapes.
        border = if (destructive) null else nomiCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                contentColor = iconColor,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (destructive && enabled) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.62f)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingNutrition(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = nomiString("Loading nutrition…"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutritionContent(
    entry: TodayFoodEntry,
    enabledMicronutrients: List<Micronutrient>,
    onEditAmount: () -> Unit,
    contentPadding: PaddingValues,
) {
    val backdrop = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.07f),
            MaterialTheme.colorScheme.surface,
        ),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(backdrop),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "heading") {
            EntryHeading(entry)
        }
        item(key = "nutrition") {
            NutritionSummaryCard(
                entry = entry,
                enabledMicronutrients = enabledMicronutrients,
            )
        }
        item(key = "items") {
            ItemAndSourceCard(entry = entry)
        }
        item(key = "explanation") {
            EstimateExplanationCard(entry = entry, onEditAmount = onEditAmount)
        }
        item(key = "references") {
            ReferencesCard(entry = entry)
        }
    }
}

@Composable
private fun EntryHeading(entry: TodayFoodEntry) {
    val categoryLabel = when (entry.mealCategory) {
        MealCategory.BREAKFAST -> nomiString("Breakfast")
        MealCategory.LUNCH -> nomiString("Lunch")
        MealCategory.DINNER -> nomiString("Dinner")
        MealCategory.SNACKS -> nomiString("Snacks")
    }
    val locale = nomiLocale()
    val quantityText = entry.quantityDisplay(locale).withContext
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelPill(categoryLabel)
            if (entry.isEstimated) {
                LabelPill(
                    text = nomiString("Estimated"),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        entry.brand?.takeIf { it.isNotBlank() }?.let { brand ->
            Text(
                text = brand,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$quantityText  •  ${entry.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionSummaryCard(
    entry: TodayFoodEntry,
    enabledMicronutrients: List<Micronutrient>,
) {
    val metrics = buildList {
        add(
            NutritionMetric(
                nomiString("Protein"),
                entry.proteinGrams,
                "g",
                MaterialTheme.colorScheme.primary,
            ),
        )
        add(
            NutritionMetric(
                nomiString("Carbs"),
                entry.carbohydrateGrams,
                "g",
                MaterialTheme.colorScheme.secondary,
            ),
        )
        add(
            NutritionMetric(
                nomiString("Fat"),
                entry.fatGrams,
                "g",
                MaterialTheme.colorScheme.tertiary,
            ),
        )
        enabledMicronutrients.distinct().forEach { nutrient ->
            add(
                NutritionMetric(
                    label = nutrient.localizedName(),
                    amount = nutrient.amountIn(entry),
                    unit = nutrient.storageUnit.suffix,
                    color = nutrient.metricColor(),
                ),
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = nomiCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = nomiCardContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = nomiCardElevation(),
        border = nomiCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.calories.roundToInt().toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CenteredNutrientGrid(metrics)
        }
    }
}

private data class NutritionMetric(
    val label: String,
    val amount: Double?,
    val unit: String,
    val color: Color,
)

/** Three values fill a row; one or two remaining values form a centered group beneath it. */
@Composable
private fun CenteredNutrientGrid(metrics: List<NutritionMetric>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val cellWidth = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            metrics.chunked(3).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = gap,
                        alignment = Alignment.CenterHorizontally,
                    ),
                ) {
                    rowMetrics.forEach { metric ->
                        NutrientMetric(metric = metric, modifier = Modifier.width(cellWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientMetric(
    metric: NutritionMetric,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${formatNumber(metric.amount ?: 0.0, locale)} ${metric.unit}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(metric.color),
            )
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Micronutrient.amountIn(entry: TodayFoodEntry): Double? = when (this) {
    Micronutrient.FIBER -> entry.fiberGrams
    Micronutrient.SUGAR -> entry.sugarGrams
    Micronutrient.SATURATED_FAT -> entry.saturatedFatGrams
    Micronutrient.SODIUM -> entry.sodiumMilligrams
}

@Composable
private fun Micronutrient.metricColor(): Color = when (this) {
    Micronutrient.FIBER -> MaterialTheme.colorScheme.primary
    Micronutrient.SUGAR -> MaterialTheme.colorScheme.secondary
    Micronutrient.SATURATED_FAT -> MaterialTheme.colorScheme.tertiary
    Micronutrient.SODIUM -> MaterialTheme.colorScheme.error
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemAndSourceCard(entry: TodayFoodEntry) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(true) }
    val locale = nomiLocale()
    val quantityText = entry.quantityDisplay(locale).withContext
    val contentSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val contentEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val sourceHostname = remember(entry.sourceUrl) {
        WebsiteFaviconUrl.normalizePublicHttpsHostname(entry.sourceUrl)
    }
    val sourceLabel = entry.sourceName?.takeIf { it.isNotBlank() }
        ?: if (entry.isEstimated) {
            nomiString("Nomi estimate")
        } else {
            nomiString("Manually logged")
        }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = nomiCardShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = nomiCardContainerColor(),
        ),
        elevation = nomiCardElevation(),
        border = nomiCardBorder(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 17.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = nomiString("Items & source"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = nomiFormat("1 item • {0}", sourceLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        nomiString("Collapse item details")
                    } else {
                        nomiString("Expand item details")
                    },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = contentEffectsSpec) +
                    expandVertically(animationSpec = contentSpatialSpec),
                exit = fadeOut(animationSpec = contentEffectsSpec) +
                    shrinkVertically(animationSpec = contentSpatialSpec),
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = quantityText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${entry.calories.roundToInt()} kcal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = nomiString("Nutrition source"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                WebsiteFavicon(
                                    sourceUrl = entry.sourceUrl,
                                    size = 34.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = sourceLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    sourceHostname?.let { hostname ->
                                        Text(
                                            text = hostname,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            // What the page itself said, which is the part that makes the number
                            // checkable: the product it names, and the amount its values describe.
                            SourceFactLine(
                                label = nomiString("Product on the page"),
                                value = entry.sourceProductName?.takeIf { it.isNotBlank() },
                            )
                            SourceFactLine(
                                label = nomiString("Values are per"),
                                value = entry.sourceServingText(locale),
                            )
                            if (entry.sourceProductName.isNullOrBlank() &&
                                entry.sourceServingText(locale) == null
                            ) {
                                Text(
                                    text = if (entry.isEstimated) {
                                        nomiString(
                                            "Nomi worked this out from your description rather " +
                                                "than from a published table.",
                                        )
                                    } else {
                                        nomiString("No source page recorded these values.")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimateExplanationCard(entry: TodayFoodEntry, onEditAmount: () -> Unit) {
    val locale = nomiLocale()
    val amountDetail = entry.quantityDisplay(locale).withContext
    val sourceDetail = entry.sourceName?.takeIf { it.isNotBlank() }
        ?: if (entry.isEstimated) {
            nomiString("the food description you entered")
        } else {
            nomiString("the saved nutrition values")
        }
    val explanation = if (entry.isEstimated) {
        nomiFormat(
            "Nomi estimated this entry for {0} using {1}. The serving size and matching food " +
                "source have the biggest effect on the result, so the actual nutrition may vary.",
            amountDetail,
            sourceDetail,
        )
    } else {
        nomiFormat(
            "These totals use {0} and the logged amount of {1}. Change the serving amount if " +
                "this does not match what you ate.",
            sourceDetail,
            amountDetail,
        )
    }

    // The section name sits above the card the way "References" does, so the card itself opens
    // on the confidence ring instead of repeating its own title.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = nomiString("Nomi’s thought process"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = nomiCardShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = nomiCardContainerColor(),
            ),
            elevation = nomiCardElevation(),
            border = nomiCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConfidenceHeader(confidence = entry.confidence, isEstimated = entry.isEstimated)
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CorrectionPrompt(onClick = onEditAmount)
            }
        }
    }
}

/** The way out of a wrong estimate: the amount editor, one tap from the reasoning that produced it. */
@Composable
private fun CorrectionPrompt(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = nomiString("Something off? Tap to edit"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The serving the cited page's values describe, e.g. "100 g".
 *
 * Null when nothing recorded one, which is the case for estimates and for entries logged before
 * the source breakdown existed. It is deliberately not defaulted to "100 g": a guessed basis is
 * worse than an absent one, because it invites arithmetic that was never done.
 */
private fun TodayFoodEntry.sourceServingText(locale: Locale): String? {
    val quantity = sourceServingQuantity?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val unit = sourceServingUnit?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return "${formatNumber(quantity, locale)} $unit"
}

@Composable
private fun SourceFactLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Confidence bands. 80 and above reads as trustworthy, below 50 as a rough guess. */
private enum class ConfidenceBand { HIGH, MEDIUM, LOW }

private fun confidenceBandFor(percent: Int): ConfidenceBand = when {
    percent >= 80 -> ConfidenceBand.HIGH
    percent >= 50 -> ConfidenceBand.MEDIUM
    else -> ConfidenceBand.LOW
}

/**
 * Green, amber and red are stated outright rather than mapped onto theme roles: this is a
 * traffic light, and a dynamic-color palette would happily make "low confidence" look inviting.
 * The dark variants are lightened so they stay legible on a dark card.
 */
@Composable
private fun ConfidenceBand.color(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (this) {
        ConfidenceBand.HIGH -> if (dark) Color(0xFF7BD08A) else Color(0xFF2E7D32)
        ConfidenceBand.MEDIUM -> if (dark) Color(0xFFE8B45C) else Color(0xFFA96A00)
        ConfidenceBand.LOW -> if (dark) Color(0xFFF08C86) else Color(0xFFC0392B)
    }
}

@Composable
private fun ConfidenceHeader(confidence: Double?, isEstimated: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // A confidence is only meaningful when something claimed one. Older entries and manual
        // ones fall back to the section icon rather than inventing a score.
        val percent = confidence
            ?.takeIf { it.isFinite() }
            ?.let { (it * 100).roundToInt().coerceIn(0, 100) }
        if (percent == null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
        } else {
            ConfidenceRing(percent = percent, color = confidenceBandFor(percent).color())
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // Without a score there is no level to label, and the section title above already
            // names the card, so the entry-kind line stands on its own.
            if (percent != null) {
                Text(
                    text = nomiString("Confidence level"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when {
                    percent != null -> when (confidenceBandFor(percent)) {
                        ConfidenceBand.HIGH -> nomiString("Very high")
                        ConfidenceBand.MEDIUM -> nomiString("Moderate")
                        ConfidenceBand.LOW -> nomiString("Low")
                    }
                    isEstimated -> nomiString("Estimate summary")
                    else -> nomiString("Nutrition summary")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = percent?.let { confidenceBandFor(it).color() }
                    ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ConfidenceRing(percent: Int, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = nomiFormat("Confidence {0} out of 100", percent)
    Box(
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (percent / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = percent.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * Every page the research cited, collapsed to a stack of site icons until it is opened.
 *
 * Only hostnames that survive [WebsiteFaviconUrl] validation are shown, so a malformed or
 * private URL from a provider cannot end up as a row the user is invited to trust.
 */
@Composable
private fun ReferencesCard(entry: TodayFoodEntry) {
    val references = remember(entry.citedSourceUrls) {
        entry.citedSourceUrls
            .mapNotNull { url ->
                WebsiteFaviconUrl.normalizePublicHttpsHostname(url)?.let { host -> url to host }
            }
            .distinctBy { (_, host) -> host }
    }
    // A manual entry never had sources and should not be asked about them. Anything Nomi
    // produced does get the section, even with nothing to list, because "no sources" is an
    // answer the reader needs and an empty space is not.
    val nomiProduced = entry.isEstimated || !entry.sourceName.isNullOrBlank()
    if (references.isEmpty() && !nomiProduced) return
    if (references.isEmpty()) {
        NoReferencesCard(isEstimated = entry.isEstimated)
        return
    }

    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = nomiString("References"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = nomiCardShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = nomiCardContainerColor()),
            elevation = nomiCardElevation(),
            border = nomiCardBorder(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FaviconRow(hostUrls = references.map { (url, _) -> url })
                    Spacer(Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (references.size == 1) {
                                nomiFormat("{0} source", references.size)
                            } else {
                                nomiFormat("{0} sources", references.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (expanded) {
                                nomiString("Hide sources")
                            } else {
                                nomiString("Show sources")
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    // A carousel rather than a stacked list: seven sources are a strip to skim
                    // sideways, not seven rows pushing the rest of the page off screen.
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(references, key = { (_, host) -> host }) { (url, host) ->
                            SourceChip(
                                host = host,
                                url = url,
                                onOpen = { runCatching { uriHandler.openUri(url) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Says outright that nothing was cited.
 *
 * An estimate with no References section reads as a section that failed to load; saying Nomi did
 * not read a page is both true and the thing that tells you how much to trust the number.
 */
@Composable
private fun NoReferencesCard(isEstimated: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = nomiString("References"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = nomiCardShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = nomiCardContainerColor()),
            elevation = nomiCardElevation(),
            border = nomiCardBorder(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = if (isEstimated) {
                        nomiString("No sources. Nomi estimated this from your description.")
                    } else {
                        nomiString("No sources were recorded for this entry.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The collapsed summary: site icons overlapping left to right, newest research first. */
@Composable
private fun FaviconRow(hostUrls: List<String>, maxIcons: Int = 5) {
    val shown = hostUrls.take(maxIcons)
    val overlap = 18.dp
    Box(
        modifier = Modifier
            .width(30.dp + overlap * (shown.size - 1).coerceAtLeast(0))
            .height(30.dp),
    ) {
        shown.forEachIndexed { index, url ->
            WebsiteFavicon(
                sourceUrl = url,
                size = 30.dp,
                modifier = Modifier
                    .offset(x = overlap * index)
                    .zIndex(index.toFloat()),
            )
        }
    }
}

/** One site in the carousel: its icon, its domain, and a tap that opens the page. */
@Composable
private fun SourceChip(host: String, url: String, onOpen: () -> Unit) {
    val openDescription = nomiFormat("Open {0}", host)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        modifier = Modifier
            .widthIn(max = 220.dp)
            .semantics { contentDescription = openDescription },
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WebsiteFavicon(sourceUrl = url, size = 24.dp)
            Text(
                text = host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun LabelPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatNumber(value: Double, locale: Locale): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(locale, "%.1f", value)
