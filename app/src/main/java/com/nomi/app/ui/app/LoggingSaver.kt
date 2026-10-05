package com.nomi.app.ui.app

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.today.MealCategory
import java.util.UUID

internal data class LoggingSaveContext(
    val destination: LogDestination,
    val replacedEntryId: Long? = null,
    val sourceUrls: List<String> = emptyList(),
    val originalText: String? = null,
    val groupId: String = UUID.randomUUID().toString(),
)

/** Both automatic and confirmed logging use the same validation, mapping and atomic write. */
internal class LoggingSaver(
    private val cacheItem: suspend (AnalyzedFoodItem) -> Long?,
    private val cacheManual: suspend (FoodLogEntity) -> Long?,
    private val writeRows: suspend (List<FoodLogEntity>, Long?) -> List<Long>,
) {
    suspend fun saveAnalysis(analysis: FoodAnalysis, category: MealCategory, context: LoggingSaveContext) {
        val validated = ServingNutritionNormalizer.validateBeforeSave(analysis)
        val grouped = validated.items.size > 1
        val logs = validated.items.map { item ->
            item.toLog(category, "ai", context.destination, context.sourceUrls, context.originalText).copy(
                foodId = if (grouped) null else cacheItem(item),
                entryGroupId = context.groupId,
            )
        }
        writeRows(logs, context.replacedEntryId)
    }

    suspend fun saveManual(draft: ManualFoodDraft, context: LoggingSaveContext) {
        require(draft.isValid)
        val log = draft.toLog(context.destination)
        writeRows(listOf(log.copy(foodId = cacheManual(log))), context.replacedEntryId)
    }
}
