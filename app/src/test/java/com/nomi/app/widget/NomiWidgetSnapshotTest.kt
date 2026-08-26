package com.nomi.app.widget

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NomiWidgetSnapshotTest {

    private val snapshot = NomiWidgetSnapshot(
        caloriesKcal = 1238.0,
        calorieTargetKcal = 1820.0,
        proteinGrams = 96.4,
        proteinTargetGrams = 130.0,
        carbohydrateGrams = 142.0,
        carbohydrateTargetGrams = 195.0,
        fatGrams = 60.0,
        fatTargetGrams = 56.0,
    )

    @Test
    fun `fractions scale linearly and clamp at the target`() {
        assertEquals(0.6802f, snapshot.calorieFraction()!!, 0.0001f)
        assertEquals(1f, NomiWidgetSnapshot.progressUnits(2000.0, 1820.0)!! / 10_000f, 0.0001f)
        assertEquals(0f, NomiWidgetSnapshot.progressUnits(0.0, 1820.0)!!.toFloat() / 10_000f, 0.0001f)
    }

    @Test
    fun `missing targets produce no fraction instead of dividing by zero`() {
        assertNull(NomiWidgetSnapshot.EMPTY.calorieFraction())
        assertNull(NomiWidgetSnapshot.progressUnits(500.0, null))
        assertNull(NomiWidgetSnapshot.progressUnits(500.0, 0.0))
        assertFalse(NomiWidgetSnapshot.EMPTY.hasPlan)
    }

    @Test
    fun `over-target days are detected without shaming thresholds`() {
        assertFalse(snapshot.isOverCalorieTarget)

        val overTarget = snapshot.copy(caloriesKcal = 2010.0)
        assertTrue(overTarget.isOverCalorieTarget)

        val exactlyAtTarget = snapshot.copy(caloriesKcal = 1820.0)
        assertFalse(exactlyAtTarget.isOverCalorieTarget)
    }

    @Test
    fun `delta is the absolute distance from the target`() {
        assertEquals(
            "582",
            NomiWidgetSnapshot.formatDelta(1238.0, 1820.0, Locale.US),
        )
        assertEquals(
            "190",
            NomiWidgetSnapshot.formatDelta(2010.0, 1820.0, Locale.US),
        )
        assertNull(NomiWidgetSnapshot.formatDelta(900.0, null, Locale.US))
    }

    @Test
    fun `kcal and grams format as grouped whole numbers per locale`() {
        assertEquals("1,238", NomiWidgetSnapshot.formatKcal(1238.4, Locale.US))
        assertEquals("1.238", NomiWidgetSnapshot.formatKcal(1238.4, Locale.GERMANY))
        assertEquals("96", NomiWidgetSnapshot.formatGrams(96.49, Locale.US))
        // Rounds half up rather than truncating.
        assertEquals("97", NomiWidgetSnapshot.formatGrams(96.5, Locale.US))
    }

    @Test
    fun `progress units round to a stable integer scale`() {
        assertEquals(6802, NomiWidgetSnapshot.progressUnits(1238.0, 1820.0))
        assertEquals(7415, NomiWidgetSnapshot.progressUnits(96.4, 130.0))
        // Over-target values never exceed the full bar.
        assertEquals(10_000, NomiWidgetSnapshot.progressUnits(60.0, 56.0))
    }

    @Test
    fun `macro amount text follows the translated template`() {
        val template = "%1\$s / %2\$s g"

        assertEquals(
            "96 / 130 g",
            NomiWidgetViews.macroAmount(96.0, 130.0, template, Locale.US),
        )
        assertNull(NomiWidgetViews.macroAmount(96.0, null, template, Locale.US))
    }
}
