package com.nomi.app.integration.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class HealthFeatures(
    val readWeight: Boolean = false,
    val writeWeight: Boolean = false,
    val readSteps: Boolean = false,
    val readActiveCalories: Boolean = false,
)

data class HealthWeight(
    val id: String,
    val kilograms: Double,
    val time: Instant,
    val zoneOffset: ZoneOffset?,
)

data class HealthActivitySummary(
    val steps: Long,
    val activeCaloriesKcal: Double,
)

class HealthConnectManager(private val context: Context) {
    val availability: HealthConnectAvailability
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED

            else -> HealthConnectAvailability.UNAVAILABLE
        }

    private val client: HealthConnectClient
        get() {
            check(availability == HealthConnectAvailability.AVAILABLE) {
                "Health Connect isn't available"
            }
            return HealthConnectClient.getOrCreate(context)
        }

    fun permissionsFor(features: HealthFeatures): Set<String> = buildSet {
        if (features.readWeight) add(HealthPermission.getReadPermission(WeightRecord::class))
        if (features.writeWeight) add(HealthPermission.getWritePermission(WeightRecord::class))
        if (features.readSteps) add(HealthPermission.getReadPermission(StepsRecord::class))
        if (features.readActiveCalories) {
            add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
        }
    }

    suspend fun grantedPermissions(): Set<String> = if (availability == HealthConnectAvailability.AVAILABLE) {
        client.permissionController.getGrantedPermissions()
    } else {
        emptySet()
    }

    suspend fun hasPermissions(features: HealthFeatures): Boolean =
        grantedPermissions().containsAll(permissionsFor(features))

    suspend fun readWeights(start: Instant, end: Instant): List<HealthWeight> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = true,
            ),
        )
        return response.records.map { record ->
            HealthWeight(
                id = record.metadata.id,
                kilograms = record.weight.inKilograms,
                time = record.time,
                zoneOffset = record.zoneOffset,
            )
        }
    }

    suspend fun writeWeight(
        kilograms: Double,
        time: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        require(kilograms in 20.0..500.0) { "Weight is outside the supported range" }
        val record = WeightRecord(
            time = time,
            zoneOffset = zoneId.rules.getOffset(time),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.manualEntry(),
        )
        return client.insertRecords(listOf(record)).recordIdsList.single()
    }

    suspend fun readActivity(start: Instant, end: Instant): HealthActivitySummary {
        val aggregation = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return HealthActivitySummary(
            steps = aggregation[StepsRecord.COUNT_TOTAL] ?: 0L,
            activeCaloriesKcal = aggregation[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories ?: 0.0,
        )
    }
}
