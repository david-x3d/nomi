package com.nomi.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nomi.app.data.local.NomiDatabase
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.preferences.DataStoreAppPreferencesStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real SQLite transactions, including failures after the old group's deletion has begun. */
class LoggingReplacementTest {
    private lateinit var database: NomiDatabase
    private lateinit var repository: NomiRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NomiDatabase::class.java).build()
        repository = NomiRepository(database, DataStoreAppPreferencesStore(context))
    }

    @After
    fun teardown() = database.close()

    @Test
    fun replacesAnEntireGroupAndPreservesUnrelatedEntries() = runBlocking {
        val originals = listOf(log("Old one", "old"), log("Old two", "old"), log("Unrelated"))
        val ids = repository.addLogs(originals)
        repository.saveLoggingRows(listOf(log("New one", "new"), log("New two", "new")), ids[1])
        val saved = database.foodLogDao().allLogs()
        assertEquals(listOf("Unrelated", "New one", "New two"), saved.map { it.displayNameSnapshot })
        assertEquals(ids[2], saved.first().id)
    }

    @Test
    fun insertionFailureRollsBackTheDeletedGroup() = runBlocking {
        val ids = repository.addLogs(listOf(log("Old one", "old"), log("Old two", "old")))
        val before = database.foodLogDao().allLogs()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_new BEFORE INSERT ON food_logs " +
                "WHEN NEW.display_name_snapshot = 'Reject' BEGIN SELECT RAISE(ABORT, 'Reject replacement'); END",
        )
        expectFailure { repository.saveLoggingRows(listOf(log("Accepted"), log("Reject")), ids[1]) }
        assertEquals(before, database.foodLogDao().allLogs())
    }

    @Test
    fun deletionFailureNeverCommitsReplacementOrPartialGroupDeletion() = runBlocking {
        val ids = repository.addLogs(listOf(log("Old one", "old"), log("Old two", "old")))
        val before = database.foodLogDao().allLogs()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_delete BEFORE DELETE ON food_logs " +
                "WHEN OLD.id = ${ids[1]} BEGIN SELECT RAISE(ABORT, 'Reject deletion'); END",
        )
        expectFailure { repository.saveLoggingRows(listOf(log("New")), ids[0]) }
        assertEquals(before, database.foodLogDao().allLogs())
    }

    @Test
    fun missingReplacementDoesNotInsertANewEntry() = runBlocking {
        expectFailure { repository.saveLoggingRows(listOf(log("New")), 999) }
        assertTrue(database.foodLogDao().allLogs().isEmpty())
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected the transaction to fail")
        } catch (_: Exception) {
            // The postcondition checks the database, not an implementation-specific SQLite error.
        }
    }

    private fun log(name: String, group: String? = null) = FoodLogEntity(
        entryGroupId = group, mealCategory = "lunch", displayNameSnapshot = name,
        amount = 100.0, unit = "g", grams = 100.0,
        nutritionSnapshot = NutritionValues(100.0, 1.0, 20.0, 2.0),
        sourceSnapshot = NutritionSourceSnapshot(displayName = "Manual"),
        inputMethod = "manual", localDate = "2026-10-05", loggedAtEpochMillis = 1_000,
        zoneId = "Europe/Berlin", createdAtEpochMillis = 1_000, updatedAtEpochMillis = 1_000,
    )
}
