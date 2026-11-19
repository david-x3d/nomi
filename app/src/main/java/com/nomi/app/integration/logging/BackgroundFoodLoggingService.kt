package com.nomi.app.integration.logging

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nomi.app.NomiApplication
import com.nomi.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that keeps an active meal lookup alive when the user leaves or
 * closes Nomi, then stops itself immediately once the pending queue is idle.
 */
class BackgroundFoodLoggingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val mealText = intent?.getStringExtra(EXTRA_MEAL_TEXT)
        val notification = buildProgressNotification(this, mealText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(PROGRESS_NOTIFICATION_ID, notification)
        }

        val app = application as? NomiApplication
        if (app != null) {
            serviceScope.launch {
                app.container.pendingLoggingManager.drainQueue()
                stopForegroundCompat()
                stopSelf(startId)
            }
        } else {
            stopForegroundCompat()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "nomi_background_logging"
        private const val PROGRESS_NOTIFICATION_ID = 4201
        private const val COMPLETED_NOTIFICATION_ID = 4202
        private const val EXTRA_MEAL_TEXT = "extra_meal_text"

        fun start(context: Context, mealText: String? = null) {
            val intent = Intent(context, BackgroundFoodLoggingService::class.java).apply {
                if (mealText != null) putExtra(EXTRA_MEAL_TEXT, mealText)
            }
            // Safely start foreground service; if Android blocks background FGS start (e.g. from
            // a network callback while app is completely backgrounded), PendingLoggingManager's
            // applicationScope still runs the work without crashing.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateProgress(context: Context, mealText: String) {
            if (!canPostNotifications(context)) return
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching {
                manager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(context, mealText))
            }
        }

        fun stopIfIdle(context: Context) {
            runCatching {
                context.stopService(Intent(context, BackgroundFoodLoggingService::class.java))
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching { manager.cancel(PROGRESS_NOTIFICATION_ID) }
        }

        fun notifyCompleted(context: Context, mealText: String, addedKcal: Int) {
            if (!canPostNotifications(context)) return
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    COMPLETED_NOTIFICATION_ID,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val title = if (addedKcal > 0) "Logged · $addedKcal kcal" else "Meal logged"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nomi_notification)
                .setColor(ContextCompat.getColor(context, R.color.nomi_seed))
                .setContentTitle(title)
                .setContentText(mealText.take(80))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
                .build()
            runCatching { manager.notify(COMPLETED_NOTIFICATION_ID, notification) }
        }

        private fun buildProgressNotification(context: Context, mealText: String?): Notification {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    PROGRESS_NOTIFICATION_ID,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nomi_notification)
                .setColor(ContextCompat.getColor(context, R.color.nomi_seed))
                .setContentTitle("Nomi is logging your meal")
                .setContentText(mealText?.take(80) ?: "Researching nutrition in the background…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background food logging",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress when Nomi finishes logging a meal in the background."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun canPostNotifications(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
