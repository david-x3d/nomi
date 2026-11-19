package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nomi.app.data.local.entity.PendingFoodLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingFoodLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: PendingFoodLogEntity): Long

    @Query(
        """
        SELECT * FROM pending_food_logs
        WHERE local_date = :localDate
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    fun observeForDate(localDate: String): Flow<List<PendingFoodLogEntity>>

    @Query(
        """
        SELECT * FROM pending_food_logs
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    fun observeAll(): Flow<List<PendingFoodLogEntity>>

    @Query(
        """
        SELECT * FROM pending_food_logs
        WHERE status IN (:statuses)
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun listByStatuses(statuses: List<String>): List<PendingFoodLogEntity>

    @Query("SELECT * FROM pending_food_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingFoodLogEntity?

    @Query(
        """
        UPDATE pending_food_logs
        SET status = :status,
            stage_label = :stageLabel,
            error_message = :errorMessage,
            attempt_count = :attemptCount,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateState(
        id: Long,
        status: String,
        stageLabel: String?,
        errorMessage: String?,
        attemptCount: Int,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE pending_food_logs
        SET stage_label = :stageLabel,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id AND status = 'PROCESSING'
        """,
    )
    suspend fun updateStage(
        id: Long,
        stageLabel: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM pending_food_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM pending_food_logs")
    suspend fun clear(): Int
}
