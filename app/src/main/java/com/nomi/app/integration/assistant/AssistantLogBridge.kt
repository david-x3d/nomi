package com.nomi.app.integration.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object AssistantLogBridge {
    private val lock = Any()
    private var pending: CompletableDeferred<String>? = null

    fun begin() {
        synchronized(lock) {
            pending?.cancel()
            pending = CompletableDeferred()
        }
    }

    suspend fun await(timeoutMs: Long): String? {
        val deferred = synchronized(lock) { pending } ?: return null
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    fun complete(text: String) {
        synchronized(lock) {
            pending?.complete(text)
            pending = null
        }
    }
}
