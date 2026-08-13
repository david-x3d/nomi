package com.nomi.app.integration.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
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
    val writeNutrition: Boolean = false,
)

data class HealthWeight(
    val id: String,
    val kilograms: Double,
    val time: Instant,
    val zoneOffset: ZoneOffset?,
    val originPackageName: String,
    val clientRecordId: String?,
)

data class HealthActivitySummary(
    val steps: Long,
    val activeCaloriesKcal: Double,
)

enum class HealthConnectPermissionStatus {
    UNAVAILABLE,
    UPDATE_REQUIRED,
    DISCONNECTED,
    PARTIAL,
    CONNECTED,
}

val NomiHealthFeatures = HealthFeatures(
    readWeight = true,
    writeWeight = true,
    readSteps = true,
    readActiveCalories = true,
    writeNutrition = true,
)

/** Health Connect takes a bounded batch per call, and a busy month easily exceeds one. */
private const val NUTRITION_BATCH_SIZE = 100

internal fun resolveHealthConnectPermissionStatus(
    availability: HealthConnectAvailability,
    requiredPermissions: Set<String>,
    grantedPermissions: Set<String>,
): HealthConnectPermissionStatus = when (availability) {
    HealthConnectAvailability.UNAVAILABLE -> HealthConnectPermissionStatus.UNAVAILABLE
    HealthConnectAvailability.UPDATE_REQUIRED -> HealthConnectPermissionStatus.UPDATE_REQUIRED
    HealthConnectAvailability.AVAILABLE -> when {
        grantedPermissions.containsAll(requiredPermissions) -> HealthConnectPermissionStatus.CONNECTED
        grantedPermissions.intersect(requiredPermissions).isNotEmpty() -> HealthConnectPermissionStatus.PARTIAL
        else -> HealthConnectPermissionStatus.DISCONNECTED
    }
}

internal fun importableHealthWeights(
    weights: List<HealthWeight>,
    ownPackageName: String,
): List<HealthWeight> = weights.asSequence()
    .filter { it.id.isNotBlank() && it.originPackageName != ownPackageName }
    .distinctBy(HealthWeight::id)
    .toList()

class HealthConnectManager(private val context: Context) {
    val applicationPackageName: String get() = context.packageName

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
        if (features.writeNutrition) {
            add(HealthPermission.getWritePermission(NutritionRecord::class))
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
                originPackageName = record.metadata.dataOrigin.packageName,
                clientRecordId = record.metadata.clientRecordId,
            )
        }
    }

    suspend fun writeWeight(
        kilograms: Double,
        time: Instant,
        clientRecordId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        require(kilograms in 20.0..500.0) { "Weight is outside the supported range" }
        require(clientRecordId.isNotBlank()) { "A client record ID is required" }
        val record = WeightRecord(
            time = time,
            zoneOffset = zoneId.rules.getOffset(time),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.manualEntry(clientRecordId = clientRecordId),
        )
        return client.insertRecords(listOf(record)).recordIdsList.single()
    }

    /**
     * Mirrors logged portions into Health Connect, one record per food entry.
     *
     * Each record carries the log row's own client record id, so writing an entry Health Connect
     * already holds replaces it rather than duplicating the meal. Batches are kept small because
     * a single insert call has a record limit that a month of logging would pass.
     */
    suspend fun writeNutrition(entries: List<HealthNutritionEntry>) {
        if (entries.isEmpty()) return
        entries.chunked(NUTRITION_BATCH_SIZE).forEach { batch ->
            client.insertRecords(batch.map(HealthNutritionEntry::toRecord))
        }
    }

    /**
     * Removes nutrition records for food the user deleted in Nomi.
     *
     * Deletes are best effort by design: the user can also clear Nomi's data from inside Health
     * Connect, and a delete for a record that is already gone must not be able to wedge every
     * later sync behind it.
     */
    suspend fun deleteNutrition(clientRecordIds: List<String>) {
        if (clientRecordIds.isEmpty()) return
        clientRecordIds.chunked(NUTRITION_BATCH_SIZE).forEach { batch ->
            runCatching {
                client.deleteRecords(
                    recordType = NutritionRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = batch,
                )
            }
        }
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

/** Optional nutrients stay absent rather than becoming a claimed zero. */
private fun HealthNutritionEntry.toRecord(): NutritionRecord = NutritionRecord(
    startTime = startTime,
    startZoneOffset = zoneOffset,
    endTime = endTime,
    endZoneOffset = zoneOffset,
    energy = Energy.kilocalories(caloriesKcal),
    protein = Mass.grams(proteinGrams),
    totalCarbohydrate = Mass.grams(carbohydrateGrams),
    totalFat = Mass.grams(fatGrams),
    dietaryFiber = fiberGrams?.let(Mass::grams),
    sugar = sugarGrams?.let(Mass::grams),
    saturatedFat = saturatedFatGrams?.let(Mass::grams),
    sodium = sodiumGrams()?.let(Mass::grams),
    name = name,
    mealType = mealType,
    metadata = Metadata.manualEntry(
        clientRecordId = clientRecordId,
        clientRecordVersion = version,
    ),
)
