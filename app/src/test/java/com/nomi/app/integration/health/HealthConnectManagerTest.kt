package com.nomi.app.integration.health

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectManagerTest {
    private val required = setOf("readWeight", "writeWeight", "readSteps", "readCalories")

    @Test
    fun permissionStatus_requiresEveryRequestedCategoryForConnection() {
        assertEquals(
            HealthConnectPermissionStatus.CONNECTED,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.AVAILABLE,
                required,
                required,
            ),
        )
        assertEquals(
            HealthConnectPermissionStatus.PARTIAL,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.AVAILABLE,
                required,
                setOf("readWeight", "writeWeight"),
            ),
        )
        assertEquals(
            HealthConnectPermissionStatus.DISCONNECTED,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.AVAILABLE,
                required,
                emptySet(),
            ),
        )
    }

    @Test
    fun permissionStatus_preservesUnavailableAndUpdateRequiredStates() {
        assertEquals(
            HealthConnectPermissionStatus.UNAVAILABLE,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.UNAVAILABLE,
                required,
                required,
            ),
        )
        assertEquals(
            HealthConnectPermissionStatus.UPDATE_REQUIRED,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.UPDATE_REQUIRED,
                required,
                required,
            ),
        )
    }

    /**
     * The connection is only reported as complete once every feature listed here is granted, so a
     * feature dropped from this set would leave the permission request asking for less than the
     * status demands - which is a connection the user can never finish.
     */
    @Test
    fun nomiFeatures_coverEveryCategoryTheSyncNeeds() {
        assertEquals(
            HealthFeatures(
                readWeight = true,
                writeWeight = true,
                readSteps = true,
                readActiveCalories = true,
                writeNutrition = true,
            ),
            NomiHealthFeatures,
        )
    }

    @Test
    fun importableWeights_excludesNomiRecordsAndDuplicateExternalIds() {
        val firstExternal = weight(id = "external-1", origin = "com.scale.app")
        val duplicateExternal = firstExternal.copy(kilograms = 99.0)
        val ownRecord = weight(id = "nomi-1", origin = "com.nomi.app")
        val blankId = weight(id = "", origin = "com.scale.app")

        val selected = importableHealthWeights(
            weights = listOf(firstExternal, duplicateExternal, ownRecord, blankId),
            ownPackageName = "com.nomi.app",
        )

        assertEquals(listOf(firstExternal), selected)
    }

    private fun weight(id: String, origin: String) = HealthWeight(
        id = id,
        kilograms = 72.5,
        time = Instant.parse("2026-08-08T08:00:00Z"),
        zoneOffset = ZoneOffset.UTC,
        originPackageName = origin,
        clientRecordId = null,
    )
}
