package com.nomi.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Refreshes the widgets on day rollover and related system events. Receives the inexact
 * midnight alarm plus DATE_CHANGED/TIME_SET/TIMEZONE_CHANGED/BOOT/MY_PACKAGE_REPLACED so the
 * home screen never keeps showing yesterday after a reboot or timezone hop.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        NomiWidgetUpdater.refreshAsync(context.applicationContext) { pendingResult.finish() }
    }
}
