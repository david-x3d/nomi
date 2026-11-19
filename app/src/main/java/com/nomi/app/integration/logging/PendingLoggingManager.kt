package com.nomi.app.integration.logging

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.nomi.app.data.local.entity.PendingFoodLogEntity
import com.nomi.app.di.AppContainer
import com.nomi.app.ui.app.AiDebugRecorder
import com.nomi.app.ui.app.AiProviderAccess
import com.nomi.app.ui.app.AppEvent
import com.nomi.app.ui.app.FoodLoggingCoordinator
import com.nomi.app.ui.app.LocalFoodCatalog
import com.nomi.app.ui.app.LogDestination
import com.nomi.app.ui.app.defaultMealCategory
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.today.AddFoodMethod
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

object PendingLoggingStatus {
    const val QUEUED_OFFLINE = "QUEUED_OFFLINE"
    const val PROCESSING = "PROCESSING"
    const val FAILED = "FAILED"
}

/**
 * Application-scoped persistent food logging queue.
 *
 * Powers two capabilities together:
 * 1. **Exit-safe background processing** — meals submitted from Today are persisted immediately
 *    and processed on an application-scoped coroutine + foreground service, so leaving or closing
 *    the app never cancels an in-flight AI lookup.
 * 2. **Offline logging queue** — when the device has no internet connection (or drops mid-request),
 *    entries stay queued on Today and automatically drain the moment connectivity returns.
 */
class PendingLoggingManager(
    context: Context,
    private val container: AppContainer,
) {
    private val appContext = context.applicationContext
    private val repository = container.repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    private val installed = AtomicBoolean(false)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _isOnline = MutableStateFlow(checkOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _activeProcessingCount = MutableStateFlow(0)
    val activeProcessingCount: StateFlow<Int> = _activeProcessingCount.asStateFlow()

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        registerNetworkMonitor()
        scope.launch {
            recoverInterruptedEntries()
            if (_isOnline.value) {
                val pending = repository.pendingFoodLogsByStatuses(
                    listOf(PendingLoggingStatus.PROCESSING, PendingLoggingStatus.QUEUED_OFFLINE),
                )
                if (pending.isNotEmpty()) {
                    triggerDrain()
                }
            }
        }
    }

    /**
     * Enqueues a meal for immediate background processing (if online) or offline queueing.
     * Returns the persisted [PendingFoodLogEntity.id].
     */
    suspend fun submit(
        text: String,
        method: AddFoodMethod,
        localDate: LocalDate,
    ): Long {
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "Meal text cannot be blank" }
        val now = System.currentTimeMillis()
        val online = refreshOnlineState()
        val initialStatus = if (online) {
            PendingLoggingStatus.PROCESSING
        } else {
            PendingLoggingStatus.QUEUED_OFFLINE
        }
        val id = repository.enqueuePendingFoodLog(
            PendingFoodLogEntity(
                localDate = localDate.toString(),
                inputText = trimmed,
                inputMethod = method.name,
                status = initialStatus,
                stageLabel = if (online) "UNDERSTANDING_MEAL" else null,
                errorMessage = null,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        if (online) {
            triggerDrain()
        } else {
            PendingQueueJobService.scheduleWhenConnected(appContext)
        }
        return id
    }

    fun retry(id: Long) {
        scope.launch {
            val entry = repository.pendingFoodLogById(id) ?: return@launch
            val online = refreshOnlineState()
            repository.updatePendingFoodLogState(
                id = id,
                status = if (online) PendingLoggingStatus.PROCESSING else PendingLoggingStatus.QUEUED_OFFLINE,
                stageLabel = if (online) "UNDERSTANDING_MEAL" else null,
                errorMessage = null,
                attemptCount = 0,
            )
            if (online) {
                triggerDrain()
            } else {
                PendingQueueJobService.scheduleWhenConnected(appContext)
            }
        }
    }

    fun retryAllOffline() {
        scope.launch {
            if (!refreshOnlineState()) return@launch
            triggerDrain()
        }
    }

    fun cancel(id: Long) {
        activeJobs.remove(id)?.cancel()
        scope.launch {
            repository.deletePendingFoodLog(id)
        }
    }

    private fun triggerDrain() {
        scope.launch {
            val pending = repository.pendingFoodLogsByStatuses(
                listOf(PendingLoggingStatus.PROCESSING, PendingLoggingStatus.QUEUED_OFFLINE),
            )
            if (pending.isEmpty()) return@launch
            BackgroundFoodLoggingService.start(appContext, pending.first().inputText)
            drainQueue()
        }
    }

    internal suspend fun drainQueue() {
        drainMutex.withLock {
            while (true) {
                if (!refreshOnlineState()) {
                    // Demote any PROCESSING rows to QUEUED_OFFLINE while offline.
                    val inFlight = repository.pendingFoodLogsByStatuses(listOf(PendingLoggingStatus.PROCESSING))
                    for (item in inFlight) {
                        repository.updatePendingFoodLogState(
                            id = item.id,
                            status = PendingLoggingStatus.QUEUED_OFFLINE,
                            stageLabel = null,
                            errorMessage = null,
                            attemptCount = item.attemptCount,
                        )
                    }
                    val remaining = repository.pendingFoodLogsByStatuses(
                        listOf(PendingLoggingStatus.QUEUED_OFFLINE),
                    )
                    if (remaining.isNotEmpty()) {
                        PendingQueueJobService.scheduleWhenConnected(appContext)
                    }
                    break
                }
                val next = repository.pendingFoodLogsByStatuses(
                    listOf(PendingLoggingStatus.PROCESSING, PendingLoggingStatus.QUEUED_OFFLINE),
                ).firstOrNull()
                if (next == null) {
                    PendingQueueJobService.cancel(appContext)
                    break
                }

                // Apply exponential backoff before retrying an item that already failed once while online.
                if (next.attemptCount > 0) {
                    val backoffMs = retryBackoffMillis(next.attemptCount)
                    delay(backoffMs)
                    if (!refreshOnlineState()) {
                        PendingQueueJobService.scheduleWhenConnected(appContext)
                        break
                    }
                }

                _activeProcessingCount.value = 1
                BackgroundFoodLoggingService.updateProgress(appContext, next.inputText)
                val job = scope.launch {
                    processSingle(next)
                }
                activeJobs[next.id] = job
                try {
                    job.join()
                } finally {
                    activeJobs.remove(next.id)
                    _activeProcessingCount.value = 0
                }
            }
        }
    }

    private suspend fun processSingle(entry: PendingFoodLogEntity) {
        val nextAttempt = entry.attemptCount + 1
        repository.updatePendingFoodLogState(
            id = entry.id,
            status = PendingLoggingStatus.PROCESSING,
            stageLabel = "UNDERSTANDING_MEAL",
            errorMessage = null,
            attemptCount = nextAttempt,
        )
        val date = runCatching { LocalDate.parse(entry.localDate) }.getOrElse { LocalDate.now() }
        val zone = ZoneId.systemDefault()
        val method = AddFoodMethod.entries.firstOrNull { it.name == entry.inputMethod } ?: AddFoodMethod.TYPE
        val language = NomiLanguage.fromTag(repository.preferences.first().languageTag)
            ?: NomiLanguage.matching(Locale.getDefault())
        val beforeLogIds = runCatching {
            repository.dayLogs(date.toString()).first().mapTo(HashSet()) { it.id }
        }.getOrDefault(emptySet())

        val result = try {
            withTimeoutOrNull(ENTRY_TIMEOUT_MILLIS) {
                runHeadlessCoordinator(
                    entryId = entry.id,
                    text = entry.inputText,
                    method = method,
                    date = date,
                    zone = zone,
                    language = language,
                )
            } ?: Outcome.Error(
                message = NomiTranslations.translate("The request took too long. Try again.", language),
                isNetwork = !refreshOnlineState(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Outcome.Error(
                message = error.message ?: NomiTranslations.translate("Could not log this meal.", language),
                isNetwork = isLikelyNetworkFailure(error) || !refreshOnlineState(),
            )
        }

        // Verify entry was not cancelled by user while processing.
        if (repository.pendingFoodLogById(entry.id) == null) return

        when (result) {
            Outcome.Saved -> {
                repository.deletePendingFoodLog(entry.id)
                val addedKcal = runCatching {
                    repository.dayLogs(date.toString()).first()
                        .filter { it.id !in beforeLogIds }
                        .sumOf { it.nutritionSnapshot.caloriesKcal }
                        .coerceAtLeast(0.0)
                        .toInt()
                }.getOrDefault(0)
                BackgroundFoodLoggingService.notifyCompleted(
                    context = appContext,
                    mealText = entry.inputText,
                    addedKcal = addedKcal,
                )
            }
            is Outcome.Error -> {
                val stillOnline = refreshOnlineState()
                if (!stillOnline) {
                    repository.updatePendingFoodLogState(
                        id = entry.id,
                        status = PendingLoggingStatus.QUEUED_OFFLINE,
                        stageLabel = null,
                        errorMessage = null,
                        attemptCount = 0,
                    )
                    PendingQueueJobService.scheduleWhenConnected(appContext)
                } else if (result.isNetwork && nextAttempt < MAX_ONLINE_NETWORK_RETRIES) {
                    repository.updatePendingFoodLogState(
                        id = entry.id,
                        status = PendingLoggingStatus.QUEUED_OFFLINE,
                        stageLabel = null,
                        errorMessage = null,
                        attemptCount = nextAttempt,
                    )
                } else {
                    repository.updatePendingFoodLogState(
                        id = entry.id,
                        status = PendingLoggingStatus.FAILED,
                        stageLabel = null,
                        errorMessage = result.message,
                        attemptCount = nextAttempt,
                    )
                }
            }
        }
    }

    private sealed interface Outcome {
        data object Saved : Outcome
        data class Error(val message: String, val isNetwork: Boolean) : Outcome
    }

    private suspend fun runHeadlessCoordinator(
        entryId: Long,
        text: String,
        method: AddFoodMethod,
        date: LocalDate,
        zone: ZoneId,
        language: NomiLanguage,
    ): Outcome = coroutineScope {
        val workerScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        try {
            val preferences = MutableStateFlow(repository.preferences.first())
            val debug = AiDebugRecorder(container, preferences, workerScope)
            val providers = AiProviderAccess(container, preferences, debug, workerScope)
            val recentFoods = repository.recentFoods().first()
            val saved = CompletableDeferred<Unit>()
            val coordinator = FoodLoggingCoordinator(
                repository = repository,
                foodCatalog = LocalFoodCatalog(repository) { recentFoods },
                providers = providers,
                debug = debug,
                scope = workerScope,
                preferences = preferences,
                destination = { LogDestination(date, zone) },
                defaultMealCategory = { defaultMealCategory(zone) },
                currentLanguage = { language },
                inUserLanguage = { NomiTranslations.translate(it, language) },
                findBarcodeProduct = container.openFoodFacts::findByBarcode,
                emitEvent = { event -> if (event == AppEvent.FoodSaved) saved.complete(Unit) },
                usage = container.usageRecorder,
            )
            // Stream live AI processing stage into Room so Today UI reflects progress seamlessly.
            // If the coordinator ever settles on Preview without auto-saving, confirm it once
            // rather than stalling until the 3-minute timeout expires.
            var previewConfirmed = false
            val stageCollector = workerScope.launch {
                coordinator.loggingState.collect { uiState ->
                    when (uiState) {
                        is FoodLoggingUiState.Processing -> {
                            repository.updatePendingFoodLogStage(entryId, uiState.stage.name)
                        }
                        is FoodLoggingUiState.Preview -> {
                            if (!previewConfirmed) {
                                previewConfirmed = true
                                coordinator.confirmLogging()
                            }
                        }
                        else -> Unit
                    }
                }
            }
            val failed = workerScope.async {
                coordinator.loggingState.filterIsInstance<FoodLoggingUiState.Error>().first()
            }
            coordinator.beginLogging(method, text)
            coordinator.analyzeText()
            val outcome = select<Outcome> {
                saved.onAwait { Outcome.Saved }
                failed.onAwait { error ->
                    val localized = NomiTranslations.localizeMessage(error.message, language)
                    Outcome.Error(
                        message = localized,
                        isNetwork = looksLikeNetworkMessage(error.message) || !refreshOnlineState(),
                    )
                }
            }
            stageCollector.cancel()
            outcome
        } finally {
            workerScope.cancel()
        }
    }

    private suspend fun recoverInterruptedEntries() {
        val interrupted = repository.pendingFoodLogsByStatuses(listOf(PendingLoggingStatus.PROCESSING))
        val online = refreshOnlineState()
        for (entry in interrupted) {
            if (!online) {
                repository.updatePendingFoodLogState(
                    id = entry.id,
                    status = PendingLoggingStatus.QUEUED_OFFLINE,
                    stageLabel = null,
                    errorMessage = null,
                    attemptCount = entry.attemptCount,
                )
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val wasOffline = !_isOnline.value
                        _isOnline.value = true
                        if (wasOffline) {
                            scope.launch {
                                val queued = repository.pendingFoodLogsByStatuses(
                                    listOf(PendingLoggingStatus.QUEUED_OFFLINE, PendingLoggingStatus.PROCESSING),
                                )
                                if (queued.isNotEmpty()) {
                                    triggerDrain()
                                }
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkOnlineNow()
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        val wasOffline = !_isOnline.value
                        _isOnline.value = online
                        if (online && wasOffline) {
                            scope.launch {
                                val queued = repository.pendingFoodLogsByStatuses(
                                    listOf(PendingLoggingStatus.QUEUED_OFFLINE, PendingLoggingStatus.PROCESSING),
                                )
                                if (queued.isNotEmpty()) {
                                    triggerDrain()
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    private fun refreshOnlineState(): Boolean {
        val online = checkOnlineNow()
        _isOnline.value = online
        return online
    }

    private fun checkOnlineNow(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isLikelyNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is IOException) return true
            val name = current::class.java.simpleName.lowercase(Locale.ROOT)
            if ("unresolvedaddress" in name || "unknownhost" in name || "connect" in name || "socket" in name) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun looksLikeNetworkMessage(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        return "network" in lower ||
            "offline" in lower ||
            "internet" in lower ||
            "connection" in lower ||
            "resolve host" in lower ||
            "unable to reach" in lower
    }

    private companion object {
        const val ENTRY_TIMEOUT_MILLIS = 180_000L
        const val MAX_ONLINE_NETWORK_RETRIES = 3

        fun retryBackoffMillis(attemptCount: Int): Long = when (attemptCount) {
            1 -> 2_000L
            2 -> 5_000L
            else -> 15_000L
        }
    }
}
