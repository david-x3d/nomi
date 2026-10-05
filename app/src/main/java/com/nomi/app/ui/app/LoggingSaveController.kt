package com.nomi.app.ui.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Reject duplicate confirmations while allowing an older draft to finish independently. */
internal class LoggingSaveController(private val scope: CoroutineScope) {
    private val savingRevisions = mutableSetOf<Long>()

    fun save(
        revision: Long,
        write: suspend () -> Unit,
        onSuccess: suspend () -> Unit,
        onFailure: suspend (Exception) -> Unit,
        onStarted: () -> Unit = {},
    ): Boolean {
        if (!savingRevisions.add(revision)) return false
        onStarted()
        scope.launch {
            try {
                write()
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
            } finally {
                savingRevisions.remove(revision)
            }
        }
        return true
    }
}
