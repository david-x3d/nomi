package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity

/** Keeps the immutable database snapshot needed for a short, inline undo window. */
internal class PendingDeletedLogStore {
    private val logs = LinkedHashMap<Long, FoodLogEntity>()

    @Synchronized
    fun remember(log: FoodLogEntity): Boolean {
        require(log.id > 0) { "Only a persisted food log can be deleted" }
        if (logs.containsKey(log.id)) return false
        logs[log.id] = log
        return true
    }

    @Synchronized
    fun peek(id: Long): FoodLogEntity? = logs[id]

    @Synchronized
    fun take(id: Long): FoodLogEntity? = logs.remove(id)

    @Synchronized
    fun discard(id: Long) {
        logs.remove(id)
    }
}
