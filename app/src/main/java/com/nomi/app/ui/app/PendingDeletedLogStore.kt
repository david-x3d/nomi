package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity

/** Keeps the immutable database snapshot needed for a short, inline undo window. */
internal class PendingDeletedLogStore {
    private val logs = LinkedHashMap<Long, List<FoodLogEntity>>()

    @Synchronized
    fun remember(log: FoodLogEntity): Boolean = remember(listOf(log))

    @Synchronized
    fun remember(logsToRemember: List<FoodLogEntity>): Boolean {
        require(logsToRemember.isNotEmpty()) { "At least one persisted food log is required" }
        require(logsToRemember.all { it.id > 0 }) { "Only persisted food logs can be deleted" }
        val key = logsToRemember.first().id
        if (logs.containsKey(key)) return false
        logs[key] = logsToRemember.toList()
        return true
    }

    @Synchronized
    fun peek(id: Long): FoodLogEntity? = logs[id]?.firstOrNull()

    @Synchronized
    fun take(id: Long): FoodLogEntity? = takeAll(id)?.firstOrNull()

    @Synchronized
    fun takeAll(id: Long): List<FoodLogEntity>? = logs.remove(id)

    @Synchronized
    fun discard(id: Long) {
        logs.remove(id)
    }
}
