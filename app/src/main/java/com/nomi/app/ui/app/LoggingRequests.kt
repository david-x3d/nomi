package com.nomi.app.ui.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The destination, replacement and citations belong to a lookup, never the next draft. */
internal class LoggingRequest(
    val revision: Long,
    val destination: LogDestination,
    val replacedEntryId: Long?,
) {
    @Volatile
    var sourceUrls: List<String> = emptyList()
        private set

    internal fun recordSources(urls: List<String>) {
        sourceUrls = urls.distinct()
    }
}

/** One cancellation lane for text, photos, labels and barcodes that all write the same draft. */
internal class LoggingRequests(private val scope: CoroutineScope) {
    var revision: Long = 0
        private set
    @Volatile
    var current: LoggingRequest? = null
        private set
    private var job: Job? = null

    fun begin(destination: LogDestination, replacedEntryId: Long?): LoggingRequest {
        cancel()
        return LoggingRequest(revision, destination, replacedEntryId).also { current = it }
    }

    fun isCurrent(request: LoggingRequest): Boolean = current === request

    fun recordSources(request: LoggingRequest, urls: List<String>): Boolean {
        if (!isCurrent(request)) return false
        request.recordSources(urls)
        return true
    }

    fun launch(request: LoggingRequest, block: suspend CoroutineScope.() -> Unit): Job {
        check(isCurrent(request)) { "A superseded logging request cannot start" }
        val launched = scope.launch(block = block)
        job = launched
        launched.invokeOnCompletion { if (job === launched) job = null }
        return launched
    }

    fun cancel() {
        revision += 1
        current = null
        job?.cancel()
        job = null
    }
}
