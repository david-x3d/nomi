package com.nomi.app.ui.app

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal val testLogDate: LocalDate = LocalDate.of(2026, 10, 5)
internal val testLogDestination = LogDestination(testLogDate, ZoneId.of("Europe/Berlin"))

internal fun testFoodLog(id: Long = 0, group: String? = null) = FoodLogEntity(
    id = id, entryGroupId = group, mealCategory = "lunch", displayNameSnapshot = "Apple",
    amount = 100.0, unit = "g", grams = 100.0,
    nutritionSnapshot = NutritionValues(100.0, 1.0, 20.0, 2.0),
    sourceSnapshot = NutritionSourceSnapshot(displayName = "Test"),
    inputMethod = "manual", localDate = testLogDate.toString(),
    loggedAtEpochMillis = 1_000, zoneId = "Europe/Berlin",
    createdAtEpochMillis = 1_000, updatedAtEpochMillis = 1_000,
)

internal fun testTodayEntry(id: Long = 7) = TodayFoodEntry(
    id = id, name = "Apple", amountText = "100 g", calories = 100.0,
    mealCategory = MealCategory.LUNCH, time = LocalTime.NOON,
)

internal fun testAnalyzedItem(name: String = "Apple") = AnalyzedFoodItem(
    name = name, quantity = 100.0, unit = "g", gramsEquivalent = 100.0,
    calories = 100.0, proteinGrams = 1.0, carbohydrateGrams = 20.0, fatGrams = 2.0,
    isEstimate = true,
)
