package com.nomi.app.integration.assistant

object SpokenMealText {
    private val assistantPrefix = Regex(
        """(?iu)^(hey google|ok google|okay google|okey google)\s*,?\s*""",
    )
    private val inNomi = Regex("""(?iu)\s+(?:in(?:to)?|in der app)\s+nomi\s*$""")
    private val addCommand = Regex(
        """(?iu)^(?:füge|fuege|add|logge|log|trage|trag|record|save)\s+(.+?)(?:\s+(?:hinzu|ein))?$""",
    )
    private val ateCommand = Regex(
        """(?iu)^(?:ich (?:habe|hab|esse|aß)|i (?:ate|eat|had|logged))\s+(.+?)(?:\s+(?:gegessen|eaten))?$""",
    )
    private val calorieQuery = Regex(
        """(?iu)(?:wie viele|how many|cuántas|combien).*(?:kcal|kalorien|calories)|(?:kcal|kalorien|calories).*(?:übrig|uebrig|left|remaining|verbleib|restantes|kvar|over)""",
    )

    fun isCalorieQuery(raw: String): Boolean = calorieQuery.containsMatchIn(raw.trim())

    fun mealText(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty() || isCalorieQuery(text)) return null
        text = assistantPrefix.replace(text, "")
        text = inNomi.replace(text, "")
        text = text.trim().trimEnd('.', '!', '?')
        addCommand.matchEntire(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        ateCommand.matchEntire(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return text.takeIf { it.isNotBlank() }
    }
}
