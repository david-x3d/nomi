package com.nomi.wear

import com.google.android.gms.wearable.DataMap
import java.time.LocalDate
import kotlin.math.roundToInt

/** One food or saved meal the watch can log with a tap. */
data class QuickItem(
    val kind: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    val caloriesKcal: Double,
) {
    /** What the phone expects on [WearContract.LOG_QUICK_PATH]. */
    val request: String get() = "$kind:$id"
}

/**
 * The phone's day as the watch shows it. Targets are null while the phone has no plan.
 *
 * A copy from an earlier date is read as an empty day with the same targets: the phone only
 * republishes when something changes, and an untouched phone overnight would otherwise leave
 * yesterday's total on the wrist all morning.
 */
data class WatchToday(
    val date: LocalDate,
    val hasProfile: Boolean,
    val languageTag: String,
    val caloriesKcal: Double,
    val calorieTargetKcal: Double?,
    val proteinGrams: Double,
    val proteinTargetGrams: Double?,
    val carbohydrateGrams: Double,
    val carbohydrateTargetGrams: Double?,
    val fatGrams: Double,
    val fatTargetGrams: Double?,
    val quickItems: List<QuickItem>,
) {
    fun asOf(today: LocalDate): WatchToday =
        if (date == today) this
        else copy(date = today, caloriesKcal = 0.0, proteinGrams = 0.0, carbohydrateGrams = 0.0, fatGrams = 0.0)

    /** Whole kilocalories left today; negative once over. Null without a target. */
    val remainingKcal: Int?
        get() = calorieTargetKcal?.let { (it - caloriesKcal).roundToInt() }

    val calorieProgress: Float get() = progress(caloriesKcal, calorieTargetKcal)

    companion object {
        fun progress(value: Double, target: Double?): Float {
            if (target == null || target <= 0.0) return 0f
            return (value / target).toFloat().coerceIn(0f, 1f)
        }

        fun fromDataMap(map: DataMap): WatchToday? {
            val date = runCatching { LocalDate.parse(map.getString(WearContract.KEY_DATE)) }.getOrNull()
                ?: return null
            return WatchToday(
                date = date,
                hasProfile = map.getBoolean(WearContract.KEY_HAS_PROFILE),
                languageTag = map.getString(WearContract.KEY_LANGUAGE).orEmpty(),
                caloriesKcal = map.getDouble(WearContract.KEY_CALORIES),
                calorieTargetKcal = map.target(WearContract.KEY_CALORIE_TARGET),
                proteinGrams = map.getDouble(WearContract.KEY_PROTEIN),
                proteinTargetGrams = map.target(WearContract.KEY_PROTEIN_TARGET),
                carbohydrateGrams = map.getDouble(WearContract.KEY_CARBS),
                carbohydrateTargetGrams = map.target(WearContract.KEY_CARBS_TARGET),
                fatGrams = map.getDouble(WearContract.KEY_FAT),
                fatTargetGrams = map.target(WearContract.KEY_FAT_TARGET),
                quickItems = map.getDataMapArrayList(WearContract.KEY_QUICK).orEmpty().mapNotNull { item ->
                    QuickItem(
                        kind = item.getString(WearContract.KEY_QUICK_KIND) ?: return@mapNotNull null,
                        id = item.getLong(WearContract.KEY_QUICK_ID),
                        title = item.getString(WearContract.KEY_QUICK_TITLE).orEmpty(),
                        subtitle = item.getString(WearContract.KEY_QUICK_SUBTITLE).orEmpty(),
                        caloriesKcal = item.getDouble(WearContract.KEY_QUICK_CALORIES),
                    )
                },
            )
        }

        private fun DataMap.target(key: String): Double? =
            getDouble(key, WearContract.NO_TARGET).takeIf { it > 0.0 }
    }
}
