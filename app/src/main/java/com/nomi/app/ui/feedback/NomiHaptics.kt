package com.nomi.app.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What the app feels like under a finger.
 *
 * These are named after moments rather than waveforms, so the same event always feels the same
 * wherever it is triggered from - sending a meal from the floating row and sending it from the
 * keyboard are one moment, not two. Keeping the vocabulary this small is the point: a phone
 * that buzzes at everything says nothing, so the page itself stays silent and only the events
 * that actually change the day speak up.
 */
@Immutable
class NomiHaptics(private val feedback: HapticFeedback) {
    /** A meal was handed over to be researched. */
    fun sent() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** An entry landed on the page, or a scanned product was identified. */
    fun confirmed() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A swipe passed the point where letting go deletes the row. */
    fun deleteArmed() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** The row left the page. */
    fun removed() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)

    /** Nomi could not make sense of the meal. */
    fun failed() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberNomiHaptics(): NomiHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { NomiHaptics(feedback) }
}
