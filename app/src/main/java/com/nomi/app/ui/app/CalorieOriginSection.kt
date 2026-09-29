package com.nomi.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.WebsiteFaviconUrl
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiMessage
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.today.TodayFoodEntry
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Where do the calories come from?" - the part of the detail page that answers it with the
 * entry's own numbers rather than a sentence the AI wrote.
 *
 * Everything here is worked out on the phone from what was saved with the log, so it is always in
 * the language the app is set to, it is the same for an entry researched today and one logged a
 * year ago, and every figure on it can be checked against the others.
 */
@Composable
internal fun CalorieOriginSection(entry: TodayFoodEntry) {
    val breakdown = remember(entry) { entry.calorieBreakdown() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = nomiString("Where do the calories come from?"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = nomiCardShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = nomiCardContainerColor()),
            elevation = nomiCardElevation(),
            border = nomiCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (entry.groupItems.size > 1) {
                    GroupOrigin(entry)
                } else {
                    SingleOrigin(entry)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Composition(breakdown)
                val notes = goodToKnow(entry, breakdown)
                if (notes.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    GoodToKnow(notes)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Origin

@Composable
private fun SingleOrigin(entry: TodayFoodEntry) {
    val origin = entry.nutritionOrigin()
    val locale = nomiLocale()
    val sourceHost = WebsiteFaviconUrl.normalizePublicHttpsHostname(entry.sourceUrl)
    val consultedHosts = remember(entry.citedSourceUrls) {
        entry.citedSourceUrls
            .mapNotNull(WebsiteFaviconUrl::normalizePublicHttpsHostname)
            .distinct()
    }
    val mainlyChecked = if (sourceHost == null) {
        entry.mostConsultedUrl()?.let(WebsiteFaviconUrl::normalizePublicHttpsHostname)
    } else {
        null
    }
    // The source's own name is only worth a line when it says more than the origin title does:
    // a page title does, "Manual entry" under "Entered by you" does not.
    val sourceName = entry.sourceName?.trim()?.takeIf(String::isNotEmpty)
        ?.takeIf { origin == NutritionOrigin.PUBLISHED_SOURCE || origin == NutritionOrigin.SAVED_VALUES }
        ?.let { nomiMessage(it) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OriginHeader(origin)
        Text(
            text = origin.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val facts = buildList {
            sourceName?.let { add(nomiString("Source") to it) }
            sourceHost?.let { add(nomiString("Website") to it) }
            mainlyChecked?.let { add(nomiString("Mainly checked") to it) }
            entry.sourceProductName?.trim()?.takeIf(String::isNotEmpty)?.let {
                add(nomiString("Product on the page") to it)
            }
            entry.sourceServingQuantity?.takeIf { it.isFinite() && it > 0.0 }?.let { quantity ->
                entry.sourceServingUnit?.trim()?.takeIf(String::isNotEmpty)?.let { unit ->
                    add(nomiString("Values are per") to "${formatAmount(quantity, locale)} $unit")
                }
            }
            if (consultedHosts.isNotEmpty()) {
                add(nomiString("Websites checked") to consultedHosts.size.toString())
            }
        }
        if (facts.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { (label, value) -> FactRow(label, value) }
                }
            }
        }
    }
}

/** A combined meal can mix a label, an estimate and a favourite, so each food says its own. */
@Composable
private fun GroupOrigin(entry: TodayFoodEntry) {
    val origins = entry.groupItems.map { it.nutritionOrigin() }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (origins.size == 1) {
            OriginHeader(origins.single())
            Text(
                text = origins.single().description(),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OriginHeader(origin = null)
            Text(
                text = nomiString("The foods in this meal come from different sources:"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.groupItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OriginIcon(item.nutritionOrigin(), size = 30.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.nutritionOrigin().title(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${item.calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OriginHeader(origin: NutritionOrigin?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OriginIcon(origin, size = 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = nomiString("Data origin"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = origin?.title() ?: nomiString("Several sources"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OriginIcon(origin: NutritionOrigin?, size: androidx.compose.ui.unit.Dp) {
    Surface(
        shape = CircleShape,
        color = if (origin == NutritionOrigin.AI_ESTIMATE) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (origin == NutritionOrigin.AI_ESTIMATE) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = origin.icon(),
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

private fun NutritionOrigin?.icon(): ImageVector = when (this) {
    NutritionOrigin.PUBLISHED_SOURCE -> Icons.Rounded.Public
    NutritionOrigin.OPEN_FOOD_FACTS -> Icons.Default.QrCodeScanner
    NutritionOrigin.LABEL_PHOTO -> Icons.Default.PhotoCamera
    NutritionOrigin.AI_ESTIMATE -> Icons.Default.AutoAwesome
    NutritionOrigin.FOOD_LIBRARY -> Icons.Default.Inventory2
    NutritionOrigin.MANUAL -> Icons.Default.Edit
    NutritionOrigin.SHARED -> Icons.Default.Nfc
    NutritionOrigin.SAVED_VALUES, null -> Icons.Default.Storage
}

@Composable
private fun NutritionOrigin.title(): String = when (this) {
    NutritionOrigin.PUBLISHED_SOURCE -> nomiString("Published nutrition table")
    NutritionOrigin.OPEN_FOOD_FACTS -> nomiString("Open Food Facts barcode database")
    NutritionOrigin.LABEL_PHOTO -> nomiString("Your photo of the nutrition label")
    NutritionOrigin.AI_ESTIMATE -> nomiString("Nomi estimate")
    NutritionOrigin.FOOD_LIBRARY -> nomiString("Your food library")
    NutritionOrigin.MANUAL -> nomiString("Entered by you")
    NutritionOrigin.SHARED -> nomiString("Shared from another Nomi")
    NutritionOrigin.SAVED_VALUES -> nomiString("Saved nutrition values")
}

@Composable
private fun NutritionOrigin.description(): String = when (this) {
    NutritionOrigin.PUBLISHED_SOURCE -> nomiString(
        "Nomi read these values from a published nutrition table and scaled them to your amount.",
    )
    NutritionOrigin.OPEN_FOOD_FACTS -> nomiString(
        "The values come from this product's entry in Open Food Facts, a public database built " +
            "from package labels.",
    )
    NutritionOrigin.LABEL_PHOTO -> nomiString(
        "Nomi read the nutrition table in your photo and scaled it to your amount.",
    )
    NutritionOrigin.AI_ESTIMATE -> nomiString(
        "No published table matched this food, so Nomi estimated typical values for it and " +
            "your portion.",
    )
    NutritionOrigin.FOOD_LIBRARY -> nomiString(
        "The values come from a food you logged before, which Nomi keeps on this phone.",
    )
    NutritionOrigin.MANUAL -> nomiString("You entered these values yourself.")
    NutritionOrigin.SHARED -> nomiString(
        "Someone sent this food from their Nomi. The values are theirs and were not looked up " +
            "again.",
    )
    NutritionOrigin.SAVED_VALUES -> nomiString(
        "These values were saved with the entry. No source page was recorded.",
    )
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Composition

@Composable
private fun Composition(breakdown: CalorieBreakdown) {
    val locale = nomiLocale()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = nomiString("Calorie composition"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (breakdown.macros.isEmpty()) {
            Text(
                text = nomiString("No protein, carbohydrate or fat values were recorded for this entry."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        CompositionBar(breakdown)
        breakdown.macros.forEach { macro -> MacroRow(macro, locale) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        TotalRow(
            label = nomiString("From protein, carbs and fat"),
            kcal = breakdown.macroKcal,
            emphasized = false,
        )
        TotalRow(
            label = nomiString("Logged total"),
            kcal = breakdown.loggedKcal,
            emphasized = true,
        )
        breakdown.scaling?.let { scaling ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = nomiString("Calculation"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = nomiFormat(
                            "{0} kcal per {1}, scaled to {2} = {3} kcal",
                            scaling.basisKcal.roundToInt(),
                            "${formatAmount(scaling.basisQuantity, locale)} ${scaling.basisUnit}",
                            "${formatAmount(scaling.portionQuantity, locale)} ${scaling.portionUnit}",
                            breakdown.loggedKcal.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** One bar, split by energy share, so the balance of the food is visible before any number. */
@Composable
private fun CompositionBar(breakdown: CalorieBreakdown) {
    val locale = nomiLocale()
    val labels = breakdown.macros.map { it.macronutrient.label() }
    val description = breakdown.macros.mapIndexed { index, macro ->
        "${labels[index]} ${formatPercent(macro.share, locale)}"
    }.joinToString(", ")
    val colors = breakdown.macros.map { it.macronutrient.color() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        breakdown.macros.forEachIndexed { index, macro ->
            if (macro.share <= 0.0) return@forEachIndexed
            Box(
                modifier = Modifier
                    .weight(macro.share.toFloat().coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(colors[index]),
            )
        }
    }
}

@Composable
private fun MacroRow(macro: MacroEnergy, locale: Locale) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(macro.macronutrient.color()),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = macro.macronutrient.label(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = nomiFormat(
                    "{0} g × {1} kcal per gram",
                    formatAmount(macro.grams, locale),
                    macro.macronutrient.kcalPerGram.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${macro.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatPercent(macro.share, locale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, kcal: Double, emphasized: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${kcal.roundToInt()} kcal",
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun Macronutrient.label(): String = when (this) {
    Macronutrient.PROTEIN -> nomiString("Protein")
    Macronutrient.CARBOHYDRATES -> nomiString("Carbs")
    Macronutrient.FAT -> nomiString("Fat")
}

/** The same colours the nutrient grid above uses, so a macro reads as itself everywhere. */
@Composable
private fun Macronutrient.color(): Color = when (this) {
    Macronutrient.PROTEIN -> MaterialTheme.colorScheme.primary
    Macronutrient.CARBOHYDRATES -> MaterialTheme.colorScheme.secondary
    Macronutrient.FAT -> MaterialTheme.colorScheme.tertiary
}

// ---------------------------------------------------------------------------------------------
// Good to know

@Composable
private fun goodToKnow(entry: TodayFoodEntry, breakdown: CalorieBreakdown): List<String> {
    val locale = nomiLocale()
    return buildList {
        when (breakdown.dominant) {
            Macronutrient.FAT -> add(nomiString("Most of the energy comes from fat, which has 9 kcal per gram - more than twice as much as protein or carbohydrates."))
            Macronutrient.CARBOHYDRATES -> add(nomiString("Most of the energy comes from carbohydrates."))
            Macronutrient.PROTEIN -> add(nomiString("Most of the energy comes from protein."))
            null -> Unit
        }
        breakdown.macros
            .firstOrNull { it.macronutrient == Macronutrient.PROTEIN }
            ?.takeIf { it.share >= HIGH_PROTEIN_SHARE && breakdown.dominant != Macronutrient.PROTEIN }
            ?.let { protein ->
                add(
                    nomiFormat(
                        "A good protein source: {0} of its energy comes from protein.",
                        formatPercent(protein.share, locale),
                    ),
                )
            }
        if (breakdown.energyDensityPer100 != null && breakdown.densityBand != null) {
            val value = "${breakdown.energyDensityPer100.roundToInt()} kcal / 100 ${breakdown.densityUnit}"
            add(
                when (breakdown.densityBand) {
                    EnergyDensityBand.VERY_LOW -> nomiFormat(
                        "Very low energy density ({0}): even a large portion adds few calories.",
                        value,
                    )
                    EnergyDensityBand.LOW -> nomiFormat(
                        "Low energy density ({0}): filling for relatively few calories.",
                        value,
                    )
                    EnergyDensityBand.MEDIUM -> nomiFormat("Medium energy density ({0}).", value)
                    EnergyDensityBand.HIGH -> nomiFormat(
                        "High energy density ({0}): small amounts add up quickly.",
                        value,
                    )
                },
            )
        }
        breakdown.unexplainedKcal?.let { gap ->
            add(
                if (gap > 0) {
                    nomiFormat(
                        "The total is {0} kcal higher than protein, carbs and fat account for. " +
                            "Fiber, alcohol, sugar alcohols and rounding on the label explain gaps like this.",
                        abs(gap).roundToInt(),
                    )
                } else {
                    nomiFormat(
                        "The total is {0} kcal lower than protein, carbs and fat suggest. Labels " +
                            "round each value separately, and some count fiber differently.",
                        abs(gap).roundToInt(),
                    )
                },
            )
        }
        if (entry.groupItems.size > 1) {
            entry.groupItems.maxByOrNull(TodayFoodEntry::calories)
                ?.takeIf { entry.calories > 0.0 }
                ?.let { largest ->
                    add(
                        nomiFormat(
                            "Largest contributor: {0} with {1} kcal ({2} of the meal).",
                            largest.name,
                            largest.calories.roundToInt(),
                            formatPercent(largest.calories / entry.calories, locale),
                        ),
                    )
                }
        }
        if (entry.isEstimated) {
            add(
                nomiString(
                    "Estimates can differ from the food you actually ate, mostly because of the " +
                        "portion size and the recipe. Correcting the amount updates every value here.",
                ),
            )
        }
    }
}

@Composable
private fun GoodToKnow(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = nomiString("Good to know"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val HIGH_PROTEIN_SHARE = 0.25

private fun formatAmount(value: Double, locale: Locale): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else String.format(locale, "%.1f", value)

private fun formatPercent(share: Double, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(share)
