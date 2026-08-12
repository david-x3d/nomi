package com.nomi.app.ui.today

import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.domain.Micronutrient
import com.nomi.app.domain.PortionChangeValidator
import java.time.LocalDate
import java.time.LocalTime

enum class MealCategory(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
}

enum class AddFoodMethod(val displayName: String) {
    TYPE("Type"),
    VOICE("Voice"),
    PHOTO("Photo"),
    MENU("Scan menu"),
    BARCODE("Barcode"),
    LABEL("Nutrition label"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    SAVED_MEALS("Saved meals"),
}

data class MacroProgress(
    val consumedGrams: Double,
    val targetGrams: Double,
) {
    val fraction: Float
        get() = if (targetGrams <= 0) 0f else (consumedGrams / targetGrams).toFloat().coerceIn(0f, 1f)
}

/**
 * One tracked micronutrient's day so far.
 *
 * [consumed] is null when nothing logged today reported this nutrient at all, which the card
 * shows as "no data" rather than as zero. A day where every source stayed silent is not a day
 * without sugar, and saying otherwise would be the app inventing a number.
 */
data class MicronutrientProgress(
    val nutrient: Micronutrient,
    val consumed: Double?,
    val target: Double,
    /** True when some of today's foods reported this nutrient and others did not. */
    val isPartial: Boolean = false,
) {
    val fraction: Float
        get() {
            val amount = consumed ?: return 0f
            return if (target <= 0) 0f else (amount / target).toFloat().coerceIn(0f, 1f)
        }

    /** A ceiling nutrient that has gone past its target deserves to be called out. */
    val isOverLimit: Boolean
        get() = nutrient.isLimit && consumed != null && target > 0 && consumed > target
}

/**
 * Correction of how much of an already logged food was really eaten.
 *
 * Nutrition scales linearly with the amount, so the preview and the saved result come from the
 * logged entry's own values. No new research runs and the recorded source stays untouched.
 */
enum class LoggedAmountEditError {
    INVALID_AMOUNT,
    INVALID_CORRECTION,
    INTERPRETATION_FAILED,
    ENTRY_GONE,
    SAVE_FAILED,
}

data class LoggedAmountEditUiState(
    val entryId: Long,
    val name: String,
    val originalUnit: String,
    val unit: String,
    val originalAmount: Double,
    val originalCalories: Double,
    val amountText: String,
    val correctionText: String = "",
    val interpretation: String? = null,
    val error: LoggedAmountEditError? = null,
    val isInterpreting: Boolean = false,
    val isSaving: Boolean = false,
) {
    val parsedAmount: Double? get() = parseLoggedAmount(amountText)

    val canSave: Boolean get() = !isSaving && !isInterpreting && parsedAmount != null
    val canInterpret: Boolean get() = correctionText.isNotBlank() && !isSaving && !isInterpreting

    /** Calories the correction would store, so the dialog can show the result before saving. */
    val previewCalories: Double?
        get() {
            val amount = parsedAmount ?: return null
            val factor = PortionChangeValidator.calculateMultiplier(
                originalQuantity = originalAmount,
                originalUnit = originalUnit,
                newQuantity = amount,
                newUnit = unit,
            ) ?: return null
            return originalCalories * factor
        }
}

/**
 * Reads an amount the way it is typed on a German keyboard, where the decimal separator is a
 * comma. Blank, non-numeric, zero, negative, and absurd values are rejected as unusable.
 */
fun parseLoggedAmount(text: String): Double? {
    val amount = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0.0 || amount > MAX_LOGGED_AMOUNT) return null
    return amount
}

private const val MAX_LOGGED_AMOUNT = 100_000.0

data class TodayFoodEntry(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val amountText: String,
    val calories: Double,
    val proteinGrams: Double = 0.0,
    val carbohydrateGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val saturatedFatGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val mealCategory: MealCategory,
    val time: LocalTime,
    val isEstimated: Boolean = false,
    val thumbnailUri: String? = null,
    val foodId: Long? = null,
    val amount: Double = 0.0,
    val unit: String = "g",
    val grams: Double? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    /** Every page the research cited, primary source first. Empty for manual entries. */
    val citedSourceUrls: List<String> = emptyList(),
    /** Provider confidence, 0..1. Null when nothing claimed one, e.g. a manual entry. */
    val confidence: Double? = null,
    /** The product title the cited page printed, which is often not what the entry is called. */
    val sourceProductName: String? = null,
    /** The amount the cited page's values describe, before scaling to the eaten portion. */
    val sourceServingQuantity: Double? = null,
    val sourceServingUnit: String? = null,
    /** Short AI-generated explanation of the main calorie contributors for this entry. */
    val calorieExplanation: String? = null,
    /** Individual logs that make up a combined meal shown as one row on the Today page. */
    val groupItems: List<TodayFoodEntry> = emptyList(),
    /** Briefly present after auto-save so the written sentence can resolve into its short label. */
    val revealText: String? = null,
)

/**
 * Rebuilds the sentence a logged entry came from, so editing it on the page starts from what
 * was written rather than from a stripped-down display name. Amount and brand are included
 * because the re-run research needs them to identify the same food again.
 */
fun TodayFoodEntry.reeditableText(): String = listOfNotNull(
    editableAmount(),
    editableName(),
    editableBrand(),
).joinToString(" ")

/** Separator between a food and its brand on the row, as one string so taps can skip it. */
const val BRAND_SEPARATOR = " · "

/** The words a logged row shows on its first line. */
fun TodayFoodEntry.rowDescription(): String = buildString {
    append(name)
    brand?.takeIf(String::isNotBlank)?.let { append(BRAND_SEPARATOR).append(it) }
}

/**
 * Where the caret belongs in [reeditableText] when [rowDescription] was tapped at [offset].
 *
 * The row reads "food · brand" while the editable sentence reads "amount food brand", so the
 * tapped character is located in the part it belongs to and re-anchored at that part's place
 * in the sentence. Tapping a word therefore opens the line with the caret inside that same
 * word instead of somewhere shifted by the amount that is only shown on the line below.
 */
fun TodayFoodEntry.reeditableCaretForDescription(offset: Int): Int {
    val name = editableName()
    val brand = editableBrand()
    val nameStart = editableAmount()?.let { it.length + 1 } ?: 0
    val brandStart = nameStart + (name?.let { it.length + 1 } ?: 0)
    val displayedNameEnd = this.name.length
    val caret = if (offset <= displayedNameEnd || brand == null) {
        nameStart + offset.coerceIn(0, name?.length ?: 0)
    } else {
        brandStart + (offset - displayedNameEnd - BRAND_SEPARATOR.length).coerceIn(0, brand.length)
    }
    return caret.coerceIn(0, reeditableText().length)
}

/**
 * Where the caret belongs in [reeditableText] when the row's amount line was tapped at
 * [offset]. That line is a formatted quantity rather than the words that were typed, so the
 * tap can only land somewhere inside the written amount at the start of the sentence.
 */
fun TodayFoodEntry.reeditableCaretForAmount(offset: Int): Int =
    offset.coerceIn(0, editableAmount()?.length ?: 0)

private fun TodayFoodEntry.editableAmount(): String? = amountText.trim().takeIf(String::isNotBlank)

private fun TodayFoodEntry.editableName(): String? = name.trim().takeIf(String::isNotBlank)

private fun TodayFoodEntry.editableBrand(): String? = brand?.trim()?.takeIf(String::isNotBlank)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val caloriesConsumed: Double = 0.0,
    val calorieTarget: Double = 2_000.0,
    val protein: MacroProgress = MacroProgress(0.0, 130.0),
    val carbohydrates: MacroProgress = MacroProgress(0.0, 240.0),
    val fat: MacroProgress = MacroProgress(0.0, 65.0),
    /** Empty until the user enables at least one micronutrient in settings. */
    val micronutrients: List<MicronutrientProgress> = emptyList(),
    val entries: List<TodayFoodEntry> = emptyList(),
    val isLoading: Boolean = false,
    val goalsCardStyle: GoalsCardStyle = GoalsCardStyle.BARS,
    /**
     * Active calories Health Connect reported for today, and the steps behind them.
     *
     * Null means Nomi has no figure, which is not the same as zero: Health Connect may be absent,
     * unpermitted, or simply not synced yet. Nothing about burned calories is shown in that case
     * rather than claiming the user did not move. Only today can carry them, because Health
     * Connect is read for the current day and a past date has no matching figure.
     */
    val activeCaloriesKcal: Double? = null,
    val steps: Long? = null,
) {
    val caloriesDifference: Double get() = calorieTarget - caloriesConsumed
    val calorieFraction: Float
        get() = if (calorieTarget <= 0) 0f else (caloriesConsumed / calorieTarget).toFloat().coerceIn(0f, 1f)

    /**
     * Burned calories measured against the same target as intake.
     *
     * Movement has no goal of its own here, so the second bar borrows the calorie target as its
     * scale. That is the only choice that lets the two bars be read against each other: an equal
     * length means an equal number of calories.
     */
    val burnedFraction: Float
        get() {
            val burned = activeCaloriesKcal ?: return 0f
            return if (calorieTarget <= 0) 0f else (burned / calorieTarget).toFloat().coerceIn(0f, 1f)
        }

    fun entriesFor(category: MealCategory): List<TodayFoodEntry> =
        entries.filter { it.mealCategory == category }.sortedBy { it.time }
}
