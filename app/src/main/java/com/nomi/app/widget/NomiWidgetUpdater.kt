package com.nomi.app.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.nomi.app.NomiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Loads today's numbers from the local database and pushes them into every placed Nomi widget.
 *
 * Updates are driven by four sources: data changes observed while the app process is alive
 * ([install]), the system's APPWIDGET_UPDATE broadcasts, a deliberately inexact midnight alarm,
 * and DATE_CHANGED/TIMEZONE_CHANGED/BOOT broadcasts. updatePeriodMillis stays 0 so the OS never
 * wakes the process just to redraw unchanged numbers.
 */
object NomiWidgetUpdater {

    const val ACTION_REFRESH: String = "com.nomi.app.action.WIDGET_REFRESH"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The date [install] observes. Moved forward by [updateAll], which the midnight alarm and the
     * DATE_CHANGED broadcast both run, so the live observer follows the day instead of watching
     * the totals of the date the process happened to start on.
     */
    private val observedDate = MutableStateFlow(todayLocalDate())

    /**
     * Fire-and-forget refresh used by widget providers and the refresh receiver. [onDone] runs
     * once the update finished (or failed) so BroadcastReceiver.goAsync results are released.
     */
    fun refreshAsync(context: Context, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                runCatching { updateAll(context) }
            } finally {
                onDone()
            }
        }
    }

    /**
     * Keeps widgets fresh whenever today's totals or the nutrition plan change while the app
     * process runs. Called once from [com.nomi.app.NomiApplication.onCreate]; the database is
     * only touched once the coroutine actually runs, keeping app start lazy.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun install(context: Context, scope: CoroutineScope = this.scope) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = (appContext as NomiApplication).container.repository
            combine(
                observedDate.flatMapLatest { repository.dayTotals(it) },
                repository.currentPlan,
            ) { totals, plan -> totals to plan }
                .distinctUntilChanged()
                .collect {
                    runCatching { updateAll(appContext) }
                }
        }
    }

    /** Refreshes every placed small and large widget from the current local data. */
    suspend fun updateAll(context: Context) {
        observedDate.value = todayLocalDate()
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext) ?: return
        val snapshot = loadSnapshot(appContext)
        if (!snapshot.hasPlan && hasNoPlacedWidgets(manager, appContext)) {
            // Nothing placed: skip work, but still arm the midnight rollover for later.
            scheduleMidnightRefresh(appContext)
            return
        }

        forEachAppWidgetId(manager, ComponentName(appContext, TodaySmallWidgetProvider::class.java)) { id ->
            manager.updateAppWidget(id, NomiWidgetViews.small(appContext, snapshot))
        }
        forEachAppWidgetId(manager, ComponentName(appContext, TodayLargeWidgetProvider::class.java)) { id ->
            manager.updateAppWidget(id, NomiWidgetViews.large(appContext, snapshot))
        }
        scheduleMidnightRefresh(appContext)
    }

    private fun hasNoPlacedWidgets(manager: AppWidgetManager, context: Context): Boolean =
        manager.getAppWidgetIds(ComponentName(context, TodaySmallWidgetProvider::class.java)).isEmpty() &&
            manager.getAppWidgetIds(ComponentName(context, TodayLargeWidgetProvider::class.java)).isEmpty()

    private inline fun forEachAppWidgetId(
        manager: AppWidgetManager,
        provider: ComponentName,
        block: (Int) -> Unit,
    ) {
        val ids = manager.getAppWidgetIds(provider) ?: return
        for (id in ids) block(id)
    }

    private suspend fun loadSnapshot(appContext: Context): NomiWidgetSnapshot {
        val repository = (appContext as NomiApplication).container.repository
        val profile = runCatching { repository.profile.first() }.getOrNull()
        if (profile?.onboardingCompleted != true) return NomiWidgetSnapshot.EMPTY

        val totals = runCatching { repository.dayTotals(todayLocalDate()).first() }.getOrNull()
        val plan = runCatching { repository.currentPlan.first() }.getOrNull()
        return NomiWidgetSnapshot(
            caloriesKcal = totals?.caloriesKcal ?: 0.0,
            calorieTargetKcal = plan?.calorieTargetKcal,
            proteinGrams = totals?.proteinGrams ?: 0.0,
            proteinTargetGrams = plan?.proteinTargetGrams,
            carbohydrateGrams = totals?.carbohydrateGrams ?: 0.0,
            carbohydrateTargetGrams = plan?.carbohydrateTargetGrams,
            fatGrams = totals?.fatGrams ?: 0.0,
            fatTargetGrams = plan?.fatTargetGrams,
        )
    }

    /**
     * Re-arms one inexact wake-up at the next local midnight so the widgets roll over to the new
     * day even when nothing else happens. setAndAllowWhileIdle keeps Nomi free of the privileged
     * exact-alarm permission, matching how meal reminders already behave.
     */
    fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zoneId = ZoneId.systemDefault()
        val nextMidnight = ZonedDateTime.of(LocalDate.now(zoneId).plusDays(1), LocalTime.MIDNIGHT, zoneId)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnight.toInstant().toEpochMilli(),
            refreshPendingIntent(context),
        )
    }

    internal fun refreshPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun todayLocalDate(): String = LocalDate.now(ZoneId.systemDefault()).toString()
}
