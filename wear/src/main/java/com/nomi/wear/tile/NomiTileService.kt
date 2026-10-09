package com.nomi.wear.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import com.nomi.wear.MainActivity
import com.nomi.wear.PhoneConnection
import com.nomi.wear.R
import com.nomi.wear.WatchLocale
import com.nomi.wear.WatchToday
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * Today's calories at a swipe from the watch face: a ring that fills with the day's food, what is
 * left, and a button that goes straight to dictating a meal.
 */
class NomiTileService : Material3TileService(allowDynamicTheme = false, defaultColorScheme = NomiTileColors) {

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val context = WatchLocale.wrap(this@NomiTileService)
        val today = PhoneConnection(this@NomiTileService).loadToday()?.asOf(LocalDate.now())
        val layout = primaryLayout(
            titleSlot = { text(context.getString(R.string.today).layoutString) },
            mainSlot = {
                if (today == null || !today.hasProfile) {
                    text(
                        context.getString(if (today == null) R.string.not_connected else R.string.not_set_up).layoutString,
                        typography = Typography.BODY_MEDIUM,
                        maxLines = 3,
                    )
                } else {
                    calorieRing(today, context)
                }
            },
            bottomSlot = {
                textEdgeButton(onClick = clickable(openApp(startVoice = today?.hasProfile == true))) {
                    text(context.getString(if (today?.hasProfile == true) R.string.log_short else R.string.open).layoutString)
                }
            },
            onClick = clickable(openApp(startVoice = false)),
        )
        return tile(
            timeline = TimelineBuilders.Timeline.fromLayoutElement(layout),
            // The phone pushes every change, so this only bounds how long a missed push lingers.
            freshness = 30.minutes,
        )
    }

    private fun MaterialScope.calorieRing(today: WatchToday, context: android.content.Context): LayoutElement {
        val remaining = today.remainingKcal
        val number = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
            .format(remaining?.let(::abs) ?: today.caloriesKcal.roundToInt())
        val label = context.getString(
            when {
                remaining == null -> R.string.kcal_eaten
                remaining < 0 -> R.string.kcal_over
                else -> R.string.kcal_left
            },
        )
        return box(
            circularProgressIndicator(staticProgress = today.calorieProgress),
            column(
                text(number.layoutString, typography = Typography.NUMERAL_MEDIUM),
                text(label.layoutString, typography = Typography.LABEL_SMALL),
                horizontalAlignment = LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER,
            ),
            horizontalAlignment = LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER,
            verticalAlignment = LayoutElementBuilders.VERTICAL_ALIGN_CENTER,
        )
    }

    private fun openApp(startVoice: Boolean): ActionBuilders.LaunchAction = ActionBuilders.launchAction(
        ComponentName(this, MainActivity::class.java),
        mapOf(MainActivity.EXTRA_START_VOICE to ActionBuilders.booleanExtra(startVoice)),
    )

    private companion object {
        /** The watch app's palette, so the tile and the app look like one thing. */
        val NomiTileColors = ColorScheme(
            primary = 0xFFFFB68C.toInt().argb,
            primaryDim = 0xFFE89A6E.toInt().argb,
            primaryContainer = 0xFF7E3000.toInt().argb,
            onPrimary = 0xFF582000.toInt().argb,
            onPrimaryContainer = 0xFFFFDBC9.toInt().argb,
            secondary = 0xFFA1D0C6.toInt().argb,
            secondaryContainer = 0xFF1F514A.toInt().argb,
            onSecondary = 0xFF003731.toInt().argb,
            onSecondaryContainer = 0xFFBCECE1.toInt().argb,
            surfaceContainer = 0xFF261E1B.toInt().argb,
            onSurface = 0xFFF2DFD9.toInt().argb,
            onSurfaceVariant = 0xFFD8C2BA.toInt().argb,
            background = 0xFF000000.toInt().argb,
            onBackground = 0xFFF2DFD9.toInt().argb,
        )
    }
}
