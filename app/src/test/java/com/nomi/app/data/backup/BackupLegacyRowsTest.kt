package com.nomi.app.data.backup

import com.nomi.app.data.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * Rows Nomi itself can write must never make the diary impossible to back up.
 *
 * Export validates the whole payload first, so one row the validator disliked - a manual entry
 * saved with an empty unit, or a long typed entry - failed every later export.
 */
class BackupLegacyRowsTest {
    private val today = LocalDate.parse("2026-10-01")

    @Test
    fun `a food logged with a blank unit does not block the backup`() {
        val summary = BackupValidator.validate(envelope(log().copy(unit = "")), today)

        assertEquals(1, summary.foodLogCount)
    }

    @Test
    fun `typed input up to the logging limit does not block the backup`() {
        val summary = BackupValidator.validate(envelope(log().copy(originalInput = "a".repeat(8_192))), today)

        assertEquals(1, summary.foodLogCount)
    }

    @Test
    fun `an unbounded input is still refused`() {
        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(envelope(log().copy(originalInput = "a".repeat(40_000))), today)
        }
    }

    private fun envelope(log: BackupFoodLogV1) = BackupEnvelopeV1(
        exportedAtEpochMillis = 1,
        appVersionName = "1.0.0-test",
        payload = BackupPayloadV1(
            preferences = AppPreferences().toBackup(),
            userProfile = null,
            nutritionPlans = emptyList(),
            nutritionSources = emptyList(),
            foods = emptyList(),
            foodServings = emptyList(),
            foodAliases = emptyList(),
            favoriteFoods = emptyList(),
            foodLogs = listOf(log),
            savedMeals = emptyList(),
            savedMealItems = emptyList(),
            weightEntries = emptyList(),
        ),
    )

    private fun log() = BackupFoodLogV1(
        id = 1,
        mealCategory = "SNACKS",
        displayNameSnapshot = "Homemade soup",
        amount = 1.0,
        unit = "bowl",
        nutritionSnapshot = BackupNutritionValuesV1(210.0, 8.0, 30.0, 6.0),
        sourceSnapshot = BackupNutritionSourceSnapshotV1(kind = "manual"),
        isEstimated = false,
        inputMethod = "manual",
        localDate = "2026-09-26",
        loggedAtEpochMillis = 1,
        zoneId = "Europe/Berlin",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
