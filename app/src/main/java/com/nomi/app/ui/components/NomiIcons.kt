package com.nomi.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The two glyphs the calorie pill is built from, drawn for Nomi rather than borrowed.
 *
 * They are a pair and only make sense as one: eaten and burned sit side by side in the same row,
 * so they share a 24dp grid, one solid silhouette each, the same visual weight, and the same
 * rounded ends. A stock icon next to a drawn one would read as two different apps.
 *
 * Both are single-colour shapes with no baked-in fill of their own, which is what lets [Icon]
 * tint them. Every call site passes a colour from the active scheme, so on a device with dynamic
 * colour the pair follows the wallpaper the way the rest of the app does. An emoji could not do
 * that: it carries its own palette and would sit in the row as a foreign object that ignores the
 * theme, light or dark.
 */
object NomiIcons {
    /** Calories eaten. */
    val Flame: ImageVector by lazy(::buildFlame)

    /** Calories burned by moving. */
    val Runner: ImageVector by lazy(::buildRunner)
}

/**
 * A flame carrying its own hollow core.
 *
 * The core is a second contour in the same path rather than a second shape, so an even-odd fill
 * punches it out as a hole. That keeps the whole glyph one colour and one path: the negative
 * space is the drawing, and it survives being tinted, scaled, and put on any background.
 */
private fun buildFlame(): ImageVector = ImageVector.Builder(
    name = "NomiFlame",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ) {
        // Outer silhouette: a pointed tip that widens into a round base.
        moveTo(12f, 1.7f)
        curveTo(12f, 1.7f, 12.9f, 4.7f, 15.1f, 7.3f)
        curveTo(17.7f, 10.4f, 19.6f, 13f, 19.6f, 15.6f)
        curveTo(19.6f, 19.4f, 16.2f, 22.3f, 12f, 22.3f)
        curveTo(7.8f, 22.3f, 4.4f, 19.4f, 4.4f, 15.6f)
        curveTo(4.4f, 13f, 6.3f, 10.4f, 8.9f, 7.3f)
        curveTo(11.1f, 4.7f, 12f, 1.7f, 12f, 1.7f)
        close()
        // Inner core, punched out by the even-odd rule.
        moveTo(12f, 11.3f)
        curveTo(12f, 11.3f, 9.7f, 14.2f, 9.7f, 16.8f)
        curveTo(9.7f, 18.7f, 10.7f, 19.9f, 12f, 19.9f)
        curveTo(13.3f, 19.9f, 14.3f, 18.7f, 14.3f, 16.8f)
        curveTo(14.3f, 14.2f, 12f, 11.3f, 12f, 11.3f)
        close()
    }
}.build()

/**
 * A figure mid-stride, leaning into the direction it is going.
 *
 * The head is a filled circle and the limbs are round-capped strokes of the same weight, which at
 * pill size reads as one solid shape rather than a wire drawing - the same density as the flame
 * beside it. The pose does the work: one leg driving forward, one trailing, arms counter-swung.
 * A standing figure would have read as "steps recorded" instead of "calories burned".
 */
private fun buildRunner(): ImageVector = ImageVector.Builder(
    name = "NomiRunner",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // Head, as four cubics around (15.8, 4.7) with radius 2.1.
        moveTo(15.8f, 2.6f)
        curveTo(16.96f, 2.6f, 17.9f, 3.54f, 17.9f, 4.7f)
        curveTo(17.9f, 5.86f, 16.96f, 6.8f, 15.8f, 6.8f)
        curveTo(14.64f, 6.8f, 13.7f, 5.86f, 13.7f, 4.7f)
        curveTo(13.7f, 3.54f, 14.64f, 2.6f, 15.8f, 2.6f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Torso, leaning forward from shoulder to hip.
        moveTo(14.8f, 8.2f)
        lineTo(11.2f, 12.8f)
        // Leading leg: knee up and forward, shin dropping to the ground.
        moveTo(11.2f, 12.8f)
        lineTo(14.4f, 14.8f)
        lineTo(13.6f, 20.4f)
        // Trailing leg, extended back.
        moveTo(11.2f, 12.8f)
        lineTo(8f, 14.2f)
        lineTo(5.2f, 18.6f)
        // Leading arm, bent forward.
        moveTo(14.6f, 8.7f)
        lineTo(17.8f, 10.3f)
        lineTo(17f, 13f)
        // Trailing arm, swung back.
        moveTo(14.6f, 8.7f)
        lineTo(11f, 8.3f)
        lineTo(9f, 5.9f)
    }
}.build()
