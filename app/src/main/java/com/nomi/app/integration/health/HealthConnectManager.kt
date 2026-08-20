package com.nomi.app.integration.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
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
import kotlinx.coroutines.CancellationException

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
    val readHealthDataHistory: Boolean = false,
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
    val steps: Long?,
    val activeCaloriesKcal: Double?,
    /** False only when an optional active-calorie read failed after steps were read successfully. */
    val activeCaloriesReadSucceeded: Boolean,
)

data class HealthNutritionDeleteRange(
    val start: Instant,
    val end: Instant,
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
    readHealthDataHistory = true,
)

/**
 * Active calories improve the display, and history enables a backfill beyond Health Connect's
 * normal lookback window. Neither is needed for Nomi's core connection.
 */
val NomiRequiredHealthFeatures = NomiHealthFeatures.copy(
    readActiveCalories = false,
    readHealthDataHistory = false,
)

/** Health Connect takes a bounded batch per call, and a complete food history easily exceeds one. */
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

/**
 * Stable across retries and database restores without colliding with a fresh installation's IDs.
 */
fun weightClientRecordId(localWeightId: Long, createdAtEpochMillis: Long): String {
    require(localWeightId > 0L) { "A persisted local weight ID is required" }
    require(createdAtEpochMillis >= 0L) { "A persisted weight creation time is required" }
    return "nomi-weight-$createdAtEpochMillis-$localWeightId"
}

internal fun healthPermissionsFor(
    features: HealthFeatures,
    readHealthDataHistoryAvailable: Boolean,
): Set<String> = buildSet {
    if (features.readWeight) add(HealthPermission.getReadPermission(WeightRecord::class))
    if (features.writeWeight) add(HealthPermission.getWritePermission(WeightRecord::class))
    if (features.readSteps) add(HealthPermission.getReadPermission(StepsRecord::class))
    if (features.readActiveCalories) {
        add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
    }
    if (features.writeNutrition) {
        add(HealthPermission.getWritePermission(NutritionRecord::class))
    }
    if (features.readHealthDataHistory && readHealthDataHistoryAvailable) {
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    }
}

internal fun grantedHealthFeatures(
    grantedPermissions: Set<String>,
    readHealthDataHistoryAvailable: Boolean,
): HealthFeatures = HealthFeatures(
    readWeight = HealthPermission.getReadPermission(WeightRecord::class) in grantedPermissions,
    writeWeight = HealthPermission.getWritePermission(WeightRecord::class) in grantedPermissions,
    readSteps = HealthPermission.getReadPermission(StepsRecord::class) in grantedPermissions,
    readActiveCalories = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in
        grantedPermissions,
    writeNutrition = HealthPermission.getWritePermission(NutritionRecord::class) in grantedPermissions,
    readHealthDataHistory = readHealthDataHistoryAvailable &&
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions,
)

internal fun HealthFeatures.containsAll(requested: HealthFeatures): Boolean =
    (!requested.readWeight || readWeight) &&
        (!requested.writeWeight || writeWeight) &&
        (!requested.readSteps || readSteps) &&
        (!requested.readActiveCalories || readActiveCalories) &&
        (!requested.writeNutrition || writeNutrition) &&
        (!requested.readHealthDataHistory || readHealthDataHistory)

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

    /** Whether this Health Connect provider can grant reads older than its normal lookback limit. */
    val supportsReadHealthDataHistory: Boolean
        get() {
            if (availability != HealthConnectAvailability.AVAILABLE) return false
            return try {
                client.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY,
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } catch (_: Exception) {
                // This is an optional provider extension. A broken feature query must not hide
                // otherwise valid read/write grants for weights, activity, or nutrition.
                false
            }
        }

    /** Omits optional permissions whose backing Health Connect feature is unavailable. */
    fun permissionsFor(features: HealthFeatures): Set<String> = healthPermissionsFor(
        features = features,
        readHealthDataHistoryAvailable = features.readHealthDataHistory &&
            supportsReadHealthDataHistory,
    )

    suspend fun grantedPermissions(): Set<String> = if (availability == HealthConnectAvailability.AVAILABLE) {
        client.permissionController.getGrantedPermissions()
    } else {
        emptySet()
    }

    /**
     * Turns one PermissionController result into independently usable sync capabilities.
     *
     * In particular, refusing one category does not hide the other categories that were granted.
     */
    fun featuresForGrantedPermissions(grantedPermissions: Set<String>): HealthFeatures =
        grantedHealthFeatures(
            grantedPermissions = grantedPermissions,
            readHealthDataHistoryAvailable =
                HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions &&
                    supportsReadHealthDataHistory,
        )

    suspend fun grantedFeatures(): HealthFeatures =
        featuresForGrantedPermissions(grantedPermissions())

    suspend fun hasPermissions(features: HealthFeatures): Boolean {
        val historyAvailable = !features.readHealthDataHistory || supportsReadHealthDataHistory
        if (!historyAvailable) return false
        return grantedHealthFeatures(
            grantedPermissions = grantedPermissions(),
            readHealthDataHistoryAvailable = features.readHealthDataHistory && historyAvailable,
        ).containsAll(features)
    }

    suspend fun readWeights(start: Instant, end: Instant): List<HealthWeight> {
        val weights = mutableListOf<HealthWeight>()
        val returnedPageTokens = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                    pageToken = pageToken,
                ),
            )
            weights += response.records.map { record ->
                HealthWeight(
                    id = record.metadata.id,
                    kilograms = record.weight.inKilograms,
                    time = record.time,
                    zoneOffset = record.zoneOffset,
                    originPackageName = record.metadata.dataOrigin.packageName,
                    clientRecordId = record.metadata.clientRecordId,
                )
            }
            pageToken = response.pageToken
            check(pageToken == null || returnedPageTokens.add(pageToken)) {
                "Health Connect returned the same weight page token more than once"
            }
        } while (pageToken != null)
        return weights
    }

    suspend fun writeWeight(
        kilograms: Double,
        time: Instant,
        clientRecordId: String,
        clientRecordVersion: Long = 0L,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        require(kilograms in 20.0..500.0) { "Weight is outside the supported range" }
        require(clientRecordId.isNotBlank()) { "A client record ID is required" }
        require(clientRecordVersion >= 0L) { "Client record version must not be negative" }
        val record = WeightRecord(
            time = time,
            zoneOffset = zoneId.rules.getOffset(time),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.manualEntry(
                clientRecordId = clientRecordId,
                clientRecordVersion = clientRecordVersion,
            ),
        )
        return client.insertRecords(listOf(record)).recordIdsList.single()
    }

    /**
     * Mirrors logged portions into Health Connect, one record per food entry.
     *
     * Each record carries the log row's own client record id, so writing an entry Health Connect
     * already holds replaces it rather than duplicating the meal. Batches are kept small because
     * a single insert call has a record limit that a complete history can pass.
     */
    suspend fun writeNutrition(entries: List<HealthNutritionEntry>) {
        if (entries.isEmpty()) return
        entries.chunked(NUTRITION_BATCH_SIZE).forEach { batch ->
            client.insertRecords(batch.map(HealthNutritionEntry::toRecord))
        }
    }

    /**
     * Deletes only Nomi-owned nutrition records in retry-safe time ranges.
     *
     * Unlike identifier deletion, an empty time range succeeds on both framework and APK Health
     * Connect providers. The caller rewrites every live Nomi record overlapping these ranges.
     */
    suspend fun deleteNutrition(ranges: List<HealthNutritionDeleteRange>) {
        ranges.forEach { range ->
            require(range.start < range.end) { "A nutrition delete range must not be empty" }
            client.deleteRecords(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(range.start, range.end),
            )
        }
    }

    /**
     * Rebuilds every nutrition record owned by Nomi from the local journal.
     *
     * Health Connect's time-range delete is scoped to records written by this application. It is
     * therefore safe when an individual client record was already removed in Health Connect, a
     * case where deleting that missing identifier would otherwise fail forever. The caller stores
     * a durable rewrite checkpoint before entering this method so a crash or partial batch write
     * always causes the complete rebuild to be retried.
     */
    suspend fun replaceNutrition(entries: List<HealthNutritionEntry>) {
        client.deleteRecords(
            recordType = NutritionRecord::class,
            timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
        )
        writeNutrition(entries)
    }

    suspend fun readActivity(
        start: Instant,
        end: Instant,
        features: HealthFeatures = NomiHealthFeatures,
    ): HealthActivitySummary {
        require(features.readSteps || features.readActiveCalories) {
            "At least one activity category is required."
        }
        val timeRange = TimeRangeFilter.between(start, end)
        val steps = if (features.readSteps) {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = timeRange,
                ),
            )[StepsRecord.COUNT_TOTAL]
        } else {
            null
        }
        var activeCaloriesReadSucceeded = false
        val activeCaloriesKcal = if (features.readActiveCalories) {
            try {
                val calories = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = timeRange,
                    ),
                )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                activeCaloriesReadSucceeded = true
                calories
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Total activity is optional. A provider failure must not discard valid steps or
                // prevent Nomi's local step estimate from being shown.
                null
            }
        } else {
            null
        }
        return HealthActivitySummary(
            steps = steps,
            activeCaloriesKcal = activeCaloriesKcal,
            activeCaloriesReadSucceeded = activeCaloriesReadSucceeded,
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
