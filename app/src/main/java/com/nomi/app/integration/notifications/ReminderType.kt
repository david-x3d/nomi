package com.nomi.app.integration.notifications

import com.nomi.app.data.preferences.ReminderPreferences
import com.nomi.app.data.preferences.ReminderSetting

enum class ReminderType(
    val wireName: String,
    internal val requestCode: Int,
    internal val notificationId: Int,
) {
    BREAKFAST("breakfast", 8101, 9101),
    LUNCH("lunch", 8102, 9102),
    DINNER("dinner", 8103, 9103),
    DAILY_SUMMARY("daily_summary", 8104, 9104),
    WEIGHT("weight", 8105, 9105),
    ;

    fun settingIn(preferences: ReminderPreferences): ReminderSetting = when (this) {
        BREAKFAST -> preferences.breakfast
        LUNCH -> preferences.lunch
        DINNER -> preferences.dinner
        DAILY_SUMMARY -> preferences.dailySummary
        WEIGHT -> preferences.weight
    }

    companion object {
        fun fromWireName(value: String?): ReminderType? = entries.firstOrNull { it.wireName == value }
    }
}
