package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.ui.today.PendingFoodDeletion
import com.nomi.app.ui.today.TodayFoodEntry
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One owner handles the undo window, early actions and exact snapshots of grouped rows. */
internal class FoodDeletionController(
    private val scope: CoroutineScope,
    private val delete: suspend (Long) -> List<FoodLogEntity>,
    private val restore: suspend (List<FoodLogEntity>) -> Boolean,
    private val onFailure: suspend (Exception) -> Unit,
    private val undoWindowMillis: Long = 2_000L,
) {
    private enum class Action { WAIT, UNDO, DISCARD }
    private class Deletion(val entry: TodayFoodEntry, val date: LocalDate) {
        var snapshots: List<FoodLogEntity>? = null
        var action = Action.WAIT
        var restoring = false
        var expiry: Job? = null
    }
    private val deletions = mutableMapOf<Long, Deletion>()
    private val mutablePending = MutableStateFlow<Map<Long, PendingFoodDeletion>>(emptyMap())
    val pending = mutablePending.asStateFlow()

    fun request(entry: TodayFoodEntry, date: LocalDate) {
        if (entry.id <= 0 || entry.id in deletions) return
        val deletion = Deletion(entry, date)
        deletions[entry.id] = deletion
        publish()
        startExpiry(deletion)
        scope.launch {
            try {
                val snapshots = delete(entry.id)
                check(snapshots.isNotEmpty()) { "That food is no longer available" }
                deletion.snapshots = snapshots.toList()
                when (deletion.action) {
                    Action.UNDO -> restore(deletion)
                    Action.DISCARD -> finish(deletion)
                    Action.WAIT -> publish()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finish(deletion)
                onFailure(error)
            }
        }
    }

    fun undo(id: Long) {
        val deletion = deletions[id] ?: return
        if (deletion.action != Action.WAIT) return
        deletion.action = Action.UNDO
        deletion.expiry?.cancel()
        publish()
        if (deletion.snapshots != null) restore(deletion)
    }

    fun discard(id: Long) {
        val deletion = deletions[id] ?: return
        if (deletion.action == Action.UNDO) return
        deletion.action = Action.DISCARD
        if (deletion.snapshots != null) finish(deletion) else publish()
    }

    private fun restore(deletion: Deletion) {
        val snapshots = deletion.snapshots ?: return
        if (deletion.restoring) return
        deletion.restoring = true
        scope.launch {
            try {
                check(restore(snapshots)) { "The deleted food could not be restored" }
                finish(deletion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                deletion.restoring = false
                deletion.action = Action.WAIT
                publish()
                startExpiry(deletion)
                onFailure(error)
            }
        }
    }

    private fun finish(deletion: Deletion) {
        deletion.expiry?.cancel()
        if (deletions[deletion.entry.id] === deletion) deletions.remove(deletion.entry.id)
        publish()
    }

    private fun startExpiry(deletion: Deletion) {
        deletion.expiry = scope.launch {
            delay(undoWindowMillis)
            discard(deletion.entry.id)
        }
    }

    private fun publish() {
        mutablePending.value = deletions.filterValues { it.action != Action.DISCARD }.mapValues { (_, deletion) ->
            PendingFoodDeletion(deletion.entry, deletion.date, isRestoring = deletion.action == Action.UNDO)
        }
    }
}
