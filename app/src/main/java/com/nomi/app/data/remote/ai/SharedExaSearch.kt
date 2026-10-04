package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiRuntimeCredential
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands every caller the answer of the first identical Exa search, for comparing models.
 *
 * Several models researching the same meal at once would otherwise each pay for the same search
 * and might each see different pages, which would compare the search rather than the readers.
 * Each distinct search has its own lock, held across the request, so parallel callers wait for
 * the one search instead of starting their own while different searches still run side by side.
 * Failures are not kept, so a later caller tries again.
 */
internal class SharedExaSearch(
    private val delegate: ExaNutritionSearchGateway,
) : ExaNutritionSearchGateway {
    private data class Key(val query: String, val resultLimit: Int, val includePageText: Boolean)

    private val registry = Mutex()
    private val locks = mutableMapOf<Key, Mutex>()
    private val answers = mutableMapOf<Key, ExaSearchResponse>()

    override suspend fun search(
        query: String,
        credential: AiRuntimeCredential,
        timeoutMillis: Long,
        resultLimit: Int,
        includePageText: Boolean,
    ): ExaSearchResponse {
        val key = Key(query, resultLimit, includePageText)
        val lock = registry.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            registry.withLock { answers[key] }
                ?: delegate.search(query, credential, timeoutMillis, resultLimit, includePageText)
                    .also { answer -> registry.withLock { answers[key] = answer } }
        }
    }
}
