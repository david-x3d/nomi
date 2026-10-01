package com.nomi.app.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** What arrives over a tap is bounded the way Nomi's own entries are. */
class NomiShareImporterTest {

    @Test
    fun `a blank unit and an oversized name are made storable`() {
        val log = NomiShareImporter.logsFor(
            envelope = envelope(food(name = "x".repeat(5_000), unit = "  ", brand = " ")),
            date = LocalDate.of(2026, 9, 28),
            zone = ZoneId.of("UTC"),
            now = 1_000,
        ).single()

        assertEquals("serving", log.unit)
        assertEquals(300, log.displayNameSnapshot.length)
        assertNull(log.brandSnapshot)
    }

    @Test
    fun `an ordinary shared food is kept as sent`() {
        val log = NomiShareImporter.logsFor(
            envelope = envelope(food(name = "Toast", unit = "slices", brand = "Golden")),
            date = LocalDate.of(2026, 9, 28),
            zone = ZoneId.of("UTC"),
            now = 1_000,
        ).single()

        assertEquals("Toast", log.displayNameSnapshot)
        assertEquals("slices", log.unit)
        assertEquals("Golden", log.brandSnapshot)
        assertEquals("2026-09-28", log.localDate)
        assertEquals(1_000L, log.loggedAtEpochMillis)
    }

    private fun envelope(food: ShareFoodV1) = ShareEnvelopeV1(
        sharedAtEpochMillis = 1,
        appVersionName = "2.9.3",
        day = ShareDayV1(date = "2026-09-28", foods = listOf(food)),
    )

    private fun food(name: String, unit: String, brand: String?) = ShareFoodV1(
        name = name, brand = brand, amount = 2.0, unit = unit, meal = "breakfast",
        kcal = 160.0, proteinGrams = 6.0, carbohydrateGrams = 28.0, fatGrams = 2.0,
    )
}
