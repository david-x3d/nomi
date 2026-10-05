package com.nomi.app.ui.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingRequestsTest {
    @Test
    fun `starting another capture cancels research and rejects its late citations`() = runTest {
        val requests = LoggingRequests(this)
        val old = requests.begin(testLogDestination, 7)
        val oldJob = requests.launch(old) { awaitCancellation() }
        runCurrent()
        assertTrue(requests.recordSources(old, listOf("https://old.test")))

        val destination = testLogDestination.copy(date = testLogDate.minusDays(1))
        val current = requests.begin(destination, null)

        assertTrue(oldJob.isCancelled)
        assertFalse(requests.recordSources(old, listOf("https://late.test")))
        assertTrue(current.sourceUrls.isEmpty())
        assertEquals(destination, current.destination)
        assertNull(current.replacedEntryId)
        assertEquals(7L, old.replacedEntryId)
        assertEquals(listOf("https://old.test"), old.sourceUrls)
    }

    @Test
    fun `completion of a superseded job does not lose the current job owner`() = runTest {
        val requests = LoggingRequests(this)
        val cleanup = CompletableDeferred<Unit>()
        val old = requests.begin(testLogDestination, null)
        requests.launch(old) {
            try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
        }
        runCurrent()
        val current = requests.begin(testLogDestination, null)
        val currentJob = requests.launch(current) { awaitCancellation() }
        runCurrent()
        cleanup.complete(Unit)
        runCurrent()

        requests.cancel()
        assertTrue(currentJob.isCancelled)
        assertNull(requests.current)
        assertFalse(requests.recordSources(current, listOf("https://late.test")))
    }
}
