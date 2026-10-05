package com.nomi.app.ui.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingSaveControllerTest {
    @Test
    fun `duplicate confirmation writes once and a new draft finishes independently`() = runTest {
        val controller = LoggingSaveController(this)
        val finishOld = CompletableDeferred<Unit>()
        val completed = mutableListOf<Long>()
        var writes = 0
        assertTrue(controller.save(1, { writes++; finishOld.await() }, { completed += 1 }, { throw it }))
        assertFalse(controller.save(1, { writes++ }, {}, { throw it }))
        assertTrue(controller.save(2, { writes++ }, { completed += 2 }, { throw it }))
        runCurrent()
        assertEquals(listOf(2L), completed)
        finishOld.complete(Unit)
        runCurrent()
        assertEquals(listOf(2L, 1L), completed)
        assertEquals(2, writes)
    }

    @Test
    fun `a failed save can be retried and never reports success`() = runTest {
        val controller = LoggingSaveController(this)
        var failures = 0
        var successes = 0
        controller.save(1, { error("Database rejected replacement") }, { successes++ }, { failures++ })
        runCurrent()
        assertEquals(0, successes)
        assertEquals(1, failures)
        assertTrue(controller.save(1, {}, { successes++ }, { failures++ }))
        runCurrent()
        assertEquals(1, successes)
    }
}
