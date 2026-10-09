package com.nomi.wear

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Shows the watch in the language picked in Nomi on the phone.
 *
 * Nomi has its own language setting, which can differ from the phone's system language, and the
 * watch follows its own system locale otherwise. The phone sends its choice with every update;
 * it is kept here so the app, the tile and the complication all start in it.
 */
object WatchLocale {
    private const val PREFERENCES = "nomi_watch"
    private const val KEY_LANGUAGE = "language"

    /** Stores [tag] and returns true when it differs from the language already in use. */
    fun update(context: Context, tag: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_LANGUAGE, "") == tag) return false
        preferences.edit().putString(KEY_LANGUAGE, tag).apply()
        return true
    }

    /** [context] with its resources in the phone's Nomi language, or unchanged when none is set. */
    fun wrap(context: Context): Context {
        val tag = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "")
            .orEmpty()
        if (tag.isBlank()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }
}
