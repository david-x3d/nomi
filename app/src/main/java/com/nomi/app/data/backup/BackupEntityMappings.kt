package com.nomi.app.data.backup

import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodAliasEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.FoodServingEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionSourceEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.SavedMealEntity
import com.nomi.app.data.local.entity.SavedMealItemEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderSelection

internal fun AppPreferences.toBackup(): BackupPreferencesV1 = BackupPreferencesV1(
    theme = theme,
    dynamicColorEnabled = dynamicColorEnabled,
    languageTag = languageTag,
    weightUnit = weightUnit,
    heightUnit = heightUnit,
    foodResearchProvider = foodResearchProvider.toBackup(),
    foodInterpretationProvider = foodInterpretationProvider.toBackup(),
    portionChangeProvider = portionChangeProvider.toBackup(),
    visionProvider = visionProvider.toBackup(),
    reminders = reminders,
    onboardingCompleted = onboardingCompleted,
    adjustTargetFromActivity = adjustTargetFromActivity,
)

private fun ProviderSelection.toBackup(): BackupProviderSelectionV1 =
    BackupProviderSelectionV1(providerId = providerId, model = model, endpoint = endpoint)

internal fun BackupProviderSelectionV1.toPreference(): ProviderSelection = ProviderSelection(
    providerId = providerId,
    model = model,
    endpoint = endpoint,
    advancedParametersJson = null,
)

internal fun NutritionValues.toBackup(): BackupNutritionValuesV1 = BackupNutritionValuesV1(
    caloriesKcal = caloriesKcal,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    saturatedFatGrams = saturatedFatGrams,
    sodiumMilligrams = sodiumMilligrams,
)

internal fun BackupNutritionValuesV1.toEntity(): NutritionValues = NutritionValues(
    caloriesKcal = caloriesKcal,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    saturatedFatGrams = saturatedFatGrams,
    sodiumMilligrams = sodiumMilligrams,
)

internal fun NutritionSourceSnapshot.toBackup(): BackupNutritionSourceSnapshotV1 =
    BackupNutritionSourceSnapshotV1(
        kind = kind,
        providerName = providerName,
        displayName = displayName,
        externalId = externalId,
        url = url,
        citedUrls = citedUrls,
        confidence = confidence,
        productName = productName,
        servingQuantity = servingQuantity,
        servingUnit = servingUnit,
        calorieExplanation = calorieExplanation,
        retrievedAtEpochMillis = retrievedAtEpochMillis,
        verifiedAtEpochMillis = verifiedAtEpochMillis,
    )

internal fun BackupNutritionSourceSnapshotV1.toEntity(): NutritionSourceSnapshot =
    NutritionSourceSnapshot(
        kind = kind,
        providerName = providerName,
        displayName = displayName,
        externalId = externalId,
        url = url,
        citedUrls = citedUrls,
        confidence = confidence,
        productName = productName,
        servingQuantity = servingQuantity,
        servingUnit = servingUnit,
        calorieExplanation = calorieExplanation,
        retrievedAtEpochMillis = retrievedAtEpochMillis,
        verifiedAtEpochMillis = verifiedAtEpochMillis,
    )

internal fun BackupUserProfileV1.toEntity(): UserProfileEntity = UserProfileEntity(
    id = id,
    dateOfBirth = dateOfBirth,
    energyCalculationSex = energyCalculationSex,
    heightCm = heightCm,
    startingWeightKg = startingWeightKg,
    goalType = goalType,
    targetWeightKg = targetWeightKg,
    activityLevel = activityLevel,
    progressionRate = progressionRate,
    onboardingCompleted = onboardingCompleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun BackupNutritionPlanV1.toEntity(): NutritionPlanEntity = NutritionPlanEntity(
    id = id,
    profileId = profileId,
    version = version,
    effectiveFromLocalDate = effectiveFromLocalDate,
    calculationMethod = calculationMethod,
    activityMultiplier = activityMultiplier,
    bmrKcal = bmrKcal,
    maintenanceKcal = maintenanceKcal,
    goalAdjustmentKcal = goalAdjustmentKcal,
    calorieTargetKcal = calorieTargetKcal,
    proteinTargetGrams = proteinTargetGrams,
    carbohydrateTargetGrams = carbohydrateTargetGrams,
    fatTargetGrams = fatTargetGrams,
    calorieTargetCustom = calorieTargetCustom,
    proteinTargetCustom = proteinTargetCustom,
    carbohydrateTargetCustom = carbohydrateTargetCustom,
    fatTargetCustom = fatTargetCustom,
    changeReason = changeReason,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun BackupNutritionSourceV1.toEntity(): NutritionSourceEntity = NutritionSourceEntity(
    id = id,
    kind = kind,
    providerName = providerName,
    displayName = displayName,
    externalId = externalId,
    url = url,
    license = license,
    retrievedAtEpochMillis = retrievedAtEpochMillis,
    verifiedAtEpochMillis = verifiedAtEpochMillis,
)

internal fun BackupFoodV1.toEntity(): FoodEntity = FoodEntity(
    id = id,
    canonicalName = canonicalName,
    normalizedName = normalizedName,
    brand = brand,
    barcode = barcode,
    nutritionPer100g = nutritionPer100g.toEntity(),
    nutritionSourceId = nutritionSourceId,
    isUserCreated = isUserCreated,
    isEstimated = isEstimated,
    lastVerifiedAtEpochMillis = lastVerifiedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun BackupFoodServingV1.toEntity(): FoodServingEntity = FoodServingEntity(
    id = id,
    foodId = foodId,
    name = name,
    normalizedName = normalizedName,
    amount = amount,
    unit = unit,
    grams = grams,
    isDefault = isDefault,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun BackupFoodAliasV1.toEntity(): FoodAliasEntity = FoodAliasEntity(
    id = id,
    foodId = foodId,
    alias = alias,
    normalizedAlias = normalizedAlias,
    locale = locale,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun BackupFavoriteFoodV1.toEntity(): FavoriteFoodEntity = FavoriteFoodEntity(
    id = id,
    foodId = foodId,
    foodServingId = foodServingId,
    typicalAmount = typicalAmount,
    typicalUnit = typicalUnit,
    typicalGrams = typicalGrams,
    createdAtEpochMillis = createdAtEpochMillis,
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
)

internal fun BackupFoodLogV1.toEntity(): FoodLogEntity = FoodLogEntity(
    id = id,
    foodId = foodId,
    foodServingId = foodServingId,
    nutritionSourceId = nutritionSourceId,
    entryGroupId = entryGroupId,
    mealCategory = mealCategory,
    displayNameSnapshot = displayNameSnapshot,
    brandSnapshot = brandSnapshot,
    amount = amount,
    unit = unit,
    grams = grams,
    nutritionSnapshot = nutritionSnapshot.toEntity(),
    sourceSnapshot = sourceSnapshot.toEntity(),
    isEstimated = isEstimated,
    inputMethod = inputMethod,
    notes = notes,
    localDate = localDate,
    loggedAtEpochMillis = loggedAtEpochMillis,
    zoneId = zoneId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun BackupSavedMealV1.toEntity(): SavedMealEntity = SavedMealEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    notes = notes,
    defaultMealCategory = defaultMealCategory,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
)

internal fun BackupSavedMealItemV1.toEntity(): SavedMealItemEntity = SavedMealItemEntity(
    id = id,
    savedMealId = savedMealId,
    foodId = foodId,
    foodServingId = foodServingId,
    sortOrder = sortOrder,
    displayNameSnapshot = displayNameSnapshot,
    brandSnapshot = brandSnapshot,
    amount = amount,
    unit = unit,
    grams = grams,
    nutritionSnapshot = nutritionSnapshot.toEntity(),
    sourceSnapshot = sourceSnapshot.toEntity(),
    isEstimated = isEstimated,
)

internal fun BackupWeightEntryV1.toEntity(): WeightEntryEntity = WeightEntryEntity(
    id = id,
    weightKg = weightKg,
    localDate = localDate,
    measuredAtEpochMillis = measuredAtEpochMillis,
    zoneId = zoneId,
    note = note,
    source = source,
    externalId = externalId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
