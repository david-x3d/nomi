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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<AiUsageEventEntity>)

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN estimated_cost_usd > 0 THEN estimated_cost_usd ELSE 0 END), 0.0)
        FROM ai_usage_events
        WHERE LOWER(provider_id) = 'exa' OR exa_searches > 0
        """,
    )
    fun observeAllTimeExaCostUsd(): Flow<Double>

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
