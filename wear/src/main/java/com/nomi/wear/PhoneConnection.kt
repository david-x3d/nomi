package com.nomi.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The answer the phone sent back for one logging request. */
data class LogResult(val ok: Boolean, val message: String, val addedKcal: Double)

/** Everything the watch app, tile and complication need from the phone. */
class PhoneConnection(context: Context) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    /** The phone's latest copy of today, or null before the phone has published one. */
    suspend fun loadToday(): WatchToday? = runCatching {
        // Every item this app can see, filtered by path, so the node the phone wrote from does
        // not have to be known or matched by a wildcard URI.
        val items = dataClient.dataItems.await()
        try {
            items.filter { it.uri.path == WearContract.TODAY_PATH }
                .firstNotNullOfOrNull { item -> WatchToday.fromDataMap(DataMapItem.fromDataItem(item).dataMap) }
        } finally {
            items.release()
        }
    }.onFailure { Log.w(TAG, "Could not read today from the Data Layer", it) }.getOrNull()

    /** Today, now and after every change the phone publishes. */
    fun today(): Flow<WatchToday?> = callbackFlow {
        trySend(loadToday())
        val listener = DataClient.OnDataChangedListener { events ->
            events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearContract.TODAY_PATH }
                .forEach { event ->
                    trySend(WatchToday.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap))
                }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }

    /** Answers to [logText] and [logQuick], while someone is listening. */
    fun results(): Flow<LogResult> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WearContract.LOG_RESULT_PATH) {
                val map = DataMap.fromByteArray(event.data)
                trySend(
                    LogResult(
                        ok = map.getBoolean(WearContract.KEY_OK),
                        message = map.getString(WearContract.KEY_MESSAGE).orEmpty(),
                        addedKcal = map.getDouble(WearContract.KEY_CALORIES),
                    ),
                )
            }
        }
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }

    /** Asks the phone to publish today again. False when no phone with Nomi is in reach. */
    suspend fun requestSync(): Boolean = send(WearContract.SYNC_PATH, ByteArray(0))

    suspend fun logText(text: String): Boolean = send(WearContract.LOG_TEXT_PATH, text.encodeToByteArray())

    suspend fun logQuick(item: QuickItem): Boolean = send(WearContract.LOG_QUICK_PATH, item.request.encodeToByteArray())

    private suspend fun send(path: String, payload: ByteArray): Boolean = runCatching {
        val node = Wearable.getCapabilityClient(appContext)
            .getCapability(WearContract.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }
            ?: return@runCatching false
        messageClient.sendMessage(node.id, path, payload).await()
        true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "NomiWatch"
    }
}
