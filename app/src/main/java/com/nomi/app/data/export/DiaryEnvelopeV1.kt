package com.nomi.app.data.export

import kotlinx.serialization.Serializable

/**
 * Readable day-by-day food diary. Unlike a Nomi backup this file is not restoreable: it only
 * lists what was eaten and the calories, protein and carbohydrate that day added up to.
 */
@Serializable
data class DiaryEnvelopeV1(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val appVersionName: String,
    val days: List<DiaryDayV1>,
) {
    companion object {
        const val FORMAT: String = "nomi-diary"
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class DiaryDayV1(
    val date: String,
    val foods: List<DiaryFoodV1>,
    val kcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
)

@Serializable
data class DiaryFoodV1(
    val name: String,
    val brand: String? = null,
    val amount: Double,
    val unit: String,
    val meal: String,
    val kcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
)
