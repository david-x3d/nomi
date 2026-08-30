package com.nomi.app.data.export

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NomiDiaryExportServiceTest {
    @Test
    fun emptyLogProducesEmptyDays() {
        val envelope = diaryFromLogs(emptyList(), exportedAtEpochMillis = 1, appVersionName = "2.1")

        assertEquals(DiaryEnvelopeV1.FORMAT, envelope.format)
        assertEquals(1, envelope.schemaVersion)
        assertEquals(emptyList<DiaryDayV1>(), envelope.days)
    }

    @Test
    fun groupsFoodsByDateAndSumsTheDay() {
        val envelope = diaryFromLogs(
            logs = listOf(
                foodLog(
                    id = 2,
                    date = "2026-08-30",
                    name = "Apfel",
                    brand = "Pink Lady",
                    amount = 180.0,
                    unit = "g",
                    meal = "snack",
                    kcal = 94.0,
                    protein = 0.5,
                    carbs = 25.0,
                    loggedAt = 20,
                ),
                foodLog(
                    id = 1,
                    date = "2026-08-29",
                    name = "Rührei",
                    amount = 2.0,
                    unit = "piece",
                    meal = "breakfast",
                    kcal = 180.0,
                    protein = 13.0,
                    carbs = 1.5,
                    loggedAt = 10,
                ),
                foodLog(
                    id = 3,
                    date = "2026-08-30",
                    name = "Skyr",
                    brand = "Arla",
                    amount = 250.0,
                    unit = "g",
                    meal = "lunch",
                    kcal = 160.0,
                    protein = 28.0,
                    carbs = 8.0,
                    loggedAt = 30,
                ),
            ),
            exportedAtEpochMillis = 99,
            appVersionName = "2.1-test",
        )

        assertEquals("2.1-test", envelope.appVersionName)
        assertEquals(listOf("2026-08-29", "2026-08-30"), envelope.days.map { it.date })

        val first = envelope.days[0]
        assertEquals(listOf("Rührei"), first.foods.map { it.name })
        assertEquals(180.0, first.kcal, 0.0)
        assertEquals(13.0, first.proteinGrams, 0.0)
        assertEquals(1.5, first.carbohydrateGrams, 0.0)

        val second = envelope.days[1]
        assertEquals(listOf("Apfel", "Skyr"), second.foods.map { it.name })
        assertEquals("Pink Lady", second.foods[0].brand)
        assertEquals(180.0, second.foods[0].amount, 0.0)
        assertEquals("g", second.foods[0].unit)
        assertEquals("snack", second.foods[0].meal)
        assertEquals(94.0, second.foods[0].kcal, 0.0)
        assertEquals(254.0, second.kcal, 0.0)
        assertEquals(28.5, second.proteinGrams, 0.0)
        assertEquals(33.0, second.carbohydrateGrams, 0.0)
    }

    @Test
    fun jsonRoundTripKeepsDayTotalsAndFoods() {
        val original = diaryFromLogs(
            logs = listOf(
                foodLog(
                    id = 1,
                    date = "2026-08-30",
                    name = "Haferflocken",
                    amount = 60.0,
                    unit = "g",
                    meal = "breakfast",
                    kcal = 222.0,
                    protein = 8.1,
                    carbs = 35.4,
                ),
            ),
            exportedAtEpochMillis = 42,
            appVersionName = "2.1",
        )
        val json = NomiDiaryExportService.diaryJson()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<DiaryEnvelopeV1>(encoded)

        assertTrue(encoded.contains("\"format\": \"nomi-diary\""))
        assertTrue(encoded.contains("\"date\": \"2026-08-30\""))
        assertTrue(encoded.contains("\"name\": \"Haferflocken\""))
        assertEquals(original, decoded)
        assertEquals(222.0, decoded.days.single().kcal, 0.0)
        assertEquals(8.1, decoded.days.single().proteinGrams, 0.0)
        assertEquals(35.4, decoded.days.single().carbohydrateGrams, 0.0)
    }

    private fun foodLog(
        id: Long,
        date: String,
        name: String,
        brand: String? = null,
        amount: Double,
        unit: String,
        meal: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        loggedAt: Long = id,
    ) = FoodLogEntity(
        id = id,
        mealCategory = meal,
        displayNameSnapshot = name,
        brandSnapshot = brand,
        amount = amount,
        unit = unit,
        nutritionSnapshot = NutritionValues(
            caloriesKcal = kcal,
            proteinGrams = protein,
            carbohydrateGrams = carbs,
            fatGrams = 0.0,
        ),
        sourceSnapshot = NutritionSourceSnapshot(),
        inputMethod = "text",
        localDate = date,
        loggedAtEpochMillis = loggedAt,
        zoneId = "Europe/Berlin",
        createdAtEpochMillis = loggedAt,
        updatedAtEpochMillis = loggedAt,
    )
}
