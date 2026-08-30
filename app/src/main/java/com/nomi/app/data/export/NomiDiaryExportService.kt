package com.nomi.app.data.export

import com.nomi.app.data.local.NomiDatabase
import com.nomi.app.data.local.entity.FoodLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Clock

class DiaryExportException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

data class DiaryExportResult(
    val dayCount: Int,
    val foodCount: Int,
    val bytesWritten: Int,
)

/**
 * Writes a pretty-printed JSON diary. The stream is never closed here because the Storage Access
 * Framework caller owns it.
 */
class NomiDiaryExportService(
    private val database: NomiDatabase,
    private val appVersionName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = diaryJson(),
) {
    suspend fun exportTo(output: OutputStream): DiaryExportResult = withContext(Dispatchers.IO) {
        val envelope = diaryFromLogs(
            logs = database.foodLogDao().allLogs(),
            exportedAtEpochMillis = clock.millis(),
            appVersionName = appVersionName,
        )
        val bytes = try {
            json.encodeToString(envelope).encodeToByteArray()
        } catch (error: SerializationException) {
            throw DiaryExportException("Nomi data could not be encoded safely", error)
        }
        output.write(bytes)
        output.flush()
        DiaryExportResult(
            dayCount = envelope.days.size,
            foodCount = envelope.days.sumOf { it.foods.size },
            bytesWritten = bytes.size,
        )
    }

    companion object {
        fun diaryJson(): Json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
            allowSpecialFloatingPointValues = false
            prettyPrint = true
        }
    }
}

internal fun diaryFromLogs(
    logs: List<FoodLogEntity>,
    exportedAtEpochMillis: Long,
    appVersionName: String,
): DiaryEnvelopeV1 {
    val days = logs
        .groupBy { it.localDate }
        .toSortedMap()
        .map { (date, dayLogs) ->
            val foods = dayLogs.map { log ->
                DiaryFoodV1(
                    name = log.displayNameSnapshot,
                    brand = log.brandSnapshot,
                    amount = log.amount,
                    unit = log.unit,
                    meal = log.mealCategory,
                    kcal = log.nutritionSnapshot.caloriesKcal,
                    proteinGrams = log.nutritionSnapshot.proteinGrams,
                    carbohydrateGrams = log.nutritionSnapshot.carbohydrateGrams,
                )
            }
            DiaryDayV1(
                date = date,
                foods = foods,
                kcal = foods.sumOf { it.kcal },
                proteinGrams = foods.sumOf { it.proteinGrams },
                carbohydrateGrams = foods.sumOf { it.carbohydrateGrams },
            )
        }
    return DiaryEnvelopeV1(
        exportedAtEpochMillis = exportedAtEpochMillis,
        appVersionName = appVersionName,
        days = days,
    )
}
