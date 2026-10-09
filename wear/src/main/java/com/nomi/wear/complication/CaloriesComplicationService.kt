package com.nomi.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.nomi.wear.MainActivity
import com.nomi.wear.PhoneConnection
import com.nomi.wear.R
import com.nomi.wear.WatchLocale
import com.nomi.wear.WatchToday
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calories left today on the watch face: a number for short slots, and an arc that fills as the
 * day's food is logged for ranged ones. Tapping it opens Nomi.
 */
class CaloriesComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val today = PhoneConnection(this).loadToday()?.asOf(LocalDate.now())
            ?.takeIf { it.hasProfile }
            ?: return NoDataComplicationData()
        return build(WatchLocale.wrap(this), request.complicationType, today)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(WatchLocale.wrap(this), type, PREVIEW)

    private fun build(context: Context, type: ComplicationType, today: WatchToday): ComplicationData? {
        val locale = context.resources.configuration.locales[0]
        val remaining = today.remainingKcal
        val number = NumberFormat.getIntegerInstance(locale)
            .format(remaining?.let(::abs) ?: today.caloriesKcal.roundToInt())
        val unit = context.getString(
            when {
                remaining == null -> R.string.kcal_eaten
                remaining < 0 -> R.string.kcal_over
                else -> R.string.kcal_left
            },
        )
        val description = PlainComplicationText.Builder("$number $unit").build()
        val icon = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_nomi_monochrome)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(number).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder(context.getString(R.string.kcal_short)).build())
                .setMonochromaticImage(icon)
                .setTapAction(openApp(context))
                .build()
            ComplicationType.RANGED_VALUE -> {
                val target = today.calorieTargetKcal?.toFloat() ?: return null
                RangedValueComplicationData.Builder(
                    value = today.caloriesKcal.toFloat().coerceIn(0f, target),
                    min = 0f,
                    max = target,
                    contentDescription = description,
                )
                    .setText(PlainComplicationText.Builder(number).build())
                    .setMonochromaticImage(icon)
                    .setTapAction(openApp(context))
                    .build()
            }
            else -> null
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        val PREVIEW = WatchToday(
            date = LocalDate.now(),
            hasProfile = true,
            languageTag = "",
            caloriesKcal = 1_240.0,
            calorieTargetKcal = 2_100.0,
            proteinGrams = 82.0,
            proteinTargetGrams = 140.0,
            carbohydrateGrams = 130.0,
            carbohydrateTargetGrams = 230.0,
            fatGrams = 44.0,
            fatTargetGrams = 70.0,
            quickItems = emptyList(),
        )
    }
}
