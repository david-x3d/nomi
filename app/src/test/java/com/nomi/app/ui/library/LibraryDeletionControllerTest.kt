package com.nomi.app.ui.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDeletionControllerTest {
    private fun item(kind: LibraryItemKind = LibraryItemKind.FAVORITE) =
        LibraryItem(1, kind, "Toast", "100 g", 200.0)

    @Test
    fun `delete waits for undo window and duplicate swipes delete once`() = runTest {
        val deleted = mutableListOf<LibraryItem>()
        val controller = LibraryDeletionController(this, { deleted += it }, { fail() })
        val food = item()
        controller.request(food)
        controller.request(food)
        advanceTimeBy(1_999)
        assertTrue(deleted.isEmpty())
        assertEquals(true, controller.pending.value[food.key])
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(food), deleted)
        assertTrue(controller.pending.value.isEmpty())
    }

    @Test
    fun `undo followed immediately by another swipe retains the new request`() = runTest {
        val deleted = mutableListOf<LibraryItem>()
        val controller = LibraryDeletionController(this, { deleted += it }, { fail() })
        val food = item()
        controller.request(food)
        runCurrent()
        controller.undo(food)
        controller.request(food)
        runCurrent()
        assertEquals(true, controller.pending.value[food.key])
        controller.undo(food)
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(deleted.isEmpty())
        assertTrue(controller.pending.value.isEmpty())
    }

    @Test
    fun `favorite and meal with same id are independent and recent cannot be deleted`() = runTest {
        val deleted = mutableListOf<LibraryItem>()
        val controller = LibraryDeletionController(this, { deleted += it }, { fail() })
        val favorite = item()
        val meal = item(LibraryItemKind.SAVED_MEAL)
        controller.request(favorite)
        controller.request(meal)
        controller.request(item(LibraryItemKind.RECENT))
        assertEquals(2, controller.pending.value.size)
        controller.undo(favorite)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(meal), deleted)
    }

    @Test
    fun `failure clears pending state and undo cannot cancel a database write`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var failures = 0
        val controller = LibraryDeletionController(this, { gate.await(); error("database unavailable") }, { failures++ })
        val food = item()
        controller.request(food)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(false, controller.pending.value[food.key])
        controller.undo(food)
        assertEquals(false, controller.pending.value[food.key])
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, failures)
        assertTrue(controller.pending.value.isEmpty())
    }
}
