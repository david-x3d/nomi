package com.nomi.app.integration.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.nomi.app.NomiApplication
import com.nomi.app.integration.wear.WatchFoodLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Narrow, caller-checked IPC: no activity, no exported database or API credentials. */
class AssistantFoodProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context).applicationContext as NomiApplication
        val uid = Binder.getCallingUid()
        val packages = app.packageManager.getPackagesForUid(uid).orEmpty()
        if (callingPackage != WISP || WISP !in packages) throw SecurityException("Only Wisp may use this integration")
        if (method != "log_food" && method != "status") return failure("unsupported", "Unsupported method")
        val identity = Binder.clearCallingIdentity()
        try {
            return synchronized(lock) {
                runBlocking {
                    val repository = app.container.repository
                    if (method == "status") return@runBlocking snapshot(app)
                    val text = extras?.getString("food")?.trim().orEmpty()
                    val requestId = extras?.getString("request_id").orEmpty()
                    if (text.isEmpty() || text.length > 2000 || !requestId.matches(Regex("[A-Za-z0-9_.:-]{1,200}")))
                        return@runBlocking failure("invalid_arguments", "Invalid food or request ID")
                    val groupId = "wisp:$requestId"
                    val receipts = app.getSharedPreferences("assistant_requests", 0)
                    // Check the committed rows first: a process death after saving must not duplicate a meal.
                    val existing = repository.logsByEntryGroup(groupId)
                    if (existing.isNotEmpty()) return@runBlocking snapshot(app).apply {
                        putDouble("added_kcal", existing.sumOf { it.nutritionSnapshot.caloriesKcal })
                        putString("food", existing.map { it.displayNameSnapshot }.joinToString(", "))
                    }
                    // Persist before any provider request. An interrupted/uncertain request is never retried blindly.
                    if (receipts.contains(groupId)) return@runBlocking failure("unknown_result", "This request was already processed or interrupted. Check Nomi before retrying.")
                    check(receipts.edit().putBoolean(groupId, true).commit())
                    val result = WatchFoodLogger(app.container).logText(text, groupId)
                    if (!result.ok) return@runBlocking failure("logging_failed", result.message)
                    snapshot(app).apply {
                        putDouble("added_kcal", result.addedKcal)
                        putString("food", text)
                    }
                }
            }
        } catch (e: Exception) {
            return failure("unknown_result", "Nomi could not confirm the result. Check the diary before retrying.")
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    private suspend fun snapshot(app: NomiApplication): Bundle {
        val repository = app.container.repository
        val date = LocalDate.now().toString()
        val total = repository.dayTotals(date).first().caloriesKcal
        val baseTarget = repository.currentPlan.first()?.calorieTargetKcal
        val preferences = repository.preferences.first()
        val extra = if (preferences.adjustTargetFromActivity) runCatching {
            val health = app.container.healthConnect
            if (!health.grantedFeatures().readSteps) 0.0 else {
                val now = java.time.ZonedDateTime.now()
                val activity = health.readActivity(now.toLocalDate().atStartOfDay(now.zone).toInstant(), now.toInstant(),
                    com.nomi.app.integration.health.HealthFeatures(readSteps = true))
                val profile = repository.profile.first()
                com.nomi.app.domain.StepCalorieEstimator.estimateFromAvailableData(activity.steps,
                    repository.latestWeight.first()?.weightKg, profile?.startingWeightKg, profile?.heightCm)?.activeCaloriesKcal ?: 0.0
            }
        }.getOrDefault(0.0) else 0.0
        val target = baseTarget?.plus(extra)
        return Bundle().apply {
            putBoolean("ok", true); putString("date", date); putDouble("total_kcal", total)
            if (target != null) {
                putDouble("target_kcal", target); putDouble("remaining_kcal", target - total)
            }
        }
    }
    private fun failure(code: String, message: String) = Bundle().apply {
        putBoolean("ok", false); putString("error", code); putString("message", message)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    companion object { private const val WISP = "dev.davidx3d.wisp"; private val lock = Any() }
}
