package com.nomi.app.data.backup

import android.database.Cursor
import androidx.room.withTransaction
import com.nomi.app.data.local.NomiDatabase

/** Reads a transactionally consistent snapshot without exposing Room internals to the UI layer. */
internal suspend fun NomiDatabase.readBackupPayload(
    preferences: BackupPreferencesV1,
): BackupPayloadV1 = withTransaction {
    val db = openHelper.readableDatabase
    BackupPayloadV1(
        preferences = preferences,
        userProfile = db.query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1").use { cursor ->
            if (cursor.moveToFirst()) cursor.readUserProfile() else null
        },
        nutritionPlans = queryRows("nutrition_plans") { readNutritionPlan() },
        nutritionSources = queryRows("nutrition_sources") { readNutritionSource() },
        foods = queryRows("foods") { readFood() },
        foodServings = queryRows("food_servings") { readFoodServing() },
        foodAliases = queryRows("food_aliases") { readFoodAlias() },
        favoriteFoods = queryRows("favorite_foods") { readFavoriteFood() },
        foodLogs = queryRows("food_logs") { readFoodLog() },
        savedMeals = queryRows("saved_meals") { readSavedMeal() },
        savedMealItems = queryRows("saved_meal_items") { readSavedMealItem() },
        weightEntries = queryRows("weight_entries") { readWeightEntry() },
    )
}

private inline fun <T> NomiDatabase.queryRows(
    table: String,
    crossinline transform: Cursor.() -> T,
): List<T> = openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
    buildList(cursor.count) {
        while (cursor.moveToNext()) add(cursor.transform())
    }
}

/** Replaces all durable user data atomically. The caller must validate the complete payload first. */
internal suspend fun NomiDatabase.replaceWith(payload: BackupPayloadV1) {
    withTransaction {
        val db = openHelper.writableDatabase
        // Children first. Debug events are transient and are cleared, never imported.
        listOf(
            "ai_debug_events",
            "favorite_foods",
            "saved_meal_items",
            "saved_meals",
            "food_logs",
            "food_aliases",
            "food_servings",
            "foods",
            "nutrition_sources",
            "weight_entries",
            "nutrition_plans",
            "user_profiles",
        ).forEach { table -> db.execSQL("DELETE FROM $table") }

        val profileDao = profilePlanDao()
        val catalogDao = foodCatalogDao()
        val logDao = foodLogDao()
        val mealDao = savedMealDao()
        val weightDao = weightDao()

        payload.userProfile?.let { profileDao.upsertProfile(it.toEntity()) }
        payload.nutritionPlans.forEach { profileDao.insertPlan(it.toEntity()) }
        payload.nutritionSources.forEach { catalogDao.insertNutritionSource(it.toEntity()) }
        if (payload.foods.isNotEmpty()) catalogDao.upsertFoods(payload.foods.map { it.toEntity() })
        if (payload.foodServings.isNotEmpty()) {
            catalogDao.insertServings(payload.foodServings.map { it.toEntity() })
        }
        if (payload.foodAliases.isNotEmpty()) {
            catalogDao.insertAliases(payload.foodAliases.map { it.toEntity() })
        }
        payload.favoriteFoods.forEach { catalogDao.upsertFavorite(it.toEntity()) }
        if (payload.foodLogs.isNotEmpty()) logDao.insertLogs(payload.foodLogs.map { it.toEntity() })
        payload.savedMeals.forEach { mealDao.insertMeal(it.toEntity()) }
        if (payload.savedMealItems.isNotEmpty()) {
            mealDao.insertItems(payload.savedMealItems.map { it.toEntity() })
        }
        if (payload.weightEntries.isNotEmpty()) {
            weightDao.upsert(payload.weightEntries.map { it.toEntity() })
        }
    }
}

private fun Cursor.readUserProfile(): BackupUserProfileV1 = BackupUserProfileV1(
    id = int("id"),
    dateOfBirth = string("date_of_birth"),
    energyCalculationSex = nullableString("energy_calculation_sex"),
    heightCm = nullableDouble("height_cm"),
    startingWeightKg = double("starting_weight_kg"),
    goalType = string("goal_type"),
    targetWeightKg = nullableDouble("target_weight_kg"),
    activityLevel = string("activity_level"),
    progressionRate = nullableString("progression_rate"),
    onboardingCompleted = boolean("onboarding_completed"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
)

private fun Cursor.readNutritionPlan(): BackupNutritionPlanV1 = BackupNutritionPlanV1(
    id = long("id"),
    profileId = int("profile_id"),
    version = int("version"),
    effectiveFromLocalDate = string("effective_from_local_date"),
    calculationMethod = string("calculation_method"),
    activityMultiplier = nullableDouble("activity_multiplier"),
    bmrKcal = nullableDouble("bmr_kcal"),
    maintenanceKcal = nullableDouble("maintenance_kcal"),
    goalAdjustmentKcal = nullableDouble("goal_adjustment_kcal"),
    calorieTargetKcal = double("calorie_target_kcal"),
    proteinTargetGrams = double("protein_target_grams"),
    carbohydrateTargetGrams = double("carbohydrate_target_grams"),
    fatTargetGrams = double("fat_target_grams"),
    calorieTargetCustom = boolean("calorie_target_custom"),
    proteinTargetCustom = boolean("protein_target_custom"),
    carbohydrateTargetCustom = boolean("carbohydrate_target_custom"),
    fatTargetCustom = boolean("fat_target_custom"),
    changeReason = nullableString("change_reason"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
)

private fun Cursor.readNutritionSource(): BackupNutritionSourceV1 = BackupNutritionSourceV1(
    id = long("id"),
    kind = string("kind"),
    providerName = nullableString("provider_name"),
    displayName = string("display_name"),
    externalId = nullableString("external_id"),
    url = nullableString("url"),
    license = nullableString("license"),
    retrievedAtEpochMillis = long("retrieved_at_epoch_millis"),
    verifiedAtEpochMillis = nullableLong("verified_at_epoch_millis"),
)

private fun Cursor.readFood(): BackupFoodV1 = BackupFoodV1(
    id = long("id"),
    canonicalName = string("canonical_name"),
    normalizedName = string("normalized_name"),
    brand = nullableString("brand"),
    barcode = nullableString("barcode"),
    nutritionPer100g = readNutrition("per_100g_"),
    nutritionSourceId = nullableLong("nutrition_source_id"),
    isUserCreated = boolean("is_user_created"),
    isEstimated = boolean("is_estimated"),
    lastVerifiedAtEpochMillis = nullableLong("last_verified_at_epoch_millis"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
)

private fun Cursor.readFoodServing(): BackupFoodServingV1 = BackupFoodServingV1(
    id = long("id"),
    foodId = long("food_id"),
    name = string("name"),
    normalizedName = string("normalized_name"),
    amount = double("amount"),
    unit = string("unit"),
    grams = double("grams"),
    isDefault = boolean("is_default"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
)

private fun Cursor.readFoodAlias(): BackupFoodAliasV1 = BackupFoodAliasV1(
    id = long("id"),
    foodId = long("food_id"),
    alias = string("alias"),
    normalizedAlias = string("normalized_alias"),
    locale = nullableString("locale"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
)

private fun Cursor.readFavoriteFood(): BackupFavoriteFoodV1 = BackupFavoriteFoodV1(
    id = long("id"),
    foodId = long("food_id"),
    foodServingId = nullableLong("food_serving_id"),
    typicalAmount = double("typical_amount"),
    typicalUnit = string("typical_unit"),
    typicalGrams = nullableDouble("typical_grams"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    lastUsedAtEpochMillis = nullableLong("last_used_at_epoch_millis"),
)

private fun Cursor.readFoodLog(): BackupFoodLogV1 = BackupFoodLogV1(
    id = long("id"),
    foodId = nullableLong("food_id"),
    foodServingId = nullableLong("food_serving_id"),
    nutritionSourceId = nullableLong("nutrition_source_id"),
    entryGroupId = nullableString("entry_group_id"),
    mealCategory = string("meal_category"),
    displayNameSnapshot = string("display_name_snapshot"),
    brandSnapshot = nullableString("brand_snapshot"),
    amount = double("amount"),
    unit = string("unit"),
    grams = nullableDouble("grams"),
    nutritionSnapshot = readNutrition("nutrition_snapshot_"),
    sourceSnapshot = readSourceSnapshot("source_snapshot_"),
    isEstimated = boolean("is_estimated"),
    inputMethod = string("input_method"),
    notes = nullableString("notes"),
    localDate = string("local_date"),
    loggedAtEpochMillis = long("logged_at_epoch_millis"),
    zoneId = string("zone_id"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
)

private fun Cursor.readSavedMeal(): BackupSavedMealV1 = BackupSavedMealV1(
    id = long("id"),
    name = string("name"),
    normalizedName = string("normalized_name"),
    notes = nullableString("notes"),
    defaultMealCategory = nullableString("default_meal_category"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
    lastUsedAtEpochMillis = nullableLong("last_used_at_epoch_millis"),
)

private fun Cursor.readSavedMealItem(): BackupSavedMealItemV1 = BackupSavedMealItemV1(
    id = long("id"),
    savedMealId = long("saved_meal_id"),
    foodId = nullableLong("food_id"),
    foodServingId = nullableLong("food_serving_id"),
    sortOrder = int("sort_order"),
    displayNameSnapshot = string("display_name_snapshot"),
    brandSnapshot = nullableString("brand_snapshot"),
    amount = double("amount"),
    unit = string("unit"),
    grams = nullableDouble("grams"),
    nutritionSnapshot = readNutrition("nutrition_snapshot_"),
    sourceSnapshot = readSourceSnapshot("source_snapshot_"),
    isEstimated = boolean("is_estimated"),
)

private fun Cursor.readWeightEntry(): BackupWeightEntryV1 = BackupWeightEntryV1(
    id = long("id"),
    weightKg = double("weight_kg"),
    localDate = string("local_date"),
    measuredAtEpochMillis = long("measured_at_epoch_millis"),
    zoneId = string("zone_id"),
    note = nullableString("note"),
    source = string("source"),
    externalId = nullableString("external_id"),
    createdAtEpochMillis = long("created_at_epoch_millis"),
    updatedAtEpochMillis = long("updated_at_epoch_millis"),
)

private fun Cursor.readNutrition(prefix: String): BackupNutritionValuesV1 =
    BackupNutritionValuesV1(
        caloriesKcal = double(prefix + "calories_kcal"),
        proteinGrams = double(prefix + "protein_grams"),
        carbohydrateGrams = double(prefix + "carbohydrate_grams"),
        fatGrams = double(prefix + "fat_grams"),
        fiberGrams = nullableDouble(prefix + "fiber_grams"),
        sugarGrams = nullableDouble(prefix + "sugar_grams"),
        saturatedFatGrams = nullableDouble(prefix + "saturated_fat_grams"),
        sodiumMilligrams = nullableDouble(prefix + "sodium_milligrams"),
    )

private fun Cursor.readSourceSnapshot(prefix: String): BackupNutritionSourceSnapshotV1 =
    BackupNutritionSourceSnapshotV1(
        kind = string(prefix + "kind"),
        providerName = nullableString(prefix + "provider_name"),
        displayName = nullableString(prefix + "display_name"),
        externalId = nullableString(prefix + "external_id"),
        url = nullableString(prefix + "url"),
        citedUrls = nullableString(prefix + "cited_urls"),
        confidence = nullableDouble(prefix + "confidence"),
        retrievedAtEpochMillis = nullableLong(prefix + "retrieved_at_epoch_millis"),
        verifiedAtEpochMillis = nullableLong(prefix + "verified_at_epoch_millis"),
    )

private fun Cursor.index(column: String): Int = getColumnIndexOrThrow(column)
private fun Cursor.string(column: String): String = getString(index(column))
private fun Cursor.int(column: String): Int = getInt(index(column))
private fun Cursor.long(column: String): Long = getLong(index(column))
private fun Cursor.double(column: String): Double = getDouble(index(column))
private fun Cursor.boolean(column: String): Boolean = getInt(index(column)) != 0
private fun Cursor.nullableString(column: String): String? =
    index(column).let { if (isNull(it)) null else getString(it) }
private fun Cursor.nullableLong(column: String): Long? =
    index(column).let { if (isNull(it)) null else getLong(it) }
private fun Cursor.nullableDouble(column: String): Double? =
    index(column).let { if (isNull(it)) null else getDouble(it) }
