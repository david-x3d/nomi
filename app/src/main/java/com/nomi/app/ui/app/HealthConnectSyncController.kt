package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.preferences.HealthNutritionSyncState
import com.nomi.app.data.repository.HEALTH_CONNECT_WEIGHT_SOURCE
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.integration.health.HealthConnectManager
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.integration.health.HealthFeatures
import com.nomi.app.integration.health.HealthNutritionDeleteRange
import com.nomi.app.integration.health.NomiHealthFeatures
import com.nomi.app.integration.health.importableHealthWeights
import com.nomi.app.integration.health.nutritionSyncDatesForFullHistory
import com.nomi.app.integration.health.nutritionSyncStartTimes
import com.nomi.app.integration.health.planNutritionSync
import com.nomi.app.integration.health.resolveHealthConnectPermissionStatus
import com.nomi.app.integration.health.toHealthNutritionEntry
import com.nomi.app.integration.health.weightClientRecordId
import com.nomi.app.ui.settings.HealthConnectUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/** What became of mirroring one freshly logged weight into Health Connect. */
internal enum class WeightMirrorResult { NOT_PERMITTED, SYNCED, SYNCED_BUT_UNMARKED, FAILED }

/**
 * Keeps Health Connect and Nomi's own data in step: weights in both directions, today's steps
 * and active calories in, and the food log out.
 *
 * It owns the sync state the settings and Today pages read, and it is the only place that talks
 * to [HealthConnectManager] for a sync, so every pass runs under one mutex and overlapping
 * triggers collapse into a single follow-up pass.
 *
 * The manager arrives as a provider rather than an instance, so it is still only created the
 * first time a sync actually needs it.
 */
internal class HealthConnectSyncController(
    private val repository: NomiRepository,
    private val healthConnectProvider: () -> HealthConnectManager,
    private val scope: CoroutineScope,
) {
    private val healthConnect: HealthConnectManager get() = healthConnectProvider()

    /** Read on every use, so a time-zone change is followed without restarting the app. */
    private val zoneId: ZoneId get() = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now(zoneId)

    private val mutableState = MutableStateFlow(HealthConnectUiState())
    val state: StateFlow<HealthConnectUiState> = mutableState.asStateFlow()

    private val mutex = Mutex()
    private var syncJob: Job? = null
    private var pendingFullSync = false
    private var pendingNutritionSync = false
    private var pendingUserInitiatedSync = false

    private data class WeightExportSummary(
        val sentCount: Int,
        val failedCount: Int,
    )

    /**
     * Coalesces onStart, permission callbacks, food changes and repeated button taps.
     *
     * A request made while a sync is running becomes one follow-up pass rather than being dropped
     * or queued repeatedly. Yielding once also folds the usual onStart + permission-result pair
     * into the same pass before any Health Connect I/O starts.
     */
    fun request(
        userInitiated: Boolean = false,
        nutritionOnly: Boolean = false,
    ) {
        if (nutritionOnly) {
            pendingNutritionSync = true
        } else {
            pendingFullSync = true
            pendingUserInitiatedSync = pendingUserInitiatedSync || userInitiated
        }
        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            yield()
            try {
                while (pendingFullSync || pendingNutritionSync) {
                    val runFullSync = pendingFullSync
                    val runUserInitiated = pendingUserInitiatedSync
                    pendingFullSync = false
                    pendingUserInitiatedSync = false
                    if (runFullSync) pendingNutritionSync = false

                    mutex.withLock {
                        if (runFullSync) {
                            refreshAndSyncLocked(runUserInitiated)
                        } else {
                            pendingNutritionSync = false
                            syncNutritionLog()
                        }
                    }
                }
            } finally {
                syncJob = null
            }
        }
    }

    private suspend fun refreshAndSyncLocked(userInitiated: Boolean) {
        try {
            val availability = healthConnect.availability
            val requestedPermissions = healthConnect.permissionsFor(NomiHealthFeatures)
            val grantedPermissions = runCatching { healthConnect.grantedPermissions() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    mutableState.value = mutableState.value.copy(
                        isSyncing = false,
                        message = "Health Connect permissions couldn't be checked. Try again.",
                    )
                    return
                }
            val status = resolveHealthConnectPermissionStatus(
                availability = availability,
                requiredPermissions = requestedPermissions,
                grantedPermissions = grantedPermissions,
            )
            if (
                status == HealthConnectPermissionStatus.UNAVAILABLE ||
                status == HealthConnectPermissionStatus.UPDATE_REQUIRED
            ) {
                mutableState.value = HealthConnectUiState(
                    status = status,
                    message = when (status) {
                        HealthConnectPermissionStatus.UPDATE_REQUIRED ->
                            "Update Health Connect to enable syncing."
                        else -> null
                    },
                )
                return
            }

            val grantedFeatures = healthConnect.featuresForGrantedPermissions(grantedPermissions)
            val hasUsablePermission = grantedFeatures.readWeight || grantedFeatures.writeWeight ||
                grantedFeatures.readSteps || grantedFeatures.readActiveCalories ||
                grantedFeatures.writeNutrition
            if (!hasUsablePermission) {
                mutableState.value = HealthConnectUiState(status = status)
                return
            }

            val previous = mutableState.value
            mutableState.value = previous.copy(
                status = status,
                isSyncing = true,
                message = if (userInitiated) "Syncing Health Connect..." else previous.message,
            )

            val now = Instant.now()
            val activityDate = now.atZone(zoneId).toLocalDate()
            val activityDateText = activityDate.toString()
            val syncEpochMillis = now.toEpochMilli()
            val failures = mutableListOf<String>()
            var attemptedOperationCount = 0
            var importedWeightCount = 0
            var sentWeightCount = 0
            var sharedNutritionEntryCount = previous.sharedNutritionEntryCount
            val canRetainPreviousActivity = previous.activityLocalDate == activityDateText
            var todaySteps = if (grantedFeatures.readSteps && canRetainPreviousActivity) {
                previous.todaySteps
            } else {
                null
            }
            var todayActiveCaloriesKcal = if (
                grantedFeatures.readActiveCalories && canRetainPreviousActivity
            ) {
                previous.todayActiveCaloriesKcal
            } else {
                null
            }
            var syncedActivityLocalDate = activityDateText.takeIf {
                canRetainPreviousActivity &&
                    (grantedFeatures.readSteps || grantedFeatures.readActiveCalories)
            }

            if (grantedFeatures.readWeight) {
                attemptedOperationCount += 1
                runCatching {
                    val weights = healthConnect.readWeights(
                        // Without extended-history access Health Connect safely filters this to
                        // the caller's grant-era boundary; with it, the complete history returns.
                        start = Instant.EPOCH,
                        end = now,
                    )
                    val entries = importableHealthWeights(
                        weights = weights,
                        ownPackageName = healthConnect.applicationPackageName,
                    ).map { weight ->
                        val measuredDate = weight.zoneOffset
                            ?.let { offset -> weight.time.atOffset(offset).toLocalDate() }
                            ?: weight.time.atZone(zoneId).toLocalDate()
                        WeightEntryEntity(
                            weightKg = weight.kilograms,
                            localDate = measuredDate.toString(),
                            measuredAtEpochMillis = weight.time.toEpochMilli(),
                            zoneId = weight.zoneOffset?.id ?: zoneId.id,
                            source = HEALTH_CONNECT_WEIGHT_SOURCE,
                            externalId = weight.id,
                            createdAtEpochMillis = syncEpochMillis,
                            updatedAtEpochMillis = syncEpochMillis,
                        )
                    }
                    repository.importHealthConnectWeights(entries)
                }.onSuccess { imported ->
                    importedWeightCount = imported
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    failures += "weight import"
                }
            }

            if (grantedFeatures.writeWeight) {
                attemptedOperationCount += 1
                runCatching { pushPendingWeights() }
                    .onSuccess { result ->
                        sentWeightCount = result.sentCount
                        if (result.failedCount > 0) failures += "weight export"
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        failures += "weight export"
                    }
            }

            if (grantedFeatures.readSteps || grantedFeatures.readActiveCalories) {
                attemptedOperationCount += 1
                runCatching {
                    healthConnect.readActivity(
                        start = activityDate.atStartOfDay(zoneId).toInstant(),
                        end = now,
                        features = HealthFeatures(
                            readSteps = grantedFeatures.readSteps,
                            readActiveCalories = grantedFeatures.readActiveCalories,
                        ),
                    )
                }.onSuccess { activity ->
                    // A successful read with no records is zero, not "not synced": right after
                    // midnight there are no steps yet, and treating that as missing hid the
                    // activity calories and showed "Not synced yet" after a sync that worked.
                    if (grantedFeatures.readSteps) todaySteps = activity.steps ?: 0L
                    if (
                        grantedFeatures.readActiveCalories &&
                        activity.activeCaloriesReadSucceeded
                    ) {
                        todayActiveCaloriesKcal = activity.activeCaloriesKcal ?: 0.0
                    }
                    syncedActivityLocalDate = activityDateText
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    failures += "activity"
                }
            }

            if (grantedFeatures.writeNutrition) {
                attemptedOperationCount += 1
                runCatching { pushNutrition(forceRewrite = userInitiated) }
                    .onSuccess { sharedNutritionEntryCount = it }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        failures += "nutrition"
                    }
            }

            mutableState.value = HealthConnectUiState(
                status = status,
                isSyncing = false,
                todaySteps = todaySteps,
                todayActiveCaloriesKcal = todayActiveCaloriesKcal,
                activityLocalDate = syncedActivityLocalDate,
                sharedNutritionEntryCount = sharedNutritionEntryCount,
                lastSyncEpochMillis = if (attemptedOperationCount > 0 && failures.isEmpty()) {
                    syncEpochMillis
                } else {
                    previous.lastSyncEpochMillis
                },
                importedWeightCount = importedWeightCount,
                message = when {
                    failures.isNotEmpty() -> "Some Health Connect data couldn't be synced. Try again."
                    importedWeightCount == 1 -> "Health Connect synced. Imported 1 new weight."
                    importedWeightCount > 1 -> "Health Connect synced. Imported $importedWeightCount new weights."
                    sentWeightCount == 1 -> "Health Connect synced. Sent 1 pending weight."
                    sentWeightCount > 1 -> "Health Connect synced. Sent $sentWeightCount pending weights."
                    status == HealthConnectPermissionStatus.PARTIAL ->
                        "Allowed Health Connect categories are up to date. Grant missing permissions for full sync."
                    else -> "Health Connect is up to date."
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                isSyncing = false,
                message = "Health Connect couldn't be synced. Try again.",
            )
        }
    }

    /** Retries every local/onboarding weight that has never reached Health Connect. */
    private suspend fun pushPendingWeights(): WeightExportSummary {
        var sent = 0
        var failed = 0
        repository.pendingHealthConnectWeightSync().forEach { weight ->
            try {
                val externalId = healthConnect.writeWeight(
                    kilograms = weight.weightKg,
                    time = Instant.ofEpochMilli(weight.measuredAtEpochMillis),
                    clientRecordId = weightClientRecordId(weight.id, weight.createdAtEpochMillis),
                    clientRecordVersion = weight.updatedAtEpochMillis.coerceAtLeast(0L),
                    zoneId = runCatching { ZoneId.of(weight.zoneId) }.getOrDefault(zoneId),
                )
                check(
                    repository.markWeightHealthConnectSynced(
                        id = weight.id,
                        externalId = externalId,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                ) { "A synced weight could not be marked locally" }
                sent += 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep this row pending but do not let one bad legacy measurement block the rest.
                failed += 1
            }
        }
        return WeightExportSummary(sentCount = sent, failedCount = failed)
    }

    /**
     * Mirrors the complete food history into Health Connect and reports how many entries it holds.
     *
     * Ordinary automatic syncs still write only changes. Local deletes use owned-data time ranges
     * that are safe to retry even when a record was already removed in Health Connect. Legacy
     * ledger rows without a stored timestamp fall back to a checkpointed one-time rebuild.
     */
    private suspend fun pushNutrition(forceRewrite: Boolean = false): Int {
        val endDate = today
        val entries = repository.logsInRange(HEALTH_CONNECT_HISTORY_START_LOCAL_DATE, endDate.toString())
            .map { log -> log.toHealthNutritionEntry(zoneId) }
        val stored = repository.preferences.first().healthNutritionSync
        val plan = planNutritionSync(
            entries = entries,
            windowDates = nutritionSyncDatesForFullHistory(entries, stored.syncedVersions),
            synced = stored.syncedVersions,
            forceRewrite = forceRewrite,
        )
        val syncedStartTimes = nutritionSyncStartTimes(entries)
        val storedStartsByLogId = stored.syncedStartEpochMillis.values
            .asSequence()
            .flatMap { day -> day.asSequence() }
            .associate { (logId, start) -> logId to start }
        val deletedStarts = plan.deleteClientRecordIds.mapNotNull { clientRecordId ->
            val logId = clientRecordId.substringAfterLast('-').takeIf(String::isNotBlank)
            logId?.let(storedStartsByLogId::get)
        }
        val requiresFullRewrite = stored.needsFullRewrite ||
            deletedStarts.size != plan.deleteClientRecordIds.size
        val synced = HealthNutritionSyncState(
            syncedVersions = plan.syncedVersions,
            syncedStartEpochMillis = syncedStartTimes,
        )
        if (
            plan.isEmpty &&
            synced == stored &&
            !requiresFullRewrite
        ) {
            return stored.entryCount
        }

        if (requiresFullRewrite) {
            repository.appPreferencesStore.setHealthNutritionSync(
                stored.copy(needsFullRewrite = true),
            )
            healthConnect.replaceNutrition(entries)
        } else {
            val deleteRanges = deletedStarts.distinct().map { startEpochMillis ->
                val start = Instant.ofEpochMilli(startEpochMillis)
                HealthNutritionDeleteRange(start = start, end = start.plusSeconds(1))
            }
            healthConnect.deleteNutrition(deleteRanges)
            val overlapRewrites = entries.filter { entry ->
                deleteRanges.any { range ->
                    entry.startTime < range.end && entry.endTime > range.start
                }
            }
            healthConnect.writeNutrition(
                (plan.write + overlapRewrites).distinctBy { entry -> entry.logId },
            )
        }
        repository.appPreferencesStore.setHealthNutritionSync(synced)
        return synced.entryCount
    }

    /**
     * Keeps Health Connect in step with edits made after a sync.
     *
     * Every logging path ends in a food_logs write, so watching the complete history totals
     * catches new entries, corrections, deletions, restored undos and copied days alike without
     * each of them having to remember to push. The debounce lets a multi-item meal land as one
     * batch instead of one write per item.
     */
    @OptIn(FlowPreview::class)
    fun observeFoodLog() {
        scope.launch {
            repository.nutritionHistory(
                HEALTH_CONNECT_HISTORY_START_LOCAL_DATE,
                HEALTH_CONNECT_HISTORY_END_LOCAL_DATE,
            )
                .drop(1)
                .debounce(NUTRITION_SYNC_DEBOUNCE_MILLIS)
                .collect {
                    request(nutritionOnly = true)
                }
        }
    }

    private suspend fun syncNutritionLog() {
        val canWrite = runCatching {
            healthConnect.hasPermissions(HealthFeatures(writeNutrition = true))
        }.getOrDefault(false)
        if (!canWrite) return
        val shared = try {
            pushNutrition()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        mutableState.update { state ->
            state.copy(sharedNutritionEntryCount = shared)
        }
    }

    /**
     * Writes a weight the user has just logged, if Nomi may write weights at all. The local row
     * already exists; this only reports whether Health Connect now holds it too.
     */
    suspend fun mirrorNewWeight(
        localId: Long,
        kilograms: Double,
        measuredAtEpochMillis: Long,
    ): WeightMirrorResult {
        val canWriteWeight = runCatching {
            healthConnect.hasPermissions(HealthFeatures(writeWeight = true))
        }.getOrDefault(false)
        if (!canWriteWeight) return WeightMirrorResult.NOT_PERMITTED

        return mutex.withLock {
            val healthConnectId = runCatching {
                healthConnect.writeWeight(
                    kilograms = kilograms,
                    time = Instant.ofEpochMilli(measuredAtEpochMillis),
                    clientRecordId = weightClientRecordId(localId, measuredAtEpochMillis),
                    clientRecordVersion = measuredAtEpochMillis,
                    zoneId = zoneId,
                )
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                return@withLock WeightMirrorResult.FAILED
            }
            runCatching {
                repository.markWeightHealthConnectSynced(
                    id = localId,
                    externalId = healthConnectId,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }.fold(
                onSuccess = { WeightMirrorResult.SYNCED },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    WeightMirrorResult.SYNCED_BUT_UNMARKED
                },
            )
        }
    }
}

/** Nomi cannot contain a legitimate journal entry before Unix time; this covers all app history. */
private const val HEALTH_CONNECT_HISTORY_START_LOCAL_DATE = "1970-01-01"
private const val HEALTH_CONNECT_HISTORY_END_LOCAL_DATE = "9999-12-31"

/** Long enough for a whole multi-item meal to be inserted before anything is pushed. */
private const val NUTRITION_SYNC_DEBOUNCE_MILLIS = 1_500L
