package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.nomi.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WeightEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<WeightEntryEntity>): List<Long>

    @Upsert
    suspend fun upsert(entries: List<WeightEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: WeightEntryEntity): Int

    @Delete
    suspend fun delete(entry: WeightEntryEntity): Int

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM weight_entries WHERE id = :id LIMIT 1")
    suspend fun entry(id: Long): WeightEntryEntity?

    @Query(
        """
        SELECT * FROM weight_entries
        WHERE source = :source AND external_id IN (:externalIds)
        ORDER BY id ASC
        """,
    )
    suspend fun entriesByExternalIds(
        source: String,
        externalIds: List<String>,
    ): List<WeightEntryEntity>

    @Query(
        """
        SELECT * FROM weight_entries
        WHERE source != :source
          AND (external_id IS NULL OR TRIM(external_id) = '')
        ORDER BY measured_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun pendingHealthConnectSync(source: String): List<WeightEntryEntity>

    @Query(
        """
        UPDATE weight_entries
        SET external_id = :externalId, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setExternalId(id: Long, externalId: String, updatedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM weight_entries
        ORDER BY measured_at_epoch_millis DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query(
        """
        SELECT * FROM weight_entries
        ORDER BY measured_at_epoch_millis ASC, id ASC
        LIMIT 1
        """,
    )
    fun observeStartingWeight(): Flow<WeightEntryEntity?>

    @Query(
        """
        SELECT * FROM weight_entries
        ORDER BY measured_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int = 30): Flow<List<WeightEntryEntity>>

    @Query(
        """
        SELECT * FROM weight_entries
        WHERE local_date BETWEEN :startLocalDate AND :endLocalDate
        ORDER BY measured_at_epoch_millis ASC, id ASC
        """,
    )
    fun observeRange(
        startLocalDate: String,
        endLocalDate: String,
    ): Flow<List<WeightEntryEntity>>

    @Query(
        """
        SELECT * FROM weight_entries
        WHERE LOWER(COALESCE(note, '')) LIKE '%' || LOWER(:query) || '%'
        ORDER BY measured_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun searchNotes(query: String, limit: Int = 100): Flow<List<WeightEntryEntity>>
}
