package com.nomi.app.integration.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nomi.app.data.preferences.AppPreferencesStore
import com.nomi.app.data.preferences.ReminderPreferences
import com.nomi.app.data.preferences.ReminderSetting
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules one rolling alarm per reminder type. setAndAllowWhileIdle is deliberately inexact, so
 * Nomi does not request the privileged exact-alarm permission and Android may batch alarms to save
 * battery.
 */
class ReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val appContext = context.applicationContext
    private val alarmManager = requireNotNull(appContext.getSystemService(AlarmManager::class.java))

    fun reconcile(preferences: ReminderPreferences) {
        ReminderNotifier.ensureChannel(appContext)
        ReminderType.entries.forEach { type -> schedule(type, type.settingIn(preferences)) }
    }

    suspend fun reconcileFrom(store: AppPreferencesStore) {
        reconcile(store.preferences.first().reminders)
    }

    fun schedule(type: ReminderType, setting: ReminderSetting): Instant? {
        val pendingIntent = alarmIntent(appContext, type)
        val next = runCatching {
            NextReminderCalculator.nextOccurrence(setting, clock.instant(), zoneIdProvider())
        }.getOrNull()
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return null
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toEpochMilli(),
            pendingIntent,
        )
        return next
    }

    fun cancel(type: ReminderType) {
        alarmManager.cancel(alarmIntent(appContext, type))
    }

    fun cancelAll() {
        ReminderType.entries.forEach(::cancel)
    }

    companion object {
        const val ACTION_REMINDER: String = "com.nomi.app.action.REMINDER"
        const val EXTRA_REMINDER_TYPE: String = "reminder_type"

        internal fun alarmIntent(context: Context, type: ReminderType): PendingIntent {
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ACTION_REMINDER)
                .putExtra(EXTRA_REMINDER_TYPE, type.wireName)
            return PendingIntent.getBroadcast(
                context,
                type.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
