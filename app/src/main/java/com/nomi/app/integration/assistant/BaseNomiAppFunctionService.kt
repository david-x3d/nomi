package com.nomi.app.integration.assistant

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.nomi.app.MainActivity
import com.nomi.app.NomiApplication
import com.nomi.app.ui.localization.NomiLanguage
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "NomiAppFunctionService",
    appFunctionXmlFileName = "nomi_app_functions",
)
abstract class BaseNomiAppFunctionService : AppFunctionService() {

    /**
     * Logs food the user ate in Nomi, the nutrition journal.
     *
     * Use this whenever the user ate something, wants to add or log food, a meal, a banana, or
     * calories in Nomi. Do not create a Google Keep or Notes item for food.
     *
     * @param food What the user ate, in their own words, for example "eine Banane" or "200 g rice".
     * @return A short confirmation including how many calories remain today.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logFood(food: String): String {
        val meal = SpokenMealText.mealText(food)?.trim().orEmpty()
        if (meal.isEmpty()) return remainingCalories()
        AssistantLogBridge.begin()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = NomiExternalIntents.ACTION_LOG_FOOD
                putExtra(NomiExternalIntents.EXTRA_FOOD, meal)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        return AssistantLogBridge.await(timeoutMs = 120_000L)
            ?: "Nomi is logging $meal."
    }

    /**
     * Tells how many calories the user has left today in Nomi.
     *
     * Use this when the user asks Nomi how many calories remain, are left, or are over target.
     * Do not create a note.
     *
     * @return Today's remaining calorie budget in Nomi.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun remainingCalories(): String = withContext(Dispatchers.IO) {
        val app = application as NomiApplication
        val today = LocalDate.now().toString()
        val logs = app.container.repository.dayLogs(today).first()
        val plan = app.container.repository.currentPlan.first()
        val prefs = app.container.repository.preferences.first()
        val consumed = logs.sumOf { it.nutritionSnapshot.caloriesKcal }
        val target = plan?.calorieTargetKcal ?: 2_000.0
        val language = NomiLanguage.fromTag(prefs.languageTag)
            ?: NomiLanguage.matching(java.util.Locale.getDefault())
        RemainingCaloriesPhrase.spoken(consumed, target, language)
    }
}
