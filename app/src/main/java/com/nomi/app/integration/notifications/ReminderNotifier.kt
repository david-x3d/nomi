package com.nomi.app.integration.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.nomi.app.R
import java.util.Locale

internal object ReminderNotifier {
    private const val CHANNEL_ID = "nomi_reminders"

    /**
     * Resolves reminder copy against the language chosen inside Nomi rather than the system one.
     *
     * A reminder is the app talking, so it should speak the language the app is set to. A blank
     * tag means the user never chose, and the system locale is then the right answer anyway.
     */
    private fun Context.localized(languageTag: String): Context {
        if (languageTag.isBlank()) return this
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return createConfigurationContext(configuration)
    }

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

    fun show(context: Context, type: ReminderType, languageTag: String = "") {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (!manager.areNotificationsEnabled()) return

        val copy = copyFor(context.localized(languageTag), type)
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
