package com.nomi.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.nomi.app.data.local.NomiDatabase
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodAliasEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.FoodServingEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionSourceEntity
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.SavedMealEntity
import com.nomi.app.data.local.entity.SavedMealItemEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.DailyNutritionTotals
import com.nomi.app.data.local.model.FavoriteFoodWithCatalog
import com.nomi.app.data.local.model.MealNutritionTotals
import com.nomi.app.data.local.model.OnboardingPersistenceResult
import com.nomi.app.data.local.model.SavedMealWithItems
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.AppPreferencesStore
import com.nomi.app.data.preferences.DataStoreAppPreferencesStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

data class CompleteOnboardingRequest(
    val profile: UserProfileEntity,
    val plan: NutritionPlanEntity,
    val localDate: String,
    val completedAtEpochMillis: Long,
    val zoneId: String,
    val startingWeightNote: String? = "Starting weight",
)

data class AddSavedMealToLogRequest(
    val savedMealId: Long,
    val mealCategory: String,
    val localDate: String,
    val startEpochMillis: Long,
    val zoneId: String,
    val entryGroupId: String = UUID.randomUUID().toString(),
)

data class SaveLoggedMealRequest(
    val name: String,
    val normalizedName: String,
    val logIds: List<Long>,
    val notes: String? = null,
    val defaultMealCategory: String? = null,
    val createdAtEpochMillis: Long,
)

const val HEALTH_CONNECT_WEIGHT_SOURCE = "health_connect"

internal fun newExternalWeightEntries(
    entries: List<WeightEntryEntity>,
    existingExternalIds: Set<String>,
): List<WeightEntryEntity> = entries.asSequence()
    .filter { !it.externalId.isNullOrBlank() }
    .distinctBy { it.externalId }
    .filterNot { it.externalId in existingExternalIds }
    .toList()

/**
 * Cohesive local-first boundary. It deliberately returns persistence models; domain adapters can
 * live in separate mapping files without making Room depend on UI or calculation packages.
 */
class NomiRepository(
    private val database: NomiDatabase,
    val appPreferencesStore: AppPreferencesStore,
) {
    private val profileDao = database.profilePlanDao()
    private val catalogDao = database.foodCatalogDao()
    private val logDao = database.foodLogDao()
    private val mealDao = database.savedMealDao()
    private val weightDao = database.weightDao()
    private val debugDao = database.aiDebugEventDao()

    val profile: Flow<UserProfileEntity?> = profileDao.observeProfile()
    val currentPlan: Flow<NutritionPlanEntity?> = profileDao.observeCurrentPlan()
    val planHistory: Flow<List<NutritionPlanEntity>> = profileDao.observePlanHistory()
    val preferences: Flow<AppPreferences> = appPreferencesStore.preferences
    val favorites: Flow<List<FavoriteFoodWithCatalog>> = catalogDao.observeFavorites()
    val savedMeals: Flow<List<SavedMealWithItems>> = mealDao.observeMeals()
    val latestWeight: Flow<WeightEntryEntity?> = weightDao.observeLatest()
    val startingWeight: Flow<WeightEntryEntity?> = weightDao.observeStartingWeight()

    fun dayLogs(localDate: String): Flow<List<FoodLogEntity>> {
        validateLocalDate(localDate)
        return logDao.observeDayLogs(localDate)
    }

    fun dayTotals(localDate: String): Flow<DailyNutritionTotals> {
        validateLocalDate(localDate)
        return logDao.observeDayTotals(localDate)
    }

    fun mealTotals(localDate: String): Flow<List<MealNutritionTotals>> {
        validateLocalDate(localDate)
        return logDao.observeMealTotals(localDate)
    }

    fun history(startLocalDate: String, endLocalDate: String): Flow<List<FoodLogEntity>> {
        validateDateRange(startLocalDate, endLocalDate)
        return logDao.observeHistoryRange(startLocalDate, endLocalDate)
    }

    fun nutritionHistory(
        startLocalDate: String,
        endLocalDate: String,
    ): Flow<List<DailyNutritionTotals>> {
        validateDateRange(startLocalDate, endLocalDate)
        return logDao.observeDailyTotalsInRange(startLocalDate, endLocalDate)
    }

    fun searchHistory(query: String, limit: Int = 100): Flow<List<FoodLogEntity>> =
        logDao.searchHistory(query.trim(), validatedLimit(limit))

    fun searchHistory(
        query: String,
        startLocalDate: String,
        endLocalDate: String,
        limit: Int = 100,
    ): Flow<List<FoodLogEntity>> {
        validateDateRange(startLocalDate, endLocalDate)
        return logDao.searchHistoryInRange(
            query = query.trim(),
            startLocalDate = startLocalDate,
            endLocalDate = endLocalDate,
            limit = validatedLimit(limit),
        )
    }

    fun searchFoods(query: String, limit: Int = 50): Flow<List<FoodEntity>> =
        catalogDao.searchFoods(normalize(query), validatedLimit(limit))

    fun recentFoods(limit: Int = 25): Flow<List<FoodEntity>> =
        catalogDao.observeRecentFoods(validatedLimit(limit))

    fun searchSavedMeals(query: String, limit: Int = 50): Flow<List<SavedMealWithItems>> =
        mealDao.searchMeals(normalize(query), validatedLimit(limit))

    fun recentWeights(limit: Int = 30): Flow<List<WeightEntryEntity>> =
        weightDao.observeRecent(validatedLimit(limit))

    fun weights(startLocalDate: String, endLocalDate: String): Flow<List<WeightEntryEntity>> {
        validateDateRange(startLocalDate, endLocalDate)
        return weightDao.observeRange(startLocalDate, endLocalDate)
    }

    suspend fun completeOnboarding(
        request: CompleteOnboardingRequest,
    ): OnboardingPersistenceResult {
        validateLocalDate(request.localDate)
        ZoneId.of(request.zoneId)
        validateProfile(request.profile)
        validatePlan(request.plan)

        val now = request.completedAtEpochMillis
        val persisted = profileDao.completeOnboarding(
            profile = request.profile.copy(
                id = UserProfileEntity.SINGLETON_ID,
                onboardingCompleted = true,
                updatedAtEpochMillis = now,
            ),
            plan = request.plan.copy(
                id = 0,
                profileId = UserProfileEntity.SINGLETON_ID,
                createdAtEpochMillis = now,
            ),
            initialWeight = WeightEntryEntity(
                weightKg = request.profile.startingWeightKg,
                localDate = request.localDate,
                measuredAtEpochMillis = now,
                zoneId = request.zoneId,
                note = request.startingWeightNote,
                source = "onboarding",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

        // If this process dies here, the Room transaction remains complete. Retrying is safe and
        // repairs this startup hint without duplicating the first plan or weight measurement.
        appPreferencesStore.markOnboardingCompleted(completed = true, clearDraft = true)
        return persisted
    }

    suspend fun saveNewPlan(plan: NutritionPlanEntity): Long {
        validatePlan(plan)
        return database.withTransaction {
            profileDao.insertPlan(
                plan.copy(
                    id = 0,
                    profileId = UserProfileEntity.SINGLETON_ID,
                    version = profileDao.nextPlanVersion(),
                ),
            )
        }
    }

    suspend fun updateProfile(profile: UserProfileEntity): Boolean {
        validateProfile(profile)
        return profileDao.updateProfile(profile.copy(id = UserProfileEntity.SINGLETON_ID)) == 1
    }

    suspend fun addNutritionSource(source: NutritionSourceEntity): Long =
        catalogDao.insertNutritionSource(source.copy(id = 0))

    suspend fun updateNutritionSource(source: NutritionSourceEntity): Boolean =
        catalogDao.updateNutritionSource(source) == 1

    suspend fun deleteNutritionSource(source: NutritionSourceEntity): Boolean =
        catalogDao.deleteNutritionSource(source) == 1

    suspend fun addFood(
        food: FoodEntity,
        servings: List<FoodServingEntity> = emptyList(),
        aliases: List<FoodAliasEntity> = emptyList(),
    ): Long {
        validateNutrition(food.nutritionPer100g)
        return catalogDao.insertFoodWithDetails(food, servings, aliases)
    }

    suspend fun updateFood(food: FoodEntity): Boolean {
        validateNutrition(food.nutritionPer100g)
        return catalogDao.updateFood(food) == 1
    }

    suspend fun deleteFood(food: FoodEntity): Boolean = catalogDao.deleteFood(food) == 1
    suspend fun foodByBarcode(barcode: String): FoodEntity? = catalogDao.foodByBarcode(barcode.trim())

    suspend fun foodByIdentity(normalizedName: String, normalizedBrand: String?): FoodEntity? =
        catalogDao.foodByIdentity(
            normalizedName = normalize(normalizedName),
            normalizedBrand = normalizedBrand?.let(::normalize),
        )

    suspend fun addServing(serving: FoodServingEntity): Long {
        require(serving.amount > 0.0 && serving.grams > 0.0) { "Serving values must be positive" }
        return catalogDao.insertServing(serving.copy(id = 0))
    }

    suspend fun updateServing(serving: FoodServingEntity): Boolean {
        require(serving.amount > 0.0 && serving.grams > 0.0) { "Serving values must be positive" }
        return catalogDao.updateServing(serving) == 1
    }

    suspend fun deleteServing(serving: FoodServingEntity): Boolean =
        catalogDao.deleteServing(serving) == 1

    suspend fun addAlias(alias: FoodAliasEntity): Long =
        catalogDao.insertAlias(alias.copy(id = 0, normalizedAlias = normalize(alias.alias)))

    suspend fun deleteAlias(alias: FoodAliasEntity): Boolean = catalogDao.deleteAlias(alias) == 1

    suspend fun favorite(favorite: FavoriteFoodEntity): Long {
        require(favorite.typicalAmount > 0.0) { "Favorite amount must be positive" }
        require(favorite.typicalGrams == null || favorite.typicalGrams > 0.0) {
            "Favorite gram weight must be positive"
        }
        return catalogDao.upsertFavorite(favorite)
    }

    suspend fun unfavorite(foodId: Long): Boolean = catalogDao.deleteFavoriteForFood(foodId) > 0

    suspend fun addLog(log: FoodLogEntity): Long {
        validateLog(log)
        return logDao.insertLog(log.copy(id = 0))
    }

    suspend fun addLogs(logs: List<FoodLogEntity>): List<Long> {
        logs.forEach(::validateLog)
        return database.withTransaction { logDao.insertLogs(logs.map { it.copy(id = 0) }) }
    }

    suspend fun updateLog(log: FoodLogEntity): Boolean {
        validateLog(log)
        return logDao.updateLog(log) == 1
    }

    suspend fun deleteLog(log: FoodLogEntity): Boolean = logDao.deleteLog(log) == 1
    suspend fun foodLog(id: Long): FoodLogEntity? = logDao.log(id)

    /**
     * Deletes one persisted log while returning the exact immutable row needed for inline Undo.
     * Reading and deleting share a transaction so the snapshot can never describe a different
     * revision than the row that was removed.
     */
    suspend fun deleteLogForUndo(id: Long): FoodLogEntity? = database.withTransaction {
        require(id > 0) { "Only a persisted food log can be deleted" }
        val snapshot = logDao.log(id) ?: return@withTransaction null
        check(logDao.deleteLogById(id) == 1) { "Food log changed before it could be deleted" }
        snapshot
    }

    /** Restores an Undo snapshot with its original primary key and timestamps intact. */
    suspend fun restoreDeletedLog(log: FoodLogEntity): Boolean = database.withTransaction {
        require(log.id > 0) { "Only a persisted food log can be restored" }
        validateLog(log)
        val existing = logDao.log(log.id)
        if (existing != null) return@withTransaction existing == log
        logDao.insertLog(log) == log.id
    }


    suspend fun deleteLog(id: Long): Boolean = logDao.deleteLogById(id) == 1

    suspend fun copyMeal(
        sourceLocalDate: String,
        sourceMealCategory: String,
        targetLocalDate: String,
        targetMealCategory: String = sourceMealCategory,
        targetStartEpochMillis: Long,
        targetZoneId: String,
    ): List<Long> {
        validateLocalDate(sourceLocalDate)
        validateLocalDate(targetLocalDate)
        ZoneId.of(targetZoneId)
        return logDao.copyMeal(
            sourceLocalDate,
            sourceMealCategory,
            targetLocalDate,
            targetMealCategory,
            targetStartEpochMillis,
            targetZoneId,
        )
    }

    suspend fun copyDay(
        sourceLocalDate: String,
        targetLocalDate: String,
        targetStartEpochMillis: Long,
        targetZoneId: String,
    ): List<Long> {
        validateLocalDate(sourceLocalDate)
        validateLocalDate(targetLocalDate)
        ZoneId.of(targetZoneId)
        return logDao.copyDay(
            sourceLocalDate,
            targetLocalDate,
            targetStartEpochMillis,
            targetZoneId,
        )
    }

    suspend fun saveMeal(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long {
        items.forEach { validateNutrition(it.nutritionSnapshot) }
        return mealDao.saveMeal(meal, items)
    }

    /** Captures the selected logs entirely in memory, then commits one normalized meal graph. */
    suspend fun saveLoggedMeal(request: SaveLoggedMealRequest): Long = database.withTransaction {
        require(request.name.isNotBlank()) { "A saved meal needs a name" }
        require(request.logIds.isNotEmpty()) { "Select at least one log" }
        val logs = request.logIds.distinct().map { id ->
            checkNotNull(logDao.log(id)) { "Food log $id does not exist" }
        }.sortedWith(compareBy<FoodLogEntity> { it.loggedAtEpochMillis }.thenBy { it.id })
        val meal = SavedMealEntity(
            name = request.name.trim(),
            normalizedName = normalize(request.normalizedName.ifBlank { request.name }),
            notes = request.notes,
            defaultMealCategory = request.defaultMealCategory ?: logs.first().mealCategory,
            createdAtEpochMillis = request.createdAtEpochMillis,
            updatedAtEpochMillis = request.createdAtEpochMillis,
        )
        val items = logs.mapIndexed { index, log ->
            SavedMealItemEntity(
                savedMealId = 0,
                foodId = log.foodId,
                foodServingId = log.foodServingId,
                sortOrder = index,
                displayNameSnapshot = log.displayNameSnapshot,
                brandSnapshot = log.brandSnapshot,
                amount = log.amount,
                unit = log.unit,
                grams = log.grams,
                nutritionSnapshot = log.nutritionSnapshot,
                sourceSnapshot = log.sourceSnapshot,
                isEstimated = log.isEstimated,
            )
        }
        mealDao.saveMeal(meal, items)
    }

    suspend fun replaceMeal(meal: SavedMealEntity, items: List<SavedMealItemEntity>) {
        items.forEach { validateNutrition(it.nutritionSnapshot) }
        mealDao.replaceMeal(meal, items)
    }

    suspend fun deleteSavedMeal(meal: SavedMealEntity): Boolean = mealDao.deleteMeal(meal) == 1

    /** Expands immutable saved-item snapshots and inserts all resulting logs atomically. */
    suspend fun addSavedMealToLog(request: AddSavedMealToLogRequest): List<Long> {
        validateLocalDate(request.localDate)
        ZoneId.of(request.zoneId)
        require(request.mealCategory.isNotBlank()) { "A meal category is required" }
        return database.withTransaction {
            val saved = checkNotNull(mealDao.mealWithItems(request.savedMealId)) {
                "Saved meal ${request.savedMealId} does not exist"
            }
            val logs = saved.items.sortedBy { it.sortOrder }.mapIndexed { index, item ->
                FoodLogEntity(
                    foodId = item.foodId,
                    foodServingId = item.foodServingId,
                    entryGroupId = request.entryGroupId,
                    mealCategory = request.mealCategory,
                    displayNameSnapshot = item.displayNameSnapshot,
                    brandSnapshot = item.brandSnapshot,
                    amount = item.amount,
                    unit = item.unit,
                    grams = item.grams,
                    nutritionSnapshot = item.nutritionSnapshot,
                    sourceSnapshot = item.sourceSnapshot,
                    isEstimated = item.isEstimated,
                    inputMethod = "saved_meal",
                    notes = saved.meal.notes,
                    localDate = request.localDate,
                    loggedAtEpochMillis = request.startEpochMillis + index,
                    zoneId = request.zoneId,
                    createdAtEpochMillis = request.startEpochMillis,
                    updatedAtEpochMillis = request.startEpochMillis,
                )
            }
            val ids = logDao.insertLogs(logs)
            mealDao.markMealUsed(request.savedMealId, request.startEpochMillis)
            ids
        }
    }

    suspend fun addWeight(entry: WeightEntryEntity): Long {
        validateWeight(entry)
        return weightDao.insert(entry.copy(id = 0))
    }

    suspend fun importHealthConnectWeights(entries: List<WeightEntryEntity>): Int {
        if (entries.isEmpty()) return 0
        entries.forEach { entry ->
            validateWeight(entry)
            require(entry.source == HEALTH_CONNECT_WEIGHT_SOURCE) {
                "Imported Health Connect weights must use the Health Connect source"
            }
            require(!entry.externalId.isNullOrBlank()) {
                "Imported Health Connect weights require an external ID"
            }
        }
        return database.withTransaction {
            val unique = entries.distinctBy { it.externalId }
            val externalIds = unique.mapNotNull(WeightEntryEntity::externalId)
            val existing = weightDao.existingExternalIds(
                source = HEALTH_CONNECT_WEIGHT_SOURCE,
                externalIds = externalIds,
            ).toSet()
            val additions = newExternalWeightEntries(unique, existing)
            if (additions.isNotEmpty()) {
                weightDao.insertAll(additions.map { it.copy(id = 0) })
            }
            additions.size
        }
    }

    suspend fun markWeightHealthConnectSynced(
        id: Long,
        externalId: String,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(id > 0) { "A persisted weight ID is required" }
        require(externalId.isNotBlank()) { "A Health Connect record ID is required" }
        return weightDao.setExternalId(id, externalId, updatedAtEpochMillis) == 1
    }

    suspend fun updateWeight(entry: WeightEntryEntity): Boolean {
        validateWeight(entry)
        return weightDao.update(entry) == 1
    }

    suspend fun deleteWeight(entry: WeightEntryEntity): Boolean = weightDao.delete(entry) == 1

    suspend fun recordAiDebugEvent(event: AiDebugEventEntity): Long =
        debugDao.insert(event.copy(id = 0))

    fun aiDebugEvents(limit: Int = 100): Flow<List<AiDebugEventEntity>> =
        debugDao.observeLatest(validatedLimit(limit))

    suspend fun pruneAiDebugEvents(cutoffEpochMillis: Long): Int =
        debugDao.deleteOlderThan(cutoffEpochMillis)

    private fun validateProfile(profile: UserProfileEntity) {
        LocalDate.parse(profile.dateOfBirth)
        require(profile.startingWeightKg > 0.0) { "Starting weight must be positive" }
        require(profile.heightCm == null || profile.heightCm > 0.0) { "Height must be positive" }
        require(profile.targetWeightKg == null || profile.targetWeightKg > 0.0) {
            "Target weight must be positive"
        }
    }

    private fun validatePlan(plan: NutritionPlanEntity) {
        validateLocalDate(plan.effectiveFromLocalDate)
        require(plan.calorieTargetKcal > 0.0) { "Calorie target must be positive" }
        require(plan.proteinTargetGrams >= 0.0) { "Protein target cannot be negative" }
        require(plan.carbohydrateTargetGrams >= 0.0) { "Carbohydrate target cannot be negative" }
        require(plan.fatTargetGrams >= 0.0) { "Fat target cannot be negative" }
    }

    private fun validateLog(log: FoodLogEntity) {
        require(log.displayNameSnapshot.isNotBlank()) { "A food name is required" }
        require(log.amount > 0.0) { "Logged amount must be positive" }
        require(log.grams == null || log.grams > 0.0) { "Logged gram weight must be positive" }
        validateNutrition(log.nutritionSnapshot)
        validateLocalDate(log.localDate)
        ZoneId.of(log.zoneId)
    }

    private fun validateWeight(entry: WeightEntryEntity) {
        require(entry.weightKg > 0.0) { "Weight must be positive" }
        validateLocalDate(entry.localDate)
        ZoneId.of(entry.zoneId)
    }

    private fun validateNutrition(values: NutritionValues) {
        require(values.caloriesKcal >= 0.0) { "Calories cannot be negative" }
        require(values.proteinGrams >= 0.0) { "Protein cannot be negative" }
        require(values.carbohydrateGrams >= 0.0) { "Carbohydrates cannot be negative" }
        require(values.fatGrams >= 0.0) { "Fat cannot be negative" }
        require(values.fiberGrams == null || values.fiberGrams >= 0.0) { "Fiber cannot be negative" }
        require(values.sugarGrams == null || values.sugarGrams >= 0.0) { "Sugar cannot be negative" }
        require(values.saturatedFatGrams == null || values.saturatedFatGrams >= 0.0) {
            "Saturated fat cannot be negative"
        }
        require(values.sodiumMilligrams == null || values.sodiumMilligrams >= 0.0) {
            "Sodium cannot be negative"
        }
    }

    private fun validateDateRange(start: String, end: String) {
        val startDate = LocalDate.parse(start)
        val endDate = LocalDate.parse(end)
        require(!endDate.isBefore(startDate)) { "End date must not precede start date" }
    }

    private fun validateLocalDate(localDate: String) {
        LocalDate.parse(localDate)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun validatedLimit(limit: Int): Int {
        require(limit in 1..500) { "Limit must be between 1 and 500" }
        return limit
    }

    companion object {
        fun create(context: Context): NomiRepository = NomiRepository(
            database = NomiDatabase.getInstance(context),
            appPreferencesStore = DataStoreAppPreferencesStore(context),
        )
    }
}
