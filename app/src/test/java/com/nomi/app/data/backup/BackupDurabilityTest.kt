package com.nomi.app.data.backup

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.MicronutrientSetting
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Regression cover for three verified backup defects, all of which had the same shape: the
 * recovery copy became unavailable exactly when it was needed.
 *
 *  - A 16 MiB ceiling that a normal multi-year diary exceeds, so export failed for the users
 *    with the most history.
 *  - The whole document was encoded into one giant String before the size was checked, which is
 *    an OutOfMemoryError on a modest phone, and an Error, so nothing above could catch it.
 *  - A backwards wall-clock correction left updated_at behind created_at, which was a hard
 *    validation error: one device setting made every later export fail AND made an
 *    already-taken backup refuse to import.
 */
class BackupDurabilityTest {

    private val json: Json = NomiBackupService.strictBackupJson()

    private fun envelope(
        logs: List<BackupFoodLogV1>,
        preferences: BackupPreferencesV1 = basePreferences(),
    ) = BackupEnvelopeV1(
        exportedAtEpochMillis = 1_700_000_000_000,
        appVersionName = "2.4.0",
        payload = BackupPayloadV1(
            preferences = preferences,
            userProfile = null,
            nutritionPlans = emptyList(),
            nutritionSources = emptyList(),
            foods = emptyList(),
            foodServings = emptyList(),
            foodAliases = emptyList(),
            favoriteFoods = emptyList(),
            foodLogs = logs,
            savedMeals = emptyList(),
            savedMealItems = emptyList(),
            weightEntries = emptyList(),
        ),
    )

    private fun basePreferences() = BackupPreferencesV1(
        theme = com.nomi.app.data.preferences.ThemePreference.SYSTEM,
        dynamicColorEnabled = false,
        weightUnit = com.nomi.app.data.preferences.WeightUnitPreference.KILOGRAMS,
        heightUnit = com.nomi.app.data.preferences.HeightUnitPreference.CENTIMETERS,
        foodResearchProvider = BackupProviderSelectionV1("openrouter", "sonar"),
        foodInterpretationProvider = BackupProviderSelectionV1("openrouter", "gpt"),
        portionChangeProvider = BackupProviderSelectionV1("openrouter", "gpt"),
        visionProvider = BackupProviderSelectionV1("openrouter", "gpt"),
        reminders = com.nomi.app.data.preferences.ReminderPreferences(),
        // No profile in this fixture, and the validator rightly refuses a completed onboarding
        // without one; these tests are about size and timestamps, not onboarding.
        onboardingCompleted = false,
        adjustTargetFromActivity = false,
    )

    private fun log(id: Long, created: Long, updated: Long, notes: String? = null) = BackupFoodLogV1(
        id = id,
        foodId = null,
        foodServingId = null,
        nutritionSourceId = null,
        entryGroupId = null,
        originalInput = null,
        mealCategory = "SNACKS",
        displayNameSnapshot = "Test food $id",
        brandSnapshot = null,
        amount = 100.0,
        grams = 100.0,
        resolvedVolumeMl = null,
        unit = "g",
        nutritionSnapshot = BackupNutritionValuesV1(
            caloriesKcal = 100.0,
            proteinGrams = 5.0,
            carbohydrateGrams = 10.0,
            fatGrams = 2.0,
        ),
        sourceSnapshot = BackupNutritionSourceSnapshotV1(kind = "MANUFACTURER"),
        isEstimated = false,
        inputMethod = "ai",
        notes = notes,
        localDate = "2026-01-01",
        zoneId = "Europe/Berlin",
        loggedAtEpochMillis = created,
        createdAtEpochMillis = created,
        updatedAtEpochMillis = updated,
    )

    @Test
    fun `a diary well past the old 16 MiB ceiling validates`() {
        val summary = BackupValidator.validate(
            envelope((1L..20_000L).map { log(it, 1_700_000_000_000, 1_700_000_000_000) }),
        )
        assertEquals(20_000, summary.foodLogCount)
    }

    @Test
    fun `the ceiling is now far above a realistic diary`() {
        // 20 k rows at roughly 1.7 kB each is ~34 MB of JSON, which the old limit refused.
        assertTrue(BackupValidator.MAX_BACKUP_BYTES >= 128 * 1024 * 1024)
    }

    @Test
    fun `a backwards wall-clock correction does not invalidate the backup`() {
        // This is the case that used to make an existing backup permanently unrestorable.
        val skewed = listOf(
            log(1, created = 1_700_000_000_000, updated = 1_600_000_000_000),
            log(2, created = 1_700_000_000_000, updated = 1_700_000_000_000),
        )
        val summary = BackupValidator.validate(envelope(skewed))
        assertEquals(2, summary.foodLogCount)
    }

    @Test
    fun `a skewed timestamp still round-trips through the envelope`() {
        val source = envelope(
            listOf(log(1, created = 1_700_000_000_000, updated = 1_600_000_000_000)),
        )
        val encoded = json.encodeToString(source).encodeToByteArray()
        val decoded = json.decodeFromString<BackupEnvelopeV1>(encoded.decodeToString())
        assertEquals(
            1_600_000_000_000,
            decoded.payload.foodLogs.single().updatedAtEpochMillis,
        )
    }

    @Test
    fun `the preference fields that used to be dropped now survive a round trip`() {
        val preferences = basePreferences().copy(
            micronutrients = BackupMicronutrientsV1(
                fiberGrams = 33.0,
                fiberEnabled = true,
                sugarGrams = 27.0,
                sugarEnabled = true,
                saturatedFatGrams = 19.0,
                saturatedFatEnabled = true,
                sodiumMilligrams = 1_500.0,
                sodiumEnabled = true,
            ),
            calorieEstimateBias = CalorieEstimateBias.OVERESTIMATE,
            goalsCardStyle = GoalsCardStyle.RINGS,
        )
        val encoded = json.encodeToString(envelope(listOf(log(1, 1, 1)), preferences))
        val decoded = json.decodeFromString<BackupEnvelopeV1>(encoded)
        val p = decoded.payload.preferences
        assertEquals(1_500.0, p.micronutrients.sodiumMilligrams, 0.0)
        assertTrue(p.micronutrients.sodiumEnabled)
        assertEquals(CalorieEstimateBias.OVERESTIMATE, p.calorieEstimateBias)
        assertEquals(GoalsCardStyle.RINGS, p.goalsCardStyle)
    }

    @Test
    fun `a backup written by an older build still decodes`() {
        // No micronutrients/bias/style keys at all: the defaults are what keep old files readable.
        val legacy = """
            {
              "format": "nomi-backup",
              "schemaVersion": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "2.0.0",
              "payload": {
                "preferences": {
                  "theme": "SYSTEM", "dynamicColorEnabled": false,
                  "germanTranslationEnabled": false, "languageTag": "de",
                  "weightUnit": "KILOGRAMS", "heightUnit": "CENTIMETERS",
                  "foodResearchProvider": {"providerId":"openrouter","model":"sonar"},
                  "foodInterpretationProvider": {"providerId":"openrouter","model":"gpt"},
                  "portionChangeProvider": {"providerId":"openrouter","model":"gpt"},
                  "visionProvider": {"providerId":"openrouter","model":"gpt"},
                  "reminders": {
                    "breakfast": {"enabled":false,"localTime":"08:00","daysOfWeek":[1,2,3,4,5,6,7]},
                    "lunch": {"enabled":false,"localTime":"12:30","daysOfWeek":[1,2,3,4,5,6,7]},
                    "dinner": {"enabled":false,"localTime":"19:00","daysOfWeek":[1,2,3,4,5,6,7]},
                    "dailySummary": {"enabled":false,"localTime":"20:30","daysOfWeek":[1,2,3,4,5,6,7]},
                    "weight": {"enabled":false,"localTime":"08:00","daysOfWeek":[1]}
                  },
                  "onboardingCompleted": false, "adjustTargetFromActivity": false
                },
                "userProfile": null, "nutritionPlans": [], "nutritionSources": [],
                "foods": [], "foodServings": [], "foodAliases": [], "favoriteFoods": [],
                "foodLogs": [], "savedMeals": [], "savedMealItems": [], "weightEntries": []
              }
            }
        """.trimIndent()
        val decoded = json.decodeFromString<BackupEnvelopeV1>(legacy)
        assertEquals("de", decoded.payload.preferences.languageTag)
        assertNotNull(decoded.payload.preferences.goalsCardStyle)
    }

    @Test
    fun `gzip output is detected and inflated on read`() {
        val payload = json.encodeToString(envelope(listOf(log(7, 1, 1)))).encodeToByteArray()
        val gzipped = java.io.ByteArrayOutputStream().use { sink ->
            java.util.zip.GZIPOutputStream(sink).use { it.write(payload) }
            sink.toByteArray()
        }
        // The reader has to be able to tell a compressed file from a plain one on its own.
        assertEquals(0x1f.toByte(), gzipped[0])
        assertEquals(0x8b.toByte(), gzipped[1])
        val inflated = java.util.zip.GZIPInputStream(ByteArrayInputStream(gzipped))
            .use { it.readBytes() }
        val decoded = json.decodeFromString<BackupEnvelopeV1>(inflated.decodeToString())
        assertEquals(7L, decoded.payload.foodLogs.single().id)
    }

    @Test
    fun `a large diary compresses by roughly an order of magnitude`() {
        val rows = (1L..20_000L).map { log(it, 1_700_000_000_000, 1_700_000_000_000, notes = "x".repeat(200)) }
        val plain = json.encodeToString(envelope(rows)).encodeToByteArray().size
        val gzipped = java.io.ByteArrayOutputStream().use { sink ->
            java.util.zip.GZIPOutputStream(sink).use { it.write(json.encodeToString(envelope(rows)).encodeToByteArray()) }
            sink.toByteArray().size
        }
        assertTrue(
            "gzip saved no space: plain=$plain gzipped=$gzipped",
            gzipped < plain / 5,
        )
    }

    @Test
    fun `the byte budget refuses an oversized write`() {
        val budget = ByteBudget(10)
        budget.take(6)
        assertThrows(BackupFormatException::class.java) { budget.take(6) }
    }
}
