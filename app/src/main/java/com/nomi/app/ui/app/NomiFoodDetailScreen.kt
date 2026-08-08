package com.nomi.app.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.WebsiteFavicon
import com.nomi.app.ui.components.WebsiteFaviconUrl
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
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
                        text = nomiString("Nutrition details", "Ernährungsdetails"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = nomiString("Close nutrition details", "Ernährungsdetails schließen"),
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
                            contentDescription = nomiString(
                                "Correct the eaten amount",
                                "Gegessene Menge korrigieren",
                            ),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = entry != null,
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = nomiString("Food entry actions", "Aktionen für den Lebensmitteleintrag"),
                            )
                        }
                        DropdownMenu(
                            expanded = false,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(nomiString("Add to favorites", "Zu Favoriten hinzufügen")) },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                                enabled = entry?.foodId != null,
                                onClick = {
                                    menuExpanded = false
                                    entry?.let { onFavorite(it.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(nomiString("Duplicate entry", "Eintrag duplizieren")) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    entry?.let { onDuplicate(it.id) }
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(nomiString("Delete entry", "Eintrag löschen")) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    entry?.let {
                                        onDelete(it.id)
                                        onBack()
                                    }
                                },
                            )
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEntryActionsSheet(
    entry: TodayFoodEntry,
    onDismiss: () -> Unit,
    onEditAmount: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var contentVisible by remember { mutableStateOf(false) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(Unit) { contentVisible = true }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = effectsSpec) +
                slideInVertically(animationSpec = spatialSpec) { height -> height / 8 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = nomiString("Entry actions", "Aktionen"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FoodEntryActionRow(
                    icon = Icons.Default.Edit,
                    title = nomiString("Change amount", "Menge ändern"),
                    description = nomiString(
                        "Recalculate this entry if you ate more or less",
                        "Neu berechnen, wenn du mehr oder weniger gegessen hast",
                    ),
                    onClick = onEditAmount,
                )
                FoodEntryActionRow(
                    icon = Icons.Default.Favorite,
                    title = nomiString("Add to favorites", "Zu Favoriten hinzufügen"),
                    description = if (entry.foodId != null) {
                        nomiString(
                            "Keep this food ready for faster logging",
                            "Dieses Lebensmittel schneller wieder eintragen",
                        )
                    } else {
                        nomiString(
                            "Available after this food is saved to your library",
                            "Verfügbar, sobald das Lebensmittel gespeichert ist",
                        )
                    },
                    enabled = entry.foodId != null,
                    onClick = onFavorite,
                )
                FoodEntryActionRow(
                    icon = Icons.Default.ContentCopy,
                    title = nomiString("Duplicate entry", "Eintrag duplizieren"),
                    description = nomiString(
                        "Add another copy to this day",
                        "Eine weitere Portion für diesen Tag eintragen",
                    ),
                    onClick = onDuplicate,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                FoodEntryActionRow(
                    icon = Icons.Default.Delete,
                    title = nomiString("Delete entry", "Eintrag löschen"),
                    description = nomiString(
                        "Remove it from this day",
                        "Aus diesem Tag entfernen",
                    ),
                    destructive = true,
                    onClick = onDelete,
                )
            }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        destructive -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val pressedColor = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isPressed) pressedColor else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 76.dp)
            .alpha(if (enabled) 1f else 0.78f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            contentColor = contentColor,
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
                text = nomiString("Loading nutrition…", "Nährwerte werden geladen…"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutritionContent(
    entry: TodayFoodEntry,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "heading") {
            EntryHeading(entry)
        }
        item(key = "nutrition") {
            NutritionSummaryCard(entry = entry)
        }
        item(key = "items") {
            ItemAndSourceCard(entry = entry)
        }
        item(key = "explanation") {
            EstimateExplanationCard(entry = entry)
        }
    }
}

@Composable
private fun EntryHeading(entry: TodayFoodEntry) {
    val categoryLabel = when (entry.mealCategory) {
        MealCategory.BREAKFAST -> nomiString("Breakfast", "Frühstück")
        MealCategory.LUNCH -> nomiString("Lunch", "Mittagessen")
        MealCategory.DINNER -> nomiString("Dinner", "Abendessen")
        MealCategory.SNACKS -> nomiString("Snacks", "Snacks")
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
                    text = nomiString("Estimated", "Geschätzt"),
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
private fun NutritionSummaryCard(entry: TodayFoodEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroMetric(
                    label = nomiString("Protein", "Eiweiß"),
                    grams = entry.proteinGrams,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                MacroMetric(
                    label = nomiString("Carbs", "Kohlenhydrate"),
                    grams = entry.carbohydrateGrams,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                MacroMetric(
                    label = nomiString("Fat", "Fett"),
                    grams = entry.fatGrams,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacroMetric(
    label: String,
    grams: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${formatNumber(grams, locale)} g",
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
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemAndSourceCard(entry: TodayFoodEntry) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(true) }
    val locale = nomiLocale()
    val quantityText = entry.quantityDisplay(locale).withContext
    val sourceHostname = remember(entry.sourceUrl) {
        WebsiteFaviconUrl.normalizePublicHttpsHostname(entry.sourceUrl)
    }
    val sourceLabel = entry.sourceName?.takeIf { it.isNotBlank() }
        ?: if (entry.isEstimated) {
            nomiString("Nomi estimate", "Nomi-Schätzung")
        } else {
            nomiString("Manually logged", "Manuell eingetragen")
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                        text = nomiString("Items & source", "Eintrag & Quelle"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = nomiString("1 item • $sourceLabel", "1 Eintrag • $sourceLabel"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        nomiString("Collapse item details", "Eintragsdetails einklappen")
                    } else {
                        nomiString("Expand item details", "Eintragsdetails ausklappen")
                    },
                )
            }
            AnimatedVisibility(visible = expanded) {
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
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = nomiString("Nutrition source", "Nährwertquelle"),
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimateExplanationCard(entry: TodayFoodEntry) {
    val locale = nomiLocale()
    val amountDetail = entry.quantityDisplay(locale).withContext
    val sourceDetail = entry.sourceName?.takeIf { it.isNotBlank() }
        ?: if (entry.isEstimated) {
            nomiString("the food description you entered", "deiner eingegebenen Lebensmittelbeschreibung")
        } else {
            nomiString("the saved nutrition values", "den gespeicherten Nährwerten")
        }
    val explanation = if (entry.isEstimated) {
        nomiString(
            "Nomi estimated this entry for $amountDetail using $sourceDetail. The serving size and matching food source have the biggest effect on the result, so the actual nutrition may vary.",
            "Nomi hat diesen Eintrag für $amountDetail anhand von $sourceDetail geschätzt. Portionsgröße und passende Lebensmittelquelle beeinflussen das Ergebnis am stärksten, daher können die tatsächlichen Nährwerte abweichen.",
        )
    } else {
        nomiString(
            "These totals use $sourceDetail and the logged amount of $amountDetail. Change the serving amount if this does not match what you ate.",
            "Diese Gesamtwerte basieren auf $sourceDetail und der eingetragenen Menge von $amountDetail. Ändere die Portionsmenge, wenn sie nicht dem entspricht, was du gegessen hast.",
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = nomiString("Nomi’s thought process", "Nomis Gedankengang"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (entry.isEstimated) {
                            nomiString("Estimate summary", "Zusammenfassung der Schätzung")
                        } else {
                            nomiString("Nutrition summary", "Nährwertübersicht")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
                    )
                }
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = nomiString(
                    "This is a concise explanation of the amount and source used—not a step-by-step AI transcript.",
                    "Dies ist eine kurze Erklärung der verwendeten Menge und Quelle – kein schrittweises KI-Protokoll.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
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
