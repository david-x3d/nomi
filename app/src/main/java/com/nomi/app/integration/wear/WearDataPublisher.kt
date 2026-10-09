package com.nomi.app.integration.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nomi.app.NomiApplication
import com.nomi.app.ui.app.LogDestination
import com.nomi.app.ui.app.cleanNumber
import com.nomi.app.ui.app.delayUntilNextDay
import com.nomi.app.ui.app.toLog
import com.nomi.app.widget.NomiWidgetUpdater
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the watch's copy of today in step with the phone.
 *
 * The watch never reads Nomi's database: it shows the [WearContract.TODAY_PATH] data item this
 * writes, which Play services delivers whenever the watch is in reach. Nothing is written while no
 * watch has Nomi installed, so a phone without a watch does no work beyond one capability lookup.
 */
internal object WearDataPublisher {

    /** Republishes on any change to today's food, the plan, the quick-add list or the language. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun install(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = (appContext as NomiApplication).container.repository
            combine(
                currentDate().flatMapLatest { repository.dayTotals(it.toString()) },
                repository.currentPlan,
                repository.favorites,
                repository.savedMeals,
                repository.preferences.map { it.languageTag },
            ) { _, _, _, _, _ -> Unit }
                // A saved meal writes several rows in a row; one publish covers all of them.
                .debounce(500)
                .collect { runCatching { publish(appContext) } }
        }
    }

    /**
     * Writes today's data item. [force] skips the check for a watch, for when a watch has just
     * asked for it and is therefore known to be there.
     */
    suspend fun publish(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!force && !hasWatchWithNomi(appContext)) return
        val repository = (appContext as NomiApplication).container.repository
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val profile = repository.profile.first()
        val snapshot = NomiWidgetUpdater.loadSnapshot(appContext)
        val language = repository.preferences.first().languageTag

        val request = PutDataMapRequest.create(WearContract.TODAY_PATH).apply {
            dataMap.putString(WearContract.KEY_DATE, today.toString())
            dataMap.putBoolean(WearContract.KEY_HAS_PROFILE, profile?.onboardingCompleted == true)
            dataMap.putString(WearContract.KEY_LANGUAGE, language)
            dataMap.putDouble(WearContract.KEY_CALORIES, snapshot.caloriesKcal)
            dataMap.putDouble(WearContract.KEY_CALORIE_TARGET, snapshot.calorieTargetKcal ?: WearContract.NO_TARGET)
            dataMap.putDouble(WearContract.KEY_PROTEIN, snapshot.proteinGrams)
            dataMap.putDouble(WearContract.KEY_PROTEIN_TARGET, snapshot.proteinTargetGrams ?: WearContract.NO_TARGET)
            dataMap.putDouble(WearContract.KEY_CARBS, snapshot.carbohydrateGrams)
            dataMap.putDouble(WearContract.KEY_CARBS_TARGET, snapshot.carbohydrateTargetGrams ?: WearContract.NO_TARGET)
            dataMap.putDouble(WearContract.KEY_FAT, snapshot.fatGrams)
            dataMap.putDouble(WearContract.KEY_FAT_TARGET, snapshot.fatTargetGrams ?: WearContract.NO_TARGET)
            dataMap.putDataMapArrayList(WearContract.KEY_QUICK, quickItems(appContext, LogDestination(today, zone)))
            // Data items only sync when their bytes change. A request from a watch that lost its
            // copy must still arrive, so every publish is distinct.
            dataMap.putLong(WearContract.KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(appContext).putDataItem(request).await()
    }

    /** Favorites first, then saved meals: both log without a provider request. */
    private suspend fun quickItems(context: Context, destination: LogDestination): ArrayList<DataMap> {
        val repository = (context as NomiApplication).container.repository
        val favorites = repository.favorites.first().map { favorite ->
            DataMap().apply {
                putString(WearContract.KEY_QUICK_KIND, WearContract.QUICK_FAVORITE)
                putLong(WearContract.KEY_QUICK_ID, favorite.food.id)
                putString(WearContract.KEY_QUICK_TITLE, favorite.food.canonicalName)
                putString(
                    WearContract.KEY_QUICK_SUBTITLE,
                    "${favorite.favorite.typicalAmount.cleanNumber()} ${favorite.favorite.typicalUnit}",
                )
                putDouble(WearContract.KEY_QUICK_CALORIES, favorite.toLog(destination).nutritionSnapshot.caloriesKcal)
            }
        }
        val meals = repository.savedMeals.first().map { saved ->
            DataMap().apply {
                putString(WearContract.KEY_QUICK_KIND, WearContract.QUICK_SAVED_MEAL)
                putLong(WearContract.KEY_QUICK_ID, saved.meal.id)
                putString(WearContract.KEY_QUICK_TITLE, saved.meal.name)
                putString(WearContract.KEY_QUICK_SUBTITLE, "")
                putDouble(WearContract.KEY_QUICK_CALORIES, saved.items.sumOf { it.nutritionSnapshot.caloriesKcal })
            }
        }
        return ArrayList((favorites + meals).take(WearContract.MAX_QUICK_ITEMS))
    }

    private suspend fun hasWatchWithNomi(context: Context): Boolean = runCatching {
        Wearable.getCapabilityClient(context)
            .getCapability(WearContract.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
            .await()
            .nodes
            .isNotEmpty()
    }.getOrDefault(false)

    /**
     * Today's date, emitted again after each local midnight. The wait runs on uptime, so after a
     * deep sleep the new date arrives late; the watch shows a dated copy as empty until then.
     */
    private fun currentDate() = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            emit(now.toLocalDate())
            delay(delayUntilNextDay(now, zone))
        }
    }
}
