package com.nomi.app.integration.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nomi.app.data.preferences.DataStoreAppPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val type = ReminderType.fromWireName(
            intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TYPE),
        ) ?: return
        val pendingResult = goAsync()
        ReminderReceiverRuntime.scope.launch {
            try {
                val preferences = DataStoreAppPreferencesStore(context).preferences.first().reminders
                val setting = type.settingIn(preferences)
                val scheduler = ReminderScheduler(context)
                val today = ZonedDateTime.now().dayOfWeek.value
                if (setting.enabled && today in setting.daysOfWeek) {
                    ReminderNotifier.show(context.applicationContext, type)
                }
                scheduler.schedule(type, setting)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Reconciles alarms after reboot, app replacement, and local clock or time-zone changes. */
class ReminderSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        ReminderReceiverRuntime.scope.launch {
            try {
                ReminderScheduler(context).reconcileFrom(DataStoreAppPreferencesStore(context))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

private object ReminderReceiverRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
