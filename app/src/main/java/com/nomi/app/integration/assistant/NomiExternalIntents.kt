package com.nomi.app.integration.assistant

object NomiExternalIntents {
    const val ACTION_LOG_FOOD: String = "com.nomi.app.action.LOG_FOOD"
    const val ACTION_CAPTURE_PHOTO: String = "com.nomi.app.action.CAPTURE_PHOTO"
    const val ACTION_SCAN_MENU: String = "com.nomi.app.action.SCAN_MENU"
    const val ACTION_SPEAK_CALORIES: String = "com.nomi.app.action.SPEAK_CALORIES"
    const val ACTION_SEARCH: String = "android.intent.action.SEARCH"
    const val ACTION_GOOGLE_SEARCH: String = "com.google.android.gms.actions.SEARCH_ACTION"
    const val ACTION_SEND: String = "android.intent.action.SEND"
    const val ACTION_VIEW: String = "android.intent.action.VIEW"

    const val SCHEME: String = "nomi"
    const val HOST_LOG: String = "log"
    const val HOST_PHOTO: String = "photo"
    const val HOST_MENU: String = "menu"
    const val HOST_CALORIES: String = "calories"

    const val EXTRA_FOOD: String = "food"
    const val EXTRA_TEXT: String = "android.intent.extra.TEXT"
    const val EXTRA_QUERY: String = "query"

    private val foodKeys = listOf(
        "foodObservation.aboutFood.name",
        "food.name",
        EXTRA_FOOD,
        "q",
        EXTRA_QUERY,
        "name",
        "text",
        EXTRA_TEXT,
    )

    fun parse(
        action: String?,
        scheme: String?,
        host: String?,
        query: Map<String, String>,
        extras: Map<String, String>,
        mimeType: String? = null,
    ): NomiExternalCommand? {
        when (action) {
            ACTION_CAPTURE_PHOTO -> return NomiExternalCommand.CapturePhoto
            ACTION_SCAN_MENU -> return NomiExternalCommand.ScanMenu
            ACTION_SPEAK_CALORIES -> return NomiExternalCommand.SpeakCalories
        }
        if (action == ACTION_VIEW && scheme.equals(SCHEME, ignoreCase = true)) {
            when (host?.lowercase()) {
                HOST_PHOTO -> return NomiExternalCommand.CapturePhoto
                HOST_MENU -> return NomiExternalCommand.ScanMenu
                HOST_CALORIES -> return NomiExternalCommand.SpeakCalories
                HOST_LOG -> return logFood(query, extras)
            }
        }
        if (action == ACTION_SEND && mimeType?.startsWith("text/") == true) {
            return spokenCommand(firstFood(query, extras))
        }
        if (action == ACTION_LOG_FOOD || action == ACTION_SEARCH || action == ACTION_GOOGLE_SEARCH) {
            return spokenCommand(firstFood(query, extras))
        }
        return null
    }

    private fun logFood(query: Map<String, String>, extras: Map<String, String>): NomiExternalCommand? =
        spokenCommand(firstFood(query, extras))

    private fun spokenCommand(raw: String?): NomiExternalCommand? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (SpokenMealText.isCalorieQuery(text)) return NomiExternalCommand.SpeakCalories
        val meal = SpokenMealText.mealText(text) ?: return null
        return NomiExternalCommand.LogFood(meal)
    }

    private fun firstFood(query: Map<String, String>, extras: Map<String, String>): String? {
        foodKeys.forEach { key ->
            query[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            extras[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }
}
