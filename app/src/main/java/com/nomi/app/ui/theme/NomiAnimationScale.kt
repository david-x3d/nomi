package com.nomi.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system's animation scale, read once per composition.
 *
 * Android exposes this as a user setting — Developer options → Animator duration scale, and the
 * accessibility switch that drives the same value. `0` means "remove animations", and Compose does
 * not consult it on its own: tweens and `AnimatedVisibility` still run, they just run quickly.
 *
 * The onboarding intro is the one place in the app that plays a sequence the user did not ask for,
 * so it reads this first. At `0` it shows its finished state immediately rather than performing a
 * three-beat reveal nobody asked to see. Read defensively: a locked-down device can refuse the
 * read, and a missing value must not mean "animations off".
 */
@Composable
internal fun rememberNomiAnimationScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}

/** True when the user has asked the system not to animate, by any route. */
@Composable
internal fun animationsAreDisabled(): Boolean = rememberNomiAnimationScale() <= 0f
