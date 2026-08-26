package com.nomi.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Base for both Nomi widgets: every system trigger funnels into one asynchronous refresh.
 */
abstract class NomiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NomiWidgetUpdater.refreshAsync(context)
    }

    override fun onEnabled(context: Context) {
        NomiWidgetUpdater.refreshAsync(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        NomiWidgetUpdater.refreshAsync(context)
    }
}
