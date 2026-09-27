package com.nomi.app.ui.history

import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Coverage for History's selection mode.
 *
 * Everything a selection decides happens in these pure functions: which row ids are picked, which
 * log rows they stand for, and what the confirm button says. The composable above them only draws
 * it, so a selection that is wrong here is wrong on the device, and a selection that is right here
 * cannot be broken by layout.
 */
class HistorySelectionTest {

    private val date = LocalDate.of(2026, 9, 26)

    @Test
    fun `a new selection starts empty`() {
        val selection = HistorySelection(date, HistorySelectionAction.SAVE_MEAL)

        // A picker that arrives with things already ticked would copy or save a plate the user
        // never looked at.
        assertTrue(selection.isEmpty)
        assertEquals(0, selection.count)
        assertFalse(selection.isSelected(1L))
    }

    @Test
    fun `one tap selects a single food`() {
        val selection = HistorySelection(date, HistorySelectionAction.SAVE_MEAL)
            .toggled(1L)

        // The whole requirement in one line: a single wanted item costs a single tap on its row.
        assertEquals(setOf(1L), selection.selectedRowIds)
        assertTrue(selection.isSelected(1L))
        assertEquals(1, selection.count)
        assertFalse(selection.isEmpty)
    }

    @Test
    fun `tapping the same row again deselects it`() {
        val selection = HistorySelection(date, HistorySelectionAction.SAVE_MEAL)
            .toggled(1L)
            .toggled(1L)

        assertTrue(selection.isEmpty)
        assertEquals(setOf<Long>(), selection.selectedRowIds)
    }

    @Test
    fun `several foods can be selected and deselected independently`() {
        var selection = HistorySelection(date, HistorySelectionAction.SAVE_MEAL)
        listOf(1L, 2L, 3L).forEach { selection = selection.toggled(it) }

        assertEquals(setOf(1L, 2L, 3L), selection.selectedRowIds)
        assertEquals(3, selection.count)

        selection = selection.toggled(2L)

        assertEquals(setOf(1L, 3L), selection.selectedRowIds)
        assertFalse(selection.isSelected(2L))
        assertTrue(selection.isSelected(1L))
        assertTrue(selection.isSelected(3L))
    }

    @Test
    fun `selecting on one day does not select on another`() {
        val other = HistorySelection(date.plusDays(1), HistorySelectionAction.ADD_TO_TODAY)
            .toggled(9L)

        // Selection is scoped to the day it was started on, which is the unit both actions operate
        // on, so a tap on a neighbouring day is not silently merged into it.
        assertEquals(date.plusDays(1), other.day)
        assertEquals(setOf(9L), other.selectedRowIds)
    }

    @Test
    fun `only the selected rows contribute log rows`() {
        val day = day(
            entry(1, "oats"),
            entry(2, "yoghurt"),
            entry(3, "apple"),
        )

        val selected = day.logIdsFor(setOf(1L, 3L))

        // This is the whole point of the picker: the food in the middle is not saved or copied.
        assertEquals(listOf(1L, 3L), selected)
    }

    @Test
    fun `an empty selection copies nothing`() {
        val day = day(entry(1, "oats"), entry(2, "yoghurt"))

        assertEquals(emptyList<Long>(), day.logIdsFor(emptySet()))
    }

    @Test
    fun `a single logged food stands for its own log row`() {
        val day = day(entry(7, "rice"))

        assertEquals(listOf(7L), day.logIdsFor(setOf(7L)))
        assertEquals(listOf(7L), day.entries.single().logIdsForSelection())
    }

    @Test
    fun `a combined meal stands for every product that made it up`() {
        // A menu scanned as one meal is one row on the screen, so tapping that row has to pick the
        // whole meal - saving half of a meal nobody ate as a meal would be a different dish.
        val group = listOf(entry(11, "chicken"), entry(12, "rice"), entry(13, "salad"))
        val day = day(groupedMeal = group)

        val selected = day.logIdsFor(setOf(day.entries.single().id))

        assertEquals(listOf(11L, 12L, 13L), selected)
    }

    @Test
    fun `a combined meal and a single food can be selected side by side`() {
        val day = day(entry(1, "oats"), groupedMeal = listOf(entry(21, "chicken"), entry(22, "rice")))
        val groupId = day.entries.last().id

        val selected = day.logIdsFor(setOf(1L, groupId))

        assertEquals(listOf(1L, 21L, 22L), selected)
    }

    @Test
    fun `rows outside the selection are ignored even if they exist on the day`() {
        val day = day(entry(1, "oats"), entry(2, "apple"))
        val selection = HistorySelection(date, HistorySelectionAction.ADD_TO_TODAY).toggled(1L)

        assertEquals(listOf(1L), day.logIdsFor(selection.selectedRowIds))
    }

    @Test
    fun `the confirm label counts what is selected, in the singular`() {
        assertEquals(
            "Save {0} item",
            HistorySelectionAction.SAVE_MEAL.confirmLabelKey(1),
        )
        assertEquals(
            "Add {0} item to today",
            HistorySelectionAction.ADD_TO_TODAY.confirmLabelKey(1),
        )
    }

    @Test
    fun `the confirm label counts what is selected, in the plural`() {
        assertEquals(
            "Save {0} items",
            HistorySelectionAction.SAVE_MEAL.confirmLabelKey(4),
        )
        assertEquals(
            "Add {0} items to today",
            HistorySelectionAction.ADD_TO_TODAY.confirmLabelKey(4),
        )
    }

    @Test
    fun `both actions name themselves in the bar and in the hint`() {
        listOf(HistorySelectionAction.SAVE_MEAL, HistorySelectionAction.ADD_TO_TODAY).forEach { action ->
            assertNotNull(action.titleKey())
            assertNotNull(action.hintKey())
            assertNotNull(action.emptyConfirmLabelKey())
            // Two actions, one picker: their titles and hints must not collide, or the bar would
            // not say which one is open.
            assertFalse(action.titleKey() == HistorySelectionAction.entries.first { it != action }.titleKey())
        }
    }

    @Test
    fun `every string a selection can raise is in the catalogue`() {
        // These keys are returned as values, so the coverage scanner - which reads call sites -
        // cannot see them. Without this they could render as English in nine languages and nothing
        // would fail.
        val keys = buildList {
            HistorySelectionAction.entries.forEach { action ->
                add(action.titleKey())
                add(action.hintKey())
                add(action.emptyConfirmLabelKey())
                add(action.confirmLabelKey(1))
                add(action.confirmLabelKey(2))
            }
        }.distinct()
        NomiLanguage.entries.filter { it != NomiLanguage.ENGLISH }.forEach { language ->
            keys.forEach { key ->
                val value = NomiTranslations.catalogue[key]?.forLanguage(language)
                assertTrue(
                    "$key has no $language translation",
                    !value.isNullOrBlank(),
                )
            }
        }
    }

    private fun day(vararg entries: TodayFoodEntry, groupedMeal: List<TodayFoodEntry> = emptyList()) =
        HistoryDay(
            date = date,
            calories = 2_000.0,
            calorieTarget = 2_000.0,
            proteinGrams = 120.0,
            carbohydrateGrams = 220.0,
            fatGrams = 70.0,
            entries = entries.toList() + if (groupedMeal.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    TodayFoodEntry(
                        id = groupedMeal.first().id,
                        name = "Chicken and rice",
                        amountText = "",
                        calories = groupedMeal.sumOf(TodayFoodEntry::calories),
                        mealCategory = MealCategory.DINNER,
                        time = LocalTime.of(19, 0),
                        groupItems = groupedMeal,
                    ),
                )
            },
        )

    private fun entry(id: Long, name: String) = TodayFoodEntry(
        id = id,
        name = name,
        amountText = "100 g",
        calories = 100.0,
        mealCategory = MealCategory.BREAKFAST,
        time = LocalTime.of(8, 0),
    )
}
