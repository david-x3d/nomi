package com.nomi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Round-trip and boundary coverage for the unit conversion that was missing entirely.
 *
 * The defect this exists for: `weightUnit` was written by Settings, echoed back by Settings and
 * carried in every backup, but nothing read it. The weight field still read "kg" while an
 * Imperial user typed pounds, so a real 180 lb weigh-in was persisted as 180 kg - and that number
 * then feeds the Mifflin-St Jeor BMR, the calorie target and the step estimate. Storage stays
 * metric; only the boundary converts.
 */
class UnitFormatterTest {

    // ---- the exact case the audit named ----

    @Test
    fun `an Imperial user entering 180 lb does not store 180 kg`() {
        val stored = UnitFormatter.parseWeightToKilograms("180", metric = false)
        assertEquals(81.6466, stored ?: 0.0, 1e-3)
        // The whole point: nowhere near 180.
        assertTrue((stored ?: 0.0) < 100.0)
        assertTrue(UnitFormatter.isPlausibleWeightKilograms(stored ?: 0.0))
    }

    @Test
    fun `a Metric user entering 82 kg stores 82 kg`() {
        assertEquals(82.0, UnitFormatter.parseWeightToKilograms("82", metric = true) ?: 0.0, 1e-9)
    }

    // ---- round trips, both systems ----

    @Test
    fun `metric weight round-trips through the Imperial field`() {
        listOf(45.0, 62.5, 81.6466, 120.0, 180.0).forEach { kilograms ->
            val shown = UnitFormatter.formatNumber(
                UnitFormatter.weightValue(kilograms, metric = false, locale = Locale.US),
                metric = false,
                locale = Locale.US,
            )
            val typed = trim(shown)
            val restored = UnitFormatter.parseWeightToKilograms(typed, metric = false)
            assertEquals(kilograms, restored ?: 0.0, 0.02)
        }
    }

    @Test
    fun `Imperial weight round-trips through the metric field`() {
        listOf(100.0, 180.0, 250.5, 310.0).forEach { pounds ->
            val kilograms = UnitFormatter.poundsToKilograms(pounds)
            val typed = trim(UnitFormatter.formatNumber(kilograms, metric = true, locale = Locale.US))
            // The metric field shows one decimal, so a value can come back up to half of that
            // away from the original. The tolerance has to allow for the rounding the display
            // does on purpose, or it asserts a precision the field never carried.
            assertEquals(kilograms, UnitFormatter.parseWeightToKilograms(typed, metric = true) ?: 0.0, 0.05)
        }
    }

    @Test
    fun `a comma decimal is accepted in both systems`() {
        assertEquals(1.5, UnitFormatter.parseWeightToKilograms("1,5", metric = true) ?: 0.0, 1e-9)
        // "181,5" lb is a real weight a scale would report.
        val pounds = UnitFormatter.parseWeightToKilograms("181,5", metric = false)
        assertEquals(82.327, pounds ?: 0.0, 1e-2)
    }

    @Test
    fun `an unparseable weight yields null rather than a wrong number`() {
        listOf("", "  ", "abc", "-5", "0").forEach { bad ->
            assertNull("accepted \"$bad\"", UnitFormatter.parseWeightToKilograms(bad, metric = true))
        }
    }

    @Test
    fun `a plausible weight is a real human weight`() {
        assertTrue(UnitFormatter.isPlausibleWeightKilograms(20.0))
        assertTrue(UnitFormatter.isPlausibleWeightKilograms(500.0))
        assertTrue(!UnitFormatter.isPlausibleWeightKilograms(19.9))
        assertTrue(!UnitFormatter.isPlausibleWeightKilograms(500.1))
        // 180 kg is plausible, which is exactly why a mis-unit entry needs the unit to be right
        // rather than relying on a range check to catch it.
        assertTrue(UnitFormatter.isPlausibleWeightKilograms(180.0))
    }

    // ---- height ----

    @Test
    fun `height round-trips in both systems`() {
        listOf(150.0, 180.0, 200.0).forEach { centimetres ->
            val inches = trim(UnitFormatter.formatNumber(UnitFormatter.centimetersToInches(centimetres), metric = true, locale = Locale.US))
            val restored = UnitFormatter.parseHeightToCentimeters(inches, metric = false)
            assertEquals(centimetres, restored ?: 0.0, 0.3)
        }
    }

    @Test
    fun `feet and inches parse and are rejected when impossible`() {
        assertEquals(
            (5 * 12.0 + 7.0) * 2.54,
            UnitFormatter.parseFeetInchesToCentimeters("5", "7") ?: 0.0,
            1e-6,
        )
        assertEquals(12 * 2.54, UnitFormatter.parseFeetInchesToCentimeters("1", "0") ?: 0.0, 1e-6)
        // Inches must be under twelve, feet under ten, and both must be numbers.
        assertNull(UnitFormatter.parseFeetInchesToCentimeters("5", "13"))
        assertNull(UnitFormatter.parseFeetInchesToCentimeters("11", "0"))
        assertNull(UnitFormatter.parseFeetInchesToCentimeters("x", "7"))
        assertNull(UnitFormatter.parseFeetInchesToCentimeters("", ""))
    }

    @Test
    fun `centimetres decompose back into feet and inches`() {
        val (feet, inches) = UnitFormatter.centimetersToFeetAndInches(180.0)
        assertEquals(5, feet)
        assertEquals(10.9, inches, 0.2)
    }

    // ---- display ----

    @Test
    fun `a weight is displayed in the unit the reader uses`() {
        // 82.5 kg and 181.88 lb - the same person, read at the precision each system stores:
        // a tenth of a kilogram and a hundredth of a pound.
        assertEquals("82.5 kg", UnitFormatter.formatWeight(82.5, metric = true, Locale.US))
        assertEquals("181.88 lb", UnitFormatter.formatWeight(82.5, metric = false, Locale.US))
    }

    @Test
    fun `rounding to the displayed decimal is half-up on the decimal value`() {
        // Deliberately not 82.55: a double cannot hold it, and it is really 82.54999..., so
        // HALF_UP correctly leaves it at 82.5. 82.56 and 82.54 are the honest boundary probes.
        assertEquals("82.6 kg", UnitFormatter.formatWeight(82.56, metric = true, Locale.US))
        assertEquals("82.5 kg", UnitFormatter.formatWeight(82.54, metric = true, Locale.US))
    }

    @Test
    fun `a German reader sees a comma decimal and German grouping`() {
        val german = UnitFormatter.formatWeight(1234.5, metric = true, Locale.GERMANY)
        assertTrue(german, german.contains(","))
        assertTrue(german, german.contains("."))
    }

    @Test
    fun `a weight always shows the precision its unit is stored in`() {
        // Not "82 kg": metric weight is stored to the tenth of a kilogram and is shown that way,
        // which is what a real scale reports. Trailing zeros are not noise to be trimmed here -
        // dropping them would make 82.0 kg and 82.04 kg look like the same reading.
        assertEquals("82.0 kg", UnitFormatter.formatWeight(82.0, metric = true, Locale.US))
        assertEquals("181.88 lb", UnitFormatter.formatWeight(82.5, metric = false, Locale.US))
    }

    @Test
    fun `the conversion factors match the reference values`() {
        assertEquals(2.2046226218487757, UnitFormatter.POUNDS_PER_KILOGRAM, 1e-15)
        assertEquals(2.54, UnitFormatter.CENTIMETERS_PER_INCH, 1e-12)
    }

    /** Drops a decimal separator so a formatted value can be typed back into a field. */
    private fun trim(formatted: String): String = formatted.trim()
}
