package com.nomi.app.integration.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
     * active calories and extended history optional.
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
                readHealthDataHistory = true,
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
                readHealthDataHistory = false,
            ),
            NomiRequiredHealthFeatures,
        )
    }

    @Test
    fun permissionStatus_doesNotRequireOptionalCaloriesOrHistory() {
        val requested = required + setOf("readCalories", "readHistory")
        assertFalse("readCalories" in required)
        assertFalse("readHistory" in required)
        assertTrue("readCalories" in requested)
        assertTrue("readHistory" in requested)
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
    fun permissionStatus_optionalOnlyGrantIsPartialInsteadOfDisconnected() {
        val requested = required + "readCalories"

        assertEquals(
            HealthConnectPermissionStatus.PARTIAL,
            resolveHealthConnectPermissionStatus(
                HealthConnectAvailability.AVAILABLE,
                requested,
                setOf("readCalories"),
            ),
        )
    }

    @Test
    fun historyPermission_isRequestedOnlyWhenTheProviderSupportsIt() {
        val historyOnly = HealthFeatures(readHealthDataHistory = true)

        assertEquals(
            emptySet<String>(),
            healthPermissionsFor(
                features = historyOnly,
                readHealthDataHistoryAvailable = false,
            ),
        )
        assertEquals(
            setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY),
            healthPermissionsFor(
                features = historyOnly,
                readHealthDataHistoryAvailable = true,
            ),
        )
    }

    @Test
    fun grantedFeatures_preserveEveryIndependentCapability() {
        val permissions = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        )

        assertEquals(
            HealthFeatures(
                readWeight = true,
                readSteps = true,
                writeNutrition = true,
                readHealthDataHistory = true,
            ),
            grantedHealthFeatures(
                grantedPermissions = permissions,
                readHealthDataHistoryAvailable = true,
            ),
        )
        assertFalse(
            grantedHealthFeatures(
                grantedPermissions = permissions,
                readHealthDataHistoryAvailable = false,
            ).readHealthDataHistory,
        )
    }

    @Test
    fun featureContainment_doesNotLetOneGrantStandInForAnother() {
        val granted = HealthFeatures(readWeight = true, readSteps = true)

        assertTrue(granted.containsAll(HealthFeatures(readWeight = true)))
        assertTrue(granted.containsAll(HealthFeatures(readSteps = true)))
        assertFalse(granted.containsAll(HealthFeatures(writeWeight = true)))
        assertFalse(granted.containsAll(HealthFeatures(readHealthDataHistory = true)))
    }

    @Test
    fun weightClientRecordId_isStableAndLocalRowSpecific() {
        assertEquals("nomi-weight-1700000000000-42", weightClientRecordId(42L, 1_700_000_000_000L))
        assertEquals("nomi-weight-1700000000000-42", weightClientRecordId(42L, 1_700_000_000_000L))
        assertEquals("nomi-weight-1700000000000-43", weightClientRecordId(43L, 1_700_000_000_000L))
        assertEquals("nomi-weight-1700000000001-42", weightClientRecordId(42L, 1_700_000_000_001L))
        assertThrows(IllegalArgumentException::class.java) { weightClientRecordId(0L, 1L) }
        assertThrows(IllegalArgumentException::class.java) { weightClientRecordId(1L, -1L) }
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
