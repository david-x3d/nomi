package com.nomi.app.integration.wear

import com.nomi.app.data.repository.AddSavedMealToLogRequest
import com.nomi.app.di.AppContainer
import com.nomi.app.ui.app.AiDebugRecorder
import com.nomi.app.ui.app.AiProviderAccess
import com.nomi.app.ui.app.AppEvent
import com.nomi.app.ui.app.FoodLoggingCoordinator
import com.nomi.app.ui.app.LocalFoodCatalog
import com.nomi.app.ui.app.LogDestination
import com.nomi.app.ui.app.defaultMealCategory
import com.nomi.app.ui.app.loggedAtFor
import com.nomi.app.ui.app.toLog
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.today.AddFoodMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/** How a request from the watch ended, already in the user's language. */
internal data class WatchLogResult(val ok: Boolean, val message: String, val addedKcal: Double = 0.0)

/**
 * Logs what the watch sends without the phone's screen.
 *
 * A meal in words runs through the same [FoodLoggingCoordinator] the Today page uses, with its own
 * short-lived scope instead of a view model's. That keeps one research and save path: the
 * configured providers, the local catalog, the fallback order, grouping and references all behave
 * exactly as they do for a dictated meal on the phone. Quick-add entries need no provider at all.
 */
internal class WatchFoodLogger(private val container: AppContainer) {
    private val repository get() = container.repository

    suspend fun logText(text: String): WatchLogResult {
        val language = language()
        if (!isSetUp()) return failure("Finish setting up Nomi on your phone first.", language)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val before = caloriesOn(today)
        val outcome = withTimeoutOrNull(TEXT_TIMEOUT_MILLIS) { runCoordinator(text.trim(), today, zone, language) }
            ?: return failure("Nomi took too long to log that. Try again on your phone.", language)
        return outcome.errorMessage?.let { WatchLogResult(ok = false, message = it) }
            ?: WatchLogResult(ok = true, message = "", addedKcal = (caloriesOn(today) - before).coerceAtLeast(0.0))
    }

    suspend fun logQuick(kind: String, id: Long): WatchLogResult {
        val language = language()
        if (!isSetUp()) return failure("Finish setting up Nomi on your phone first.", language)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val before = caloriesOn(today)
        val added = runCatching {
            when (kind) {
                WearContract.QUICK_FAVORITE -> {
                    val favorite = repository.favorites.first().first { it.food.id == id }
                    repository.addLog(favorite.toLog(LogDestination(today, zone)))
                }
                WearContract.QUICK_SAVED_MEAL -> repository.addSavedMealToLog(
                    AddSavedMealToLogRequest(
                        savedMealId = id,
                        mealCategory = defaultMealCategory(zone).name,
                        localDate = today.toString(),
                        startEpochMillis = loggedAtFor(today, Instant.now(), zone),
                        zoneId = zone.id,
                    ),
                )
                else -> error("Unknown quick-add kind $kind")
            }
        }
        if (added.isFailure) return failure("Nomi couldn't add that item.", language)
        return WatchLogResult(ok = true, message = "", addedKcal = (caloriesOn(today) - before).coerceAtLeast(0.0))
    }

    private class Outcome(val errorMessage: String?)

    private suspend fun runCoordinator(
        text: String,
        today: LocalDate,
        zone: ZoneId,
        language: NomiLanguage,
    ): Outcome = coroutineScope {
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[kotlinx.coroutines.Job]))
        try {
            val preferences = MutableStateFlow(repository.preferences.first())
            val debug = AiDebugRecorder(container, preferences, scope)
            val providers = AiProviderAccess(container, preferences, debug, scope)
            val recentFoods = repository.recentFoods().first()
            val saved = CompletableDeferred<Unit>()
            val coordinator = FoodLoggingCoordinator(
                repository,
                LocalFoodCatalog(repository) { recentFoods },
                providers,
                debug,
                scope,
                preferences,
                destination = { LogDestination(today, zone) },
                defaultMealCategory = { defaultMealCategory(zone) },
                currentLanguage = { language },
                inUserLanguage = { NomiTranslations.translate(it, language) },
                findBarcodeProduct = container.openFoodFacts::findByBarcode,
                emitEvent = { event -> if (event == AppEvent.FoodSaved) saved.complete(Unit) },
            )
            val failed = scope.async {
                coordinator.loggingState.filterIsInstance<FoodLoggingUiState.Error>().first()
            }
            coordinator.beginLogging(AddFoodMethod.VOICE, text)
            coordinator.analyzeText()
            select {
                saved.onAwait { Outcome(errorMessage = null) }
                failed.onAwait { error -> Outcome(NomiTranslations.localizeMessage(error.message, language)) }
            }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun isSetUp(): Boolean = repository.profile.first()?.onboardingCompleted == true

    private suspend fun caloriesOn(date: LocalDate): Double =
        runCatching { repository.dayTotals(date.toString()).first().caloriesKcal }.getOrDefault(0.0)

    private suspend fun language(): NomiLanguage =
        NomiLanguage.fromTag(repository.preferences.first().languageTag) ?: NomiLanguage.matching(Locale.getDefault())

    private fun failure(english: String, language: NomiLanguage) =
        WatchLogResult(ok = false, message = NomiTranslations.translate(english, language))

    private companion object {
        /** Longer than a slow research with its fallback, shorter than a watch user will wait. */
        const val TEXT_TIMEOUT_MILLIS = 150_000L
    }
}
