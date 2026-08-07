package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nomi.app.data.local.entity.AiDebugEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDebugEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AiDebugEventEntity): Long

    @Query(
        """
        SELECT * FROM ai_debug_events
        ORDER BY created_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeLatest(limit: Int = 100): Flow<List<AiDebugEventEntity>>

    @Query(
        """
        SELECT * FROM ai_debug_events
        WHERE pipeline = :pipeline
        ORDER BY created_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observePipeline(
        pipeline: String,
        limit: Int = 100,
    ): Flow<List<AiDebugEventEntity>>

    @Query("DELETE FROM ai_debug_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM ai_debug_events WHERE created_at_epoch_millis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long): Int

    @Query("DELETE FROM ai_debug_events")
    suspend fun clear(): Int
}
