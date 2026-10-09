package com.nomi.app.integration.wear

/**
 * The Data Layer contract between Nomi on the phone and Nomi on a Wear OS watch.
 *
 * The watch module keeps an identical copy (com.nomi.wear.WearContract). Both apps share the
 * application id and signing key, which is what lets Google Play services route these paths
 * between them; change a path or key here only together with that copy.
 */
internal object WearContract {
    /** Advertised by the phone app, so the watch knows which node can log food. */
    const val PHONE_CAPABILITY = "nomi_phone"

    /** Advertised by the watch app, so the phone only publishes when a watch has Nomi. */
    const val WATCH_CAPABILITY = "nomi_watch"

    /** Data item with today's totals, targets and the quick-add list. */
    const val TODAY_PATH = "/nomi/today"

    /** Watch to phone: a meal in plain words, UTF-8. */
    const val LOG_TEXT_PATH = "/nomi/log-text"

    /** Watch to phone: a quick-add entry, as "<kind>:<id>". */
    const val LOG_QUICK_PATH = "/nomi/log-quick"

    /** Watch to phone: publish [TODAY_PATH] again, sent when the watch app opens. */
    const val SYNC_PATH = "/nomi/sync"

    /** Phone to watch: how a logging request ended, as a DataMap of [KEY_OK] and [KEY_MESSAGE]. */
    const val LOG_RESULT_PATH = "/nomi/log-result"

    const val KEY_DATE = "date"
    const val KEY_HAS_PROFILE = "has_profile"
    const val KEY_LANGUAGE = "language"
    const val KEY_CALORIES = "calories"
    const val KEY_CALORIE_TARGET = "calorie_target"
    const val KEY_PROTEIN = "protein"
    const val KEY_PROTEIN_TARGET = "protein_target"
    const val KEY_CARBS = "carbs"
    const val KEY_CARBS_TARGET = "carbs_target"
    const val KEY_FAT = "fat"
    const val KEY_FAT_TARGET = "fat_target"
    const val KEY_QUICK = "quick"
    const val KEY_UPDATED_AT = "updated_at"

    const val KEY_QUICK_KIND = "kind"
    const val KEY_QUICK_ID = "id"
    const val KEY_QUICK_TITLE = "title"
    const val KEY_QUICK_SUBTITLE = "subtitle"
    const val KEY_QUICK_CALORIES = "calories"

    const val QUICK_FAVORITE = "favorite"
    const val QUICK_SAVED_MEAL = "meal"

    const val KEY_OK = "ok"
    const val KEY_MESSAGE = "message"

    /** A missing target travels as this, because a DataMap has no null doubles. */
    const val NO_TARGET = -1.0

    /** The watch list stays short enough to scroll with a bezel. */
    const val MAX_QUICK_ITEMS = 16
}
