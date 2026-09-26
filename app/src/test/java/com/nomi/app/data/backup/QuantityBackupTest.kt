package com.nomi.app.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class QuantityBackupTest {
    private fun log() = BackupFoodLogV1(
        id = 1, mealCategory = "SNACKS", displayNameSnapshot = "Coke", amount = 2.0, unit = "can",
        resolvedVolumeMl = 500.0, resolutionSource = "user-stated container size",
        nutritionSnapshot = BackupNutritionValuesV1(210.0, 0.0, 52.5, 0.0),
        sourceSnapshot = BackupNutritionSourceSnapshotV1(kind = "database"),
        isEstimated = false, inputMethod = "text", localDate = "2026-09-26",
        loggedAtEpochMillis = 1, zoneId = "Europe/Berlin", createdAtEpochMillis = 1, updatedAtEpochMillis = 1,
    )

    @Test fun `backup and restore retain semantic amount and resolved volume separately`() {
        val restored = Json.decodeFromString<BackupFoodLogV1>(Json.encodeToString(log())).toEntity()
        assertEquals(2.0, restored.amount, 0.0)
        assertEquals("can", restored.unit)
        assertEquals(500.0, restored.resolvedVolumeMl!!, 0.0)
        assertEquals("user-stated container size", restored.resolutionSource)
        assertNull(restored.grams)
    }

    @Test fun `older backup without a resolved volume remains readable`() {
        val original = log().copy(resolvedVolumeMl = null, resolutionSource = null)
        val restored = Json.decodeFromString<BackupFoodLogV1>(Json.encodeToString(original)).toEntity()
        assertEquals(2.0, restored.amount, 0.0)
        assertNull(restored.resolvedVolumeMl)
        assertNull(restored.resolutionSource)
    }
}
