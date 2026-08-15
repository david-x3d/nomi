package com.nomi.app.data.backup

import com.nomi.app.data.preferences.ReminderSetting
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class BackupValidationIssue(val path: String, val message: String)

class BackupValidationException(
    val issues: List<BackupValidationIssue>,
) : IllegalArgumentException(
    issues.joinToString(prefix = "Invalid Nomi backup: ", separator = "; ") {
        "${it.path}: ${it.message}"
    },
)

data class BackupSummary(
    val nutritionPlanCount: Int,
    val foodCount: Int,
    val foodLogCount: Int,
    val savedMealCount: Int,
    val weightEntryCount: Int,
) {
    val durableRowCount: Int
        get() = nutritionPlanCount + foodCount + foodLogCount + savedMealCount + weightEntryCount
}

/** Strict preflight validation. No storage mutation may occur before this returns successfully. */
object BackupValidator {
    const val MAX_BACKUP_BYTES: Int = 16 * 1024 * 1024
    private const val MAX_TOTAL_ROWS = 300_000
    private const val MAX_CATALOG_ROWS = 75_000
    private const val MAX_LOG_ROWS = 200_000
    private const val MAX_WEIGHT_ROWS = 50_000
    private const val MAX_SAVED_MEAL_ROWS = 20_000
    private const val MAX_TEXT = 4_096
    private const val MAX_NOTE = 32_768
    private const val MAX_ISSUES = 100
    private const val MAX_NUTRITION_VALUE = 1_000_000.0

    fun validate(
        envelope: BackupEnvelopeV1,
        today: LocalDate = LocalDate.now(),
    ): BackupSummary {
        val issues = mutableListOf<BackupValidationIssue>()
        fun issue(path: String, message: String) {
            if (issues.size < MAX_ISSUES) issues += BackupValidationIssue(path, message)
        }

        if (envelope.format != BackupEnvelopeV1.FORMAT) issue("format", "unsupported format")
        if (envelope.schemaVersion != BackupEnvelopeV1.SCHEMA_VERSION) {
            issue("schemaVersion", "unsupported version ${envelope.schemaVersion}")
        }
        if (envelope.exportedAtEpochMillis < 0) issue("exportedAtEpochMillis", "must be non-negative")
        validateText(envelope.appVersionName, "appVersionName", issues, required = true, max = 100)

        val p = envelope.payload
        val totalRows = listOf(
            p.nutritionPlans.size,
            p.nutritionSources.size,
            p.foods.size,
            p.foodServings.size,
            p.foodAliases.size,
            p.favoriteFoods.size,
            p.foodLogs.size,
            p.savedMeals.size,
            p.savedMealItems.size,
            p.weightEntries.size,
            if (p.userProfile == null) 0 else 1,
        ).sum()
        if (totalRows > MAX_TOTAL_ROWS) issue("payload", "contains too many rows ($totalRows)")
        if (p.foods.size > MAX_CATALOG_ROWS) issue("payload.foods", "too many foods")
        if (p.foodLogs.size > MAX_LOG_ROWS) issue("payload.foodLogs", "too many log entries")
        if (p.weightEntries.size > MAX_WEIGHT_ROWS) {
            issue("payload.weightEntries", "too many weight entries")
        }
        if (p.savedMealItems.size > MAX_SAVED_MEAL_ROWS) {
            issue("payload.savedMealItems", "too many saved-meal items")
        }

        validatePreferences(p.preferences, issues)
        validateUniqueIds("payload.nutritionPlans", p.nutritionPlans.map { it.id }, issues)
        validateUniqueIds("payload.nutritionSources", p.nutritionSources.map { it.id }, issues)
        validateUniqueIds("payload.foods", p.foods.map { it.id }, issues)
        validateUniqueIds("payload.foodServings", p.foodServings.map { it.id }, issues)
        validateUniqueIds("payload.foodAliases", p.foodAliases.map { it.id }, issues)
        validateUniqueIds("payload.favoriteFoods", p.favoriteFoods.map { it.id }, issues)
        validateUniqueIds("payload.foodLogs", p.foodLogs.map { it.id }, issues)
        validateUniqueIds("payload.savedMeals", p.savedMeals.map { it.id }, issues)
        validateUniqueIds("payload.savedMealItems", p.savedMealItems.map { it.id }, issues)
        validateUniqueIds("payload.weightEntries", p.weightEntries.map { it.id }, issues)

        val profile = p.userProfile
        if (profile == null) {
            if (p.nutritionPlans.isNotEmpty()) issue("payload.nutritionPlans", "requires a profile")
            if (p.preferences.onboardingCompleted) {
                issue("payload.preferences.onboardingCompleted", "cannot be true without a profile")
            }
        } else {
            if (profile.id != 1) issue("payload.userProfile.id", "must be the singleton id 1")
            date(profile.dateOfBirth, "payload.userProfile.dateOfBirth", issues)?.let {
                if (it.isAfter(today)) issue("payload.userProfile.dateOfBirth", "cannot be in the future")
                if (it.isBefore(today.minusYears(130))) {
                    issue("payload.userProfile.dateOfBirth", "is outside the supported range")
                }
            }
            positive(profile.startingWeightKg, "payload.userProfile.startingWeightKg", issues)
            optionalPositive(profile.heightCm, "payload.userProfile.heightCm", issues)
            optionalPositive(profile.targetWeightKg, "payload.userProfile.targetWeightKg", issues)
            validateText(profile.goalType, "payload.userProfile.goalType", issues)
            validateText(profile.activityLevel, "payload.userProfile.activityLevel", issues)
            validateText(profile.energyCalculationSex, "payload.userProfile.energyCalculationSex", issues)
            validateText(profile.progressionRate, "payload.userProfile.progressionRate", issues)
            timestamps(
                profile.createdAtEpochMillis,
                profile.updatedAtEpochMillis,
                "payload.userProfile",
                issues,
            )
            if (profile.onboardingCompleted != p.preferences.onboardingCompleted) {
                issue("payload.preferences.onboardingCompleted", "does not match the profile")
            }
            if (profile.onboardingCompleted && p.nutritionPlans.isEmpty()) {
                issue("payload.nutritionPlans", "completed onboarding requires a nutrition plan")
            }
        }

        val sourceIds = p.nutritionSources.mapTo(mutableSetOf()) { it.id }
        val foodIds = p.foods.mapTo(mutableSetOf()) { it.id }
        val servingById = p.foodServings.associateBy { it.id }
        val mealIds = p.savedMeals.mapTo(mutableSetOf()) { it.id }

        p.nutritionPlans.forEachIndexed { index, row ->
            val path = "payload.nutritionPlans[$index]"
            if (profile == null || row.profileId != profile.id) issue("$path.profileId", "unknown profile")
            if (row.version <= 0) issue("$path.version", "must be positive")
            date(row.effectiveFromLocalDate, "$path.effectiveFromLocalDate", issues)
            validateText(row.calculationMethod, "$path.calculationMethod", issues)
            optionalPositive(row.activityMultiplier, "$path.activityMultiplier", issues)
            optionalNonNegative(row.bmrKcal, "$path.bmrKcal", issues)
            optionalNonNegative(row.maintenanceKcal, "$path.maintenanceKcal", issues)
            finite(row.goalAdjustmentKcal, "$path.goalAdjustmentKcal", issues)
            positive(row.calorieTargetKcal, "$path.calorieTargetKcal", issues)
            nonNegative(row.proteinTargetGrams, "$path.proteinTargetGrams", issues)
            nonNegative(row.carbohydrateTargetGrams, "$path.carbohydrateTargetGrams", issues)
            nonNegative(row.fatTargetGrams, "$path.fatTargetGrams", issues)
            validateText(row.changeReason, "$path.changeReason", issues, max = MAX_NOTE)
            timestamp(row.createdAtEpochMillis, "$path.createdAtEpochMillis", issues)
        }
        duplicateKeys(
            "payload.nutritionPlans",
            p.nutritionPlans.map { "${it.profileId}:${it.version}" },
            issues,
        )

        p.nutritionSources.forEachIndexed { index, row ->
            val path = "payload.nutritionSources[$index]"
            validateText(row.kind, "$path.kind", issues)
            validateText(row.providerName, "$path.providerName", issues)
            validateText(row.displayName, "$path.displayName", issues)
            validateText(row.externalId, "$path.externalId", issues)
            validateText(row.url, "$path.url", issues)
            validateText(row.license, "$path.license", issues)
            timestamp(row.retrievedAtEpochMillis, "$path.retrievedAtEpochMillis", issues)
            optionalTimestamp(row.verifiedAtEpochMillis, "$path.verifiedAtEpochMillis", issues)
        }

        p.foods.forEachIndexed { index, row ->
            val path = "payload.foods[$index]"
            validateText(row.canonicalName, "$path.canonicalName", issues)
            validateText(row.normalizedName, "$path.normalizedName", issues)
            validateText(row.brand, "$path.brand", issues)
            validateText(row.barcode, "$path.barcode", issues, max = 128)
            validateNutrition(row.nutritionPer100g, "$path.nutritionPer100g", issues)
            row.nutritionSourceId?.let {
                if (it !in sourceIds) issue("$path.nutritionSourceId", "unknown nutrition source")
            }
            optionalTimestamp(row.lastVerifiedAtEpochMillis, "$path.lastVerifiedAtEpochMillis", issues)
            timestamps(row.createdAtEpochMillis, row.updatedAtEpochMillis, path, issues)
        }
        duplicateKeys("payload.foods.barcode", p.foods.mapNotNull { it.barcode }, issues)

        p.foodServings.forEachIndexed { index, row ->
            val path = "payload.foodServings[$index]"
            if (row.foodId !in foodIds) issue("$path.foodId", "unknown food")
            validateText(row.name, "$path.name", issues)
            validateText(row.normalizedName, "$path.normalizedName", issues)
            positive(row.amount, "$path.amount", issues)
            validateText(row.unit, "$path.unit", issues, max = 100)
            positive(row.grams, "$path.grams", issues)
            timestamps(row.createdAtEpochMillis, row.updatedAtEpochMillis, path, issues)
        }
        duplicateKeys(
            "payload.foodServings",
            p.foodServings.map { "${it.foodId}:${it.normalizedName}:${it.amount}:${it.unit}" },
            issues,
        )

        p.foodAliases.forEachIndexed { index, row ->
            val path = "payload.foodAliases[$index]"
            if (row.foodId !in foodIds) issue("$path.foodId", "unknown food")
            validateText(row.alias, "$path.alias", issues)
            validateText(row.normalizedAlias, "$path.normalizedAlias", issues)
            validateText(row.locale, "$path.locale", issues, max = 100)
            timestamp(row.createdAtEpochMillis, "$path.createdAtEpochMillis", issues)
        }
        duplicateKeys(
            "payload.foodAliases",
            p.foodAliases.map { "${it.foodId}:${it.normalizedAlias}" },
            issues,
        )

        p.favoriteFoods.forEachIndexed { index, row ->
            val path = "payload.favoriteFoods[$index]"
            if (row.foodId !in foodIds) issue("$path.foodId", "unknown food")
            row.foodServingId?.let { servingId ->
                val serving = servingById[servingId]
                if (serving == null) issue("$path.foodServingId", "unknown serving")
                else if (serving.foodId != row.foodId) issue("$path.foodServingId", "belongs to another food")
            }
            positive(row.typicalAmount, "$path.typicalAmount", issues)
            optionalPositive(row.typicalGrams, "$path.typicalGrams", issues)
            validateText(row.typicalUnit, "$path.typicalUnit", issues, max = 100)
            timestamp(row.createdAtEpochMillis, "$path.createdAtEpochMillis", issues)
            optionalTimestamp(row.lastUsedAtEpochMillis, "$path.lastUsedAtEpochMillis", issues)
        }
        duplicateKeys("payload.favoriteFoods.foodId", p.favoriteFoods.map { it.foodId }, issues)

        p.foodLogs.forEachIndexed { index, row ->
            val path = "payload.foodLogs[$index]"
            validateCatalogReferences(row.foodId, row.foodServingId, foodIds, servingById, path, issues)
            row.nutritionSourceId?.let {
                if (it !in sourceIds) issue("$path.nutritionSourceId", "unknown nutrition source")
            }
            validateText(row.entryGroupId, "$path.entryGroupId", issues, max = 200)
            validateText(row.originalInput, "$path.originalInput", issues, max = 4_000)
            validateText(row.mealCategory, "$path.mealCategory", issues, max = 100)
            validateText(row.displayNameSnapshot, "$path.displayNameSnapshot", issues)
            validateText(row.brandSnapshot, "$path.brandSnapshot", issues)
            positive(row.amount, "$path.amount", issues)
            optionalPositive(row.grams, "$path.grams", issues)
            validateText(row.unit, "$path.unit", issues, max = 100)
            validateNutrition(row.nutritionSnapshot, "$path.nutritionSnapshot", issues)
            validateSourceSnapshot(row.sourceSnapshot, "$path.sourceSnapshot", issues)
            validateText(row.inputMethod, "$path.inputMethod", issues, max = 100)
            validateText(row.notes, "$path.notes", issues, max = MAX_NOTE)
            date(row.localDate, "$path.localDate", issues)
            zone(row.zoneId, "$path.zoneId", issues)
            timestamp(row.loggedAtEpochMillis, "$path.loggedAtEpochMillis", issues)
            timestamps(row.createdAtEpochMillis, row.updatedAtEpochMillis, path, issues)
        }

        p.savedMeals.forEachIndexed { index, row ->
            val path = "payload.savedMeals[$index]"
            validateText(row.name, "$path.name", issues)
            validateText(row.normalizedName, "$path.normalizedName", issues)
            validateText(row.notes, "$path.notes", issues, max = MAX_NOTE)
            validateText(row.defaultMealCategory, "$path.defaultMealCategory", issues, max = 100)
            timestamps(row.createdAtEpochMillis, row.updatedAtEpochMillis, path, issues)
            optionalTimestamp(row.lastUsedAtEpochMillis, "$path.lastUsedAtEpochMillis", issues)
        }

        p.savedMealItems.forEachIndexed { index, row ->
            val path = "payload.savedMealItems[$index]"
            if (row.savedMealId !in mealIds) issue("$path.savedMealId", "unknown saved meal")
            if (row.sortOrder < 0) issue("$path.sortOrder", "must be non-negative")
            validateCatalogReferences(row.foodId, row.foodServingId, foodIds, servingById, path, issues)
            validateText(row.displayNameSnapshot, "$path.displayNameSnapshot", issues)
            validateText(row.brandSnapshot, "$path.brandSnapshot", issues)
            positive(row.amount, "$path.amount", issues)
            optionalPositive(row.grams, "$path.grams", issues)
            validateText(row.unit, "$path.unit", issues, max = 100)
            validateNutrition(row.nutritionSnapshot, "$path.nutritionSnapshot", issues)
            validateSourceSnapshot(row.sourceSnapshot, "$path.sourceSnapshot", issues)
        }
        p.savedMealItems.groupBy { it.savedMealId }.forEach { (mealId, items) ->
            val orders = items.map { it.sortOrder }.sorted()
            if (orders != orders.indices.toList()) {
                issue("payload.savedMealItems", "meal $mealId must have contiguous sort order from zero")
            }
        }
        val savedMealIdsWithItems = p.savedMealItems.asSequence().map { it.savedMealId }.toHashSet()
        p.savedMeals.forEach { meal ->
            if (meal.id !in savedMealIdsWithItems) {
                issue("payload.savedMeals", "meal ${meal.id} has no items")
            }
        }

        p.weightEntries.forEachIndexed { index, row ->
            val path = "payload.weightEntries[$index]"
            positive(row.weightKg, "$path.weightKg", issues)
            date(row.localDate, "$path.localDate", issues)
            zone(row.zoneId, "$path.zoneId", issues)
            timestamp(row.measuredAtEpochMillis, "$path.measuredAtEpochMillis", issues)
            validateText(row.note, "$path.note", issues, max = MAX_NOTE)
            validateText(row.source, "$path.source", issues, max = 100)
            validateText(row.externalId, "$path.externalId", issues)
            timestamps(row.createdAtEpochMillis, row.updatedAtEpochMillis, path, issues)
        }

        if (issues.isNotEmpty()) throw BackupValidationException(issues)
        return BackupSummary(
            nutritionPlanCount = p.nutritionPlans.size,
            foodCount = p.foods.size,
            foodLogCount = p.foodLogs.size,
            savedMealCount = p.savedMeals.size,
            weightEntryCount = p.weightEntries.size,
        )
    }

    private fun validatePreferences(
        value: BackupPreferencesV1,
        issues: MutableList<BackupValidationIssue>,
    ) {
        listOf(
            "foodResearchProvider" to value.foodResearchProvider,
            "foodInterpretationProvider" to value.foodInterpretationProvider,
            "portionChangeProvider" to value.portionChangeProvider,
            "visionProvider" to value.visionProvider,
        ).forEach { (name, provider) ->
            val path = "payload.preferences.$name"
            validateText(provider.providerId, "$path.providerId", issues, required = false, max = 200)
            validateText(provider.model, "$path.model", issues, required = false, max = 500)
            provider.endpoint?.let { endpoint ->
                validateText(endpoint, "$path.endpoint", issues, max = 2_048)
                runCatching { URI(endpoint) }.fold(
                    onSuccess = { uri ->
                        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
                            add(issues, "$path.endpoint", "must be an HTTPS endpoint")
                        }
                        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
                            add(issues, "$path.endpoint", "must not contain credentials, query, or fragment")
                        }
                    },
                    onFailure = { add(issues, "$path.endpoint", "is not a valid URI") },
                )
            }
        }
        val reminders = value.reminders
        validateReminder(reminders.breakfast, "payload.preferences.reminders.breakfast", issues)
        validateReminder(reminders.lunch, "payload.preferences.reminders.lunch", issues)
        validateReminder(reminders.dinner, "payload.preferences.reminders.dinner", issues)
        validateReminder(reminders.dailySummary, "payload.preferences.reminders.dailySummary", issues)
        validateReminder(reminders.weight, "payload.preferences.reminders.weight", issues)
    }

    private fun validateReminder(
        setting: ReminderSetting,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        if (!setting.localTime.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) ||
            runCatching { LocalTime.parse(setting.localTime) }.isFailure
        ) {
            add(issues, "$path.localTime", "must use valid HH:mm form")
        }
        if (setting.daysOfWeek.any { it !in 1..7 }) {
            add(issues, "$path.daysOfWeek", "must contain only ISO days 1 through 7")
        }
        if (setting.enabled && setting.daysOfWeek.isEmpty()) {
            add(issues, "$path.daysOfWeek", "cannot be empty when enabled")
        }
    }

    private fun validateCatalogReferences(
        foodId: Long?,
        servingId: Long?,
        foodIds: Set<Long>,
        servingById: Map<Long, BackupFoodServingV1>,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        foodId?.let { if (it !in foodIds) add(issues, "$path.foodId", "unknown food") }
        servingId?.let {
            val serving = servingById[it]
            if (serving == null) add(issues, "$path.foodServingId", "unknown serving")
            else if (foodId == null || serving.foodId != foodId) {
                add(issues, "$path.foodServingId", "requires its matching food")
            }
        }
    }

    private fun validateNutrition(
        value: BackupNutritionValuesV1,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        nonNegative(value.caloriesKcal, "$path.caloriesKcal", issues)
        nonNegative(value.proteinGrams, "$path.proteinGrams", issues)
        nonNegative(value.carbohydrateGrams, "$path.carbohydrateGrams", issues)
        nonNegative(value.fatGrams, "$path.fatGrams", issues)
        optionalNonNegative(value.fiberGrams, "$path.fiberGrams", issues)
        optionalNonNegative(value.sugarGrams, "$path.sugarGrams", issues)
        optionalNonNegative(value.saturatedFatGrams, "$path.saturatedFatGrams", issues)
        optionalNonNegative(value.sodiumMilligrams, "$path.sodiumMilligrams", issues)
    }

    private fun validateSourceSnapshot(
        value: BackupNutritionSourceSnapshotV1,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        validateText(value.kind, "$path.kind", issues)
        validateText(value.providerName, "$path.providerName", issues)
        validateText(value.displayName, "$path.displayName", issues)
        validateText(value.externalId, "$path.externalId", issues)
        validateText(value.url, "$path.url", issues)
        optionalTimestamp(value.retrievedAtEpochMillis, "$path.retrievedAtEpochMillis", issues)
        optionalTimestamp(value.verifiedAtEpochMillis, "$path.verifiedAtEpochMillis", issues)
    }

    private fun validateUniqueIds(
        path: String,
        ids: List<Long>,
        issues: MutableList<BackupValidationIssue>,
    ) {
        ids.forEachIndexed { index, id -> if (id <= 0) add(issues, "$path[$index].id", "must be positive") }
        duplicateKeys("$path.id", ids, issues)
    }

    private fun duplicateKeys(
        path: String,
        keys: List<Any>,
        issues: MutableList<BackupValidationIssue>,
    ) {
        val duplicate = keys.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) add(issues, path, "contains duplicate value ${duplicate.key}")
    }

    private fun positive(value: Double, path: String, issues: MutableList<BackupValidationIssue>) {
        if (!value.isFinite() || value <= 0.0 || value > MAX_NUTRITION_VALUE) {
            add(issues, path, "must be a finite positive value")
        }
    }

    private fun optionalPositive(value: Double?, path: String, issues: MutableList<BackupValidationIssue>) {
        value?.let { positive(it, path, issues) }
    }

    private fun nonNegative(value: Double, path: String, issues: MutableList<BackupValidationIssue>) {
        if (!value.isFinite() || value < 0.0 || value > MAX_NUTRITION_VALUE) {
            add(issues, path, "must be a finite non-negative value")
        }
    }

    private fun optionalNonNegative(
        value: Double?,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        value?.let { nonNegative(it, path, issues) }
    }

    private fun finite(value: Double?, path: String, issues: MutableList<BackupValidationIssue>) {
        if (value != null && (!value.isFinite() || kotlin.math.abs(value) > MAX_NUTRITION_VALUE)) {
            add(issues, path, "must be finite")
        }
    }

    private fun date(
        value: String,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ): LocalDate? = runCatching { LocalDate.parse(value) }.fold(
        onSuccess = { it },
        onFailure = { add(issues, path, "must be an ISO-8601 date"); null },
    )

    private fun zone(value: String, path: String, issues: MutableList<BackupValidationIssue>) {
        if (runCatching { ZoneId.of(value) }.isFailure) add(issues, path, "must be an IANA zone id")
    }

    private fun timestamp(value: Long, path: String, issues: MutableList<BackupValidationIssue>) {
        if (value < 0) add(issues, path, "must be non-negative")
    }

    private fun optionalTimestamp(value: Long?, path: String, issues: MutableList<BackupValidationIssue>) {
        value?.let { timestamp(it, path, issues) }
    }

    private fun timestamps(
        created: Long,
        updated: Long,
        path: String,
        issues: MutableList<BackupValidationIssue>,
    ) {
        timestamp(created, "$path.createdAtEpochMillis", issues)
        timestamp(updated, "$path.updatedAtEpochMillis", issues)
        if (updated < created) add(issues, "$path.updatedAtEpochMillis", "precedes creation")
    }

    private fun validateText(
        value: String?,
        path: String,
        issues: MutableList<BackupValidationIssue>,
        required: Boolean = value != null,
        max: Int = MAX_TEXT,
    ) {
        if (required && value.isNullOrBlank()) add(issues, path, "must not be blank")
        if (value != null && value.length > max) add(issues, path, "is longer than $max characters")
        if (value?.contains('\u0000') == true) add(issues, path, "contains a null character")
    }

    private fun add(issues: MutableList<BackupValidationIssue>, path: String, message: String) {
        if (issues.size < MAX_ISSUES) issues += BackupValidationIssue(path, message)
    }
}
