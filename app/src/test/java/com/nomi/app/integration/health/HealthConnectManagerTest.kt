package com.nomi.app.integration.health

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectManagerTest {
    private val required = setOf("readWeight", "writeWeight", "readSteps", "writeNutrition")

    @Test
    fun permissionStatus_requiresEveryRequiredCategoryForConnection() {
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
     * Nomi asks for the complete useful set, while the required subset deliberately leaves total
     * active calories optional so denying them cannot block the local step estimate.
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
        assertEquals(
            HealthFeatures(
                readWeight = true,
                writeWeight = true,
                readSteps = true,
                readActiveCalories = false,
                writeNutrition = true,
            ),
            NomiRequiredHealthFeatures,
        )
    }

    @Test
    fun permissionStatus_doesNotBlockStepEstimateWhenOptionalCaloriesAreMissing() {
        val requested = required + "readCalories"
        assertEquals(false, "readCalories" in required)
        assertEquals(true, "readCalories" in requested)
        assertEquals(
            HealthConnectPermissionStatus.CONNECTED,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.AVAILABLE,
                required,
                required,
            ),
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
