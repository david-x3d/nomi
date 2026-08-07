package com.nomi.app.integration.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nomi.app.R

internal object ReminderNotifier {
    private const val CHANNEL_ID = "nomi_reminders"

    fun ensureChannel(context: Context) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    fun show(context: Context, type: ReminderType) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (!manager.areNotificationsEnabled()) return

        val copy = copyFor(context, type)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                type.requestCode,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nomi_notification)
            .setColor(context.getColor(R.color.nomi_seed))
            .setContentTitle(copy.first)
            .setContentText(copy.second)
            .setStyle(Notification.BigTextStyle().bigText(copy.second))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(type.notificationId, notification)
    }

    private fun copyFor(context: Context, type: ReminderType): Pair<String, String> = when (type) {
        ReminderType.BREAKFAST -> context.getString(R.string.reminder_breakfast_title) to
            context.getString(R.string.reminder_breakfast_body)
        ReminderType.LUNCH -> context.getString(R.string.reminder_lunch_title) to
            context.getString(R.string.reminder_lunch_body)
        ReminderType.DINNER -> context.getString(R.string.reminder_dinner_title) to
            context.getString(R.string.reminder_dinner_body)
        ReminderType.DAILY_SUMMARY -> context.getString(R.string.reminder_summary_title) to
            context.getString(R.string.reminder_summary_body)
        ReminderType.WEIGHT -> context.getString(R.string.reminder_weight_title) to
            context.getString(R.string.reminder_weight_body)
    }
}
