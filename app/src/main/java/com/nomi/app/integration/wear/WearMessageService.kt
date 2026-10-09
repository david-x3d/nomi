package com.nomi.app.integration.wear

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nomi.app.NomiApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Receives what the watch sends: a meal in words, a quick-add entry, or a request for today.
 *
 * Play services keeps this service bound while a callback runs, and callbacks arrive on a
 * background thread, so each request is finished here before returning. Handing the work to
 * another scope would let Android stop the process halfway through a provider request.
 */
class WearMessageService : WearableListenerService() {
    private val logger by lazy { WatchFoodLogger((application as NomiApplication).container) }

    override fun onMessageReceived(event: MessageEvent) {
        runBlocking {
            when (event.path) {
                WearContract.SYNC_PATH -> runCatching { WearDataPublisher.publish(this@WearMessageService, force = true) }
                WearContract.LOG_TEXT_PATH -> {
                    val text = event.data.decodeToString().trim()
                    if (text.isNotEmpty()) reply(event.sourceNodeId, logger.logText(text))
                }
                WearContract.LOG_QUICK_PATH -> {
                    val (kind, id) = event.data.decodeToString().split(':', limit = 2)
                        .takeIf { it.size == 2 } ?: return@runBlocking
                    val itemId = id.toLongOrNull() ?: return@runBlocking
                    reply(event.sourceNodeId, logger.logQuick(kind, itemId))
                }
            }
        }
    }

    private suspend fun reply(nodeId: String, result: WatchLogResult) {
        val payload = DataMap().apply {
            putBoolean(WearContract.KEY_OK, result.ok)
            putString(WearContract.KEY_MESSAGE, result.message)
            putDouble(WearContract.KEY_CALORIES, result.addedKcal)
        }.toByteArray()
        runCatching {
            Wearable.getMessageClient(this).sendMessage(nodeId, WearContract.LOG_RESULT_PATH, payload).await()
        }
        // The data item would follow on its own once the database observer sees the new rows,
        // but only while this process stays alive; publishing here does not depend on that.
        runCatching { WearDataPublisher.publish(this, force = true) }
    }
}
