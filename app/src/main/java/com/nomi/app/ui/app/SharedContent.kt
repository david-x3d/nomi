package com.nomi.app.ui.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Something another app handed to Nomi through the Android share sheet.
 *
 * Text lands in the Today composer for the user to read before anything is sent to a provider:
 * a shared page or message is rarely just a meal, and research costs the user's own credits.
 * An image goes straight into the photo flow, which already stops at a review step.
 */
sealed interface SharedContent {
    data class Text(val text: String) : SharedContent
    data class Image(val uri: Uri) : SharedContent
}

/** Reads an ACTION_SEND intent. Anything else, or a share with nothing usable in it, is null. */
internal fun sharedContentFrom(intent: Intent?): SharedContent? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val type = intent.type.orEmpty()
    if (type.startsWith("image/")) {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) return SharedContent.Image(uri)
    }
    val text = sharedLoggingText(
        subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
    ) ?: return null
    return SharedContent.Text(text)
}

/**
 * The composer text for a shared subject and body.
 *
 * Browsers share a page as its title in the subject and only the address in the body, so the
 * title is kept in front of it: "Chicken tikka masala - Recipe" says more about the meal than a
 * URL does. A subject the body already starts with is not repeated, and the result is capped so
 * a whole article pasted in by accident stays a draft the user can still read and trim.
 */
internal fun sharedLoggingText(subject: String?, text: String?): String? {
    val body = text?.trim().orEmpty()
    val title = subject?.trim().orEmpty()
    val combined = when {
        title.isEmpty() -> body
        body.isEmpty() -> title
        body.startsWith(title, ignoreCase = true) -> body
        else -> "$title\n$body"
    }
    return combined.take(MAX_SHARED_TEXT_LENGTH).trim().takeIf { it.isNotEmpty() }
}

private const val MAX_SHARED_TEXT_LENGTH = 2_000
