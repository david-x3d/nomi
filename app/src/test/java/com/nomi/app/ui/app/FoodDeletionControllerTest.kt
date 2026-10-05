package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodDeletionControllerTest {
    @Test
    fun `early undo restores every group row under the id that was clicked`() = runTest {
        val deleted = CompletableDeferred<List<FoodLogEntity>>()
        val snapshots = listOf(testFoodLog(1, "meal"), testFoodLog(7, "meal"))
        var restored = emptyList<FoodLogEntity>()
        var deleteCalls = 0
        val controller = FoodDeletionController(this, { deleteCalls++; deleted.await() }, { restored = it; true }, { throw it })
        controller.request(testTodayEntry(7), testLogDate)
        controller.request(testTodayEntry(7), testLogDate)
        controller.undo(7)
        assertTrue(controller.pending.value.getValue(7).isRestoring)
        runCurrent()
        deleted.complete(snapshots)
        runCurrent()
        assertEquals(1, deleteCalls)
        assertEquals(snapshots, restored)
        assertTrue(controller.pending.value.isEmpty())
    }

    @Test
    fun `failed deletion removes the optimistic placeholder and reports the failure`() = runTest {
        val failures = mutableListOf<Exception>()
        val controller = FoodDeletionController(this, { error("Delete failed") }, { true }, { failures += it })
        controller.request(testTodayEntry(), testLogDate)
        assertEquals(testLogDate, controller.pending.value.getValue(7).date)
        runCurrent()
        assertTrue(controller.pending.value.isEmpty())
        assertEquals("Delete failed", failures.single().message)
    }

    @Test
    fun `failed restore offers undo again with the exact original snapshot`() = runTest {
        val snapshots = listOf(testFoodLog(7))
        var succeeds = false
        var restores = 0
        var failures = 0
        val controller = FoodDeletionController(this, { snapshots }, { assertEquals(snapshots, it); restores++; succeeds }, { failures++ })
        controller.request(testTodayEntry(), testLogDate)
        runCurrent()
        controller.undo(7)
        runCurrent()
        assertFalse(controller.pending.value.getValue(7).isRestoring)
        assertEquals(1, failures)
        succeeds = true
        controller.undo(7)
        runCurrent()
        assertEquals(2, restores)
        assertTrue(controller.pending.value.isEmpty())
    }

    @Test
    fun `undo window expires without a mounted screen and late undo does nothing`() = runTest {
        var restored = false
        val controller = FoodDeletionController(this, { listOf(testFoodLog(7)) }, { restored = true; true }, { throw it })
        controller.request(testTodayEntry(), testLogDate)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        controller.undo(7)
        runCurrent()
        assertTrue(controller.pending.value.isEmpty())
        assertFalse(restored)
    }

    @Test
    fun `expiry before the database finishes never turns into an early undo`() = runTest {
        val deleted = CompletableDeferred<List<FoodLogEntity>>()
        var restored = false
        val controller = FoodDeletionController(this, { deleted.await() }, { restored = true; true }, { throw it })
        controller.request(testTodayEntry(), testLogDate)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        controller.undo(7)
        deleted.complete(listOf(testFoodLog(7)))
        runCurrent()
        assertTrue(controller.pending.value.isEmpty())
        assertFalse(restored)
    }
}
