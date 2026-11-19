package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nomi.app.data.local.entity.AiUsageEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AiUsageEventEntity): Long

    @Query(
        """
        SELECT * FROM ai_usage_events
        WHERE created_at_epoch_millis >= :sinceEpochMillis
        ORDER BY created_at_epoch_millis DESC, id DESC
        """,
    )
    fun observeSince(sinceEpochMillis: Long): Flow<List<AiUsageEventEntity>>

    @Query(
        """
        SELECT * FROM ai_usage_events
        ORDER BY created_at_epoch_millis DESC, id DESC
        """,
    )
    fun observeAll(): Flow<List<AiUsageEventEntity>>

    @Query("DELETE FROM ai_usage_events WHERE created_at_epoch_millis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long): Int

    @Query("DELETE FROM ai_usage_events")
    suspend fun clear(): Int
}
