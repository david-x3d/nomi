package com.nomi.app.ui.app

import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Picks the site shown under Nutrition source when no single page published the values.
 *
 * It stands in for a source without being one, so getting it wrong points the reader at a site
 * the research barely touched.
 */
class MostConsultedSiteTest {

    @Test
    fun `the host behind the most pages wins`() {
        val entry = entryWith(
            "https://www.rewe.de/produkte/riegel",
            "https://fddb.info/db/de/riegel/index.html",
            "https://www.rewe.de/produkte/riegel-nutrition",
            "https://www.rewe.de/produkte/riegel-serving",
        )

        assertEquals("https://www.rewe.de/produkte/riegel", entry.mostConsultedUrl())
    }

    @Test
    fun `a tie goes to the page the provider reported first`() {
        val entry = entryWith(
            "https://fddb.info/a",
            "https://www.dm.de/a",
            "https://fddb.info/b",
            "https://www.dm.de/b",
        )

        assertEquals("https://fddb.info/a", entry.mostConsultedUrl())
    }

    @Test
    fun `pages that fail hostname validation are ignored entirely`() {
        val entry = entryWith(
            "http://insecure.example.com/a",
            "https://localhost/a",
            "not a url",
            "https://fddb.info/only-valid",
        )

        assertEquals("https://fddb.info/only-valid", entry.mostConsultedUrl())
    }

    @Test
    fun `an entry with nothing usable has no leading site`() {
        assertNull(entryWith().mostConsultedUrl())
        assertNull(entryWith("not a url", "ftp://files.example.org/x").mostConsultedUrl())
    }

    @Test
    fun `www is folded so one site does not look like two`() {
        val entry = entryWith(
            "https://www.rewe.de/a",
            "https://rewe.de/b",
            "https://fddb.info/c",
        )

        assertEquals("https://www.rewe.de/a", entry.mostConsultedUrl())
    }

    private fun entryWith(vararg urls: String) = TodayFoodEntry(
        id = 1,
        name = "Proteinriegel",
        amountText = "1 Stück",
        calories = 217.0,
        mealCategory = MealCategory.SNACKS,
        time = LocalTime.NOON,
        isEstimated = true,
        citedSourceUrls = urls.toList(),
    )
}
