package com.nomi.app.data.backup

import com.nomi.app.data.preferences.AppPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupValidatorTest {
    @Test
    fun emptyFreshInstallBackupIsValid() {
        val summary = BackupValidator.validate(envelope(), LocalDate.parse("2026-08-07"))

        assertTrue(summary.durableRowCount == 0)
    }

    @Test
    fun foreignKeyFailureIsReportedBeforeImport() {
        val invalid = envelope(
            payload = emptyPayload().copy(
                foodServings = listOf(
                    BackupFoodServingV1(
                        id = 1,
                        foodId = 99,
                        name = "Cup",
                        normalizedName = "cup",
                        amount = 1.0,
                        unit = "cup",
                        grams = 240.0,
                        isDefault = true,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    ),
                ),
            ),
        )

        val error = runCatching {
            BackupValidator.validate(invalid, LocalDate.parse("2026-08-07"))
        }.exceptionOrNull() as BackupValidationException

        assertTrue(error.issues.any { it.path.endsWith("foodId") && it.message == "unknown food" })
    }

    @Test
    fun providerEndpointCannotSmuggleCredentials() {
        val unsafePreferences = AppPreferences().toBackup().copy(
            visionProvider = BackupProviderSelectionV1(
                providerId = "custom",
                model = "vision",
                endpoint = "https://api.example.test/v1?api_key=secret",
            ),
        )
        val error = runCatching {
            BackupValidator.validate(
                envelope(payload = emptyPayload().copy(preferences = unsafePreferences)),
                LocalDate.parse("2026-08-07"),
            )
        }.exceptionOrNull() as BackupValidationException

        assertTrue(error.issues.any { it.path.endsWith("visionProvider.endpoint") })
    }

    private fun envelope(payload: BackupPayloadV1 = emptyPayload()) = BackupEnvelopeV1(
        exportedAtEpochMillis = 1,
        appVersionName = "1.0.0-test",
        payload = payload,
    )

    private fun emptyPayload() = BackupPayloadV1(
        preferences = AppPreferences().toBackup(),
        userProfile = null,
        nutritionPlans = emptyList(),
        nutritionSources = emptyList(),
        foods = emptyList(),
        foodServings = emptyList(),
        foodAliases = emptyList(),
        favoriteFoods = emptyList(),
        foodLogs = emptyList(),
        savedMeals = emptyList(),
        savedMealItems = emptyList(),
        weightEntries = emptyList(),
    )
}
