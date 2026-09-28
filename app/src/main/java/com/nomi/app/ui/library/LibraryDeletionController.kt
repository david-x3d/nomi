package com.nomi.app.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Keeps the Undo window alive when the user switches tabs or leaves the library. */
class LibraryDeletionController(
    private val scope: CoroutineScope,
    private val delete: suspend (LibraryItem) -> Unit,
    private val onFailure: suspend () -> Unit,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val mutablePending = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** True while Undo is available; false while the database write is in progress. */
    val pending = mutablePending.asStateFlow()

    fun request(item: LibraryItem) {
        if (item.kind == LibraryItemKind.RECENT || item.key in mutablePending.value) return
        mutablePending.value += item.key to true
        jobs[item.key] = scope.launch {
            try {
                delay(2_000)
                mutablePending.value += item.key to false
                delete(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure()
            } finally {
                if (jobs[item.key] == currentCoroutineContext()[Job]) {
                    mutablePending.value -= item.key
                    jobs.remove(item.key)
                }
            }
        }
    }

    fun undo(item: LibraryItem) {
        if (mutablePending.value[item.key] != true) return
        jobs.remove(item.key)?.cancel()
        mutablePending.value -= item.key
    }
}
