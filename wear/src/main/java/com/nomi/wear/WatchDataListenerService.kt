package com.nomi.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.nomi.wear.complication.CaloriesComplicationService
import com.nomi.wear.tile.NomiTileService

/**
 * Redraws the tile and the complication whenever the phone publishes a new day, even while the
 * app itself is closed. Both read the data item themselves; this only tells the system to ask.
 */
class WatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.dataItem.uri.path != WearContract.TODAY_PATH) return@forEach
            WatchToday.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)?.let {
                WatchLocale.update(this, it.languageTag)
            }
        }
        TileService.getUpdater(this).requestUpdate(NomiTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, CaloriesComplicationService::class.java))
            .requestUpdateAll()
    }
}
