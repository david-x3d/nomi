package com.nomi.app.data.repository

import com.nomi.app.data.local.entity.WeightEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NomiRepositoryWeightSyncTest {
    @Test
    fun externalWeightChanges_addsOnlyOneCopyOfANewExternalRecord() {
        val incoming = weight(id = 99, externalId = "record-1")

        val changes = externalWeightChanges(
            entries = listOf(incoming, incoming.copy(weightKg = 91.0)),
            existingEntries = emptyList(),
        )

        assertEquals(listOf(incoming.copy(id = 0)), changes.additions)
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun externalWeightChanges_updatesAnExistingRecordWithoutReplacingLocalMetadata() {
        val existing = weight(
            id = 7,
            externalId = "record-1",
            weightKg = 80.0,
            note = "after breakfast",
            createdAt = 100,
            updatedAt = 100,
        )
        val incoming = weight(
            externalId = "record-1",
            weightKg = 79.5,
            measuredAt = 2_000,
            createdAt = 900,
            updatedAt = 900,
        )

        val changes = externalWeightChanges(listOf(incoming), listOf(existing))

        assertTrue(changes.additions.isEmpty())
        assertEquals(
            listOf(
                existing.copy(
                    weightKg = 79.5,
                    measuredAtEpochMillis = 2_000,
                    updatedAtEpochMillis = 900,
                ),
            ),
            changes.updates,
        )
    }

    @Test
    fun externalWeightChanges_isIdempotentWhenOnlyTheSyncTimestampChanged() {
        val existing = weight(id = 7, externalId = "record-1", updatedAt = 100)
        val repeatedImport = existing.copy(id = 0, createdAtEpochMillis = 900, updatedAtEpochMillis = 900)

        val changes = externalWeightChanges(listOf(repeatedImport), listOf(existing))

        assertTrue(changes.additions.isEmpty())
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun externalWeightChanges_ignoresBlankExternalIds() {
        val changes = externalWeightChanges(
            entries = listOf(weight(externalId = null), weight(externalId = "  ")),
            existingEntries = emptyList(),
        )

        assertTrue(changes.additions.isEmpty())
        assertTrue(changes.updates.isEmpty())
    }

    private fun weight(
        id: Long = 0,
        externalId: String?,
        weightKg: Double = 80.0,
        measuredAt: Long = 1_000,
        note: String? = null,
        createdAt: Long = 100,
        updatedAt: Long = 100,
    ) = WeightEntryEntity(
        id = id,
        weightKg = weightKg,
        localDate = "2026-08-20",
        measuredAtEpochMillis = measuredAt,
        zoneId = "Europe/Berlin",
        note = note,
        source = HEALTH_CONNECT_WEIGHT_SOURCE,
        externalId = externalId,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt,
    )
}
