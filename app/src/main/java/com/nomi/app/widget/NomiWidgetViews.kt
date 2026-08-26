package com.nomi.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import com.nomi.app.MainActivity
import com.nomi.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Builds the RemoteViews for both Nomi widgets. Kept separate from data loading so rendering
 * stays a pure function of a [NomiWidgetSnapshot].
 */
internal object NomiWidgetViews {

    fun small(context: Context, snapshot: NomiWidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_small)
        views.setOnClickPendingIntent(R.id.widget_small_root, openAppPendingIntent(context))
        if (!snapshot.hasPlan) {
            views.showSetupState(contentIds = listOf(R.id.widget_small_content), hintId = R.id.widget_small_hint)
            return views
        }
        val locale = formatLocale()
        views.setViewVisibility(R.id.widget_small_hint, GONE)
        views.setTextViewText(
            R.id.widget_small_value,
            NomiWidgetSnapshot.formatKcal(snapshot.caloriesKcal, locale),
        )
        snapshot.calorieTargetKcal?.let { target ->
            views.setTextViewText(
                R.id.widget_small_target,
                context.getString(R.string.widget_of_target, NomiWidgetSnapshot.formatKcal(target, locale)),
            )
        }
        progressUnits(snapshot.caloriesKcal, snapshot.calorieTargetKcal)?.let { units ->
            views.setProgressBar(R.id.widget_small_bar, NomiWidgetSnapshot.PROGRESS_SCALE, units, false)
        }
        deltaText(context, snapshot)?.let { views.setTextViewText(R.id.widget_small_delta, it) }
        return views
    }

    fun large(context: Context, snapshot: NomiWidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_large)
        views.setOnClickPendingIntent(R.id.widget_large_root, openAppPendingIntent(context))
        if (!snapshot.hasPlan) {
            views.showSetupState(
                contentIds = listOf(R.id.widget_large_macros, R.id.widget_large_delta),
                hintId = R.id.widget_large_hint,
            )
            views.setTextViewText(R.id.widget_large_date, "")
            return views
        }
        val locale = formatLocale()
        val gramTemplate = context.getString(R.string.widget_macro_grams)
        views.setViewVisibility(R.id.widget_large_hint, GONE)
        views.setViewVisibility(R.id.widget_large_macros, VISIBLE)
        views.setViewVisibility(R.id.widget_large_delta, VISIBLE)
        views.setTextViewText(R.id.widget_large_date, dateLabel())
        views.setTextViewText(
            R.id.widget_large_value,
            NomiWidgetSnapshot.formatKcal(snapshot.caloriesKcal, locale),
        )
        snapshot.calorieTargetKcal?.let { target ->
            views.setTextViewText(
                R.id.widget_large_target,
                context.getString(R.string.widget_of_target, NomiWidgetSnapshot.formatKcal(target, locale)),
            )
        }
        deltaText(context, snapshot)?.let { views.setTextViewText(R.id.widget_large_delta, it) }

        views.bindMacro(gramTemplate, R.id.widget_large_protein_amount, R.id.widget_large_protein_bar, snapshot.proteinGrams, snapshot.proteinTargetGrams)
        views.bindMacro(gramTemplate, R.id.widget_large_carb_amount, R.id.widget_large_carb_bar, snapshot.carbohydrateGrams, snapshot.carbohydrateTargetGrams)
        views.bindMacro(gramTemplate, R.id.widget_large_fat_amount, R.id.widget_large_fat_bar, snapshot.fatGrams, snapshot.fatTargetGrams)
        return views
    }

    private fun RemoteViews.bindMacro(
        gramTemplate: String,
        amountId: Int,
        barId: Int,
        grams: Double,
        target: Double?,
    ) {
        macroAmount(grams, target, gramTemplate)?.let { setTextViewText(amountId, it) }
        progressUnits(grams, target)?.let { units ->
            setProgressBar(barId, NomiWidgetSnapshot.PROGRESS_SCALE, units, false)
        }
    }

    /** "96 / 130 g" built from the translated pattern so word order follows each language. */
    internal fun macroAmount(
        grams: Double,
        target: Double?,
        gramTemplate: String,
        locale: Locale = Locale.getDefault(),
    ): String? {
        if (target == null) return null
        return String.format(
            locale,
            gramTemplate,
            NomiWidgetSnapshot.formatGrams(grams, locale),
            NomiWidgetSnapshot.formatGrams(target, locale),
        )
    }

    /**
     * "580 left" or "190 over target". Over-target days keep the calm accent color on purpose;
     * Nomi never turns exceeding the goal into an alarm.
     */
    internal fun deltaText(context: Context, snapshot: NomiWidgetSnapshot): String? {
        val delta = NomiWidgetSnapshot.formatDelta(
            snapshot.caloriesKcal,
            snapshot.calorieTargetKcal,
            formatLocale(),
        ) ?: return null
        return if (snapshot.isOverCalorieTarget) {
            context.getString(R.string.widget_kcal_over, delta)
        } else {
            context.getString(R.string.widget_kcal_left, delta)
        }
    }

    internal fun dateLabel(): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(formatLocale())
            .format(LocalDate.now())

    internal fun progressUnits(value: Double, target: Double?): Int? =
        NomiWidgetSnapshot.progressUnits(value, target)

    private fun RemoteViews.showSetupState(contentIds: List<Int>, hintId: Int) {
        contentIds.forEach { setViewVisibility(it, GONE) }
        setViewVisibility(hintId, VISIBLE)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatLocale(): Locale = Locale.getDefault(Locale.Category.FORMAT)
}
