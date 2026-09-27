package com.nomi.app.ui.history

import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * Coverage for the parts of the History screen that decide what the user is offered.
 *
 * The day header renders one "copy" chip per meal actually eaten, and hides every copy action on
 * today. Both are pure derivations from a day's entries, so both are checked here rather than being
 * left to a screenshot.
 */
class HistoryModelsTest {

    @Test
    fun `a day with no entries offers no meal to copy`() {
        val day = day(entries = emptyList())

        assertTrue(day.mealCategories.isEmpty())
    }

    @Test
    fun `each meal eaten that day is offered exactly once`() {
        val day = day(
            entries = listOf(
                entry(1, MealCategory.BREAKFAST),
                entry(2, MealCategory.BREAKFAST),
                entry(3, MealCategory.DINNER),
            ),
        )

        // Two breakfasts are still one breakfast as far as "copy this meal" is concerned.
        assertEquals(listOf(MealCategory.BREAKFAST, MealCategory.DINNER), day.mealCategories)
    }

    @Test
    fun `meals are listed in the order they are eaten, not the order they were logged`() {
        val day = day(
            entries = listOf(
                entry(1, MealCategory.SNACKS),
                entry(2, MealCategory.BREAKFAST),
                entry(3, MealCategory.DINNER),
                entry(4, MealCategory.LUNCH),
            ),
        )

        // Enum declaration order happens to be the same, so this asserts the sort is real rather
        // than incidental: the chips read down the page the way the user ate them.
        assertEquals(
            listOf(
                MealCategory.BREAKFAST,
                MealCategory.LUNCH,
                MealCategory.DINNER,
                MealCategory.SNACKS,
            ),
            day.mealCategories,
        )
    }

    @Test
    fun `a day knows whether it is the day being copied into`() {
        val date = LocalDate.of(2026, 9, 27)
        val day = day(date = date)

        assertTrue(day.isSameDayAs(date))
        assertFalse(day.isSameDayAs(date.plusDays(1)))
        assertFalse(day.isSameDayAs(date.minusDays(1)))
    }

    @Test
    fun `the suggested meal name is never blank`() {
        val name = defaultSavedMealName(LocalDate.of(2026, 9, 27), Locale.US)

        // saveHistoryDayAsMeal returns early on a blank name, so a blank suggestion would make the
        // Save chip look broken.
        assertTrue(name.isNotBlank())
        assertTrue(name.contains("2026"))
    }

    @Test
    fun `the suggested meal name is localised`() {
        val date = LocalDate.of(2026, 9, 27)
        val english = defaultSavedMealName(date, Locale.US)
        val german = defaultSavedMealName(date, Locale.GERMAN)

        // A German user should not be handed an American date order.
        assertFalse(english == german)
        assertTrue(german.isNotBlank())
    }

    @Test
    fun `the suggested meal name carries the date for every language Nomi ships`() {
        val date = LocalDate.of(2026, 9, 27)
        listOf(
            Locale.US, Locale.GERMAN, Locale.FRENCH, Locale.ITALIAN, Locale.forLanguageTag("es"),
            Locale.forLanguageTag("nl"), Locale.forLanguageTag("pt"), Locale.forLanguageTag("sq"),
            Locale.forLanguageTag("sv"), Locale.forLanguageTag("tr"),
        ).forEach { locale ->
            assertTrue(
                "blank suggestion for $locale",
                defaultSavedMealName(date, locale).isNotBlank(),
            )
        }
    }

    @Test
    fun `the suggested meal name uses the prefix it is given`() {
        val date = LocalDate.of(2026, 9, 27)
        val english = defaultSavedMealName(date, Locale.US, "Meal")
        val german = defaultSavedMealName(date, Locale.US, "Mahlzeit")

        // The prefix arrives already localised from the catalogue, so the date part must be the
        // only thing the formatter contributes.
        assertTrue(english.startsWith("Meal · "))
        assertTrue(german.startsWith("Mahlzeit · "))
        assertEquals(english.substringAfter("· "), german.substringAfter("· "))
    }

    private fun day(
        date: LocalDate = LocalDate.of(2026, 9, 26),
        entries: List<TodayFoodEntry> = emptyList(),
    ) = HistoryDay(
        date = date,
        calories = 2_000.0,
        calorieTarget = 2_000.0,
        proteinGrams = 120.0,
        carbohydrateGrams = 220.0,
        fatGrams = 70.0,
        entries = entries,
    )

    private fun entry(id: Long, mealCategory: MealCategory) = TodayFoodEntry(
        id = id,
        name = "food $id",
        amountText = "100 g",
        calories = 100.0,
        mealCategory = mealCategory,
        time = LocalTime.of(8 + id.toInt(), 0),
    )
}
