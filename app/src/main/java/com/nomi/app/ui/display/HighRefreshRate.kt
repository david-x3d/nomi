package com.nomi.app.ui.display

/** One display mode reduced to what the refresh-rate choice actually depends on. */
data class DisplayModeSpec(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * Picks the fastest display mode that keeps the current resolution.
 *
 * Several OEM skins hand apps a 60 Hz mode by default and only switch to 120 Hz when the app
 * asks for it. Resolution is held fixed so requesting a faster mode can never silently drop the
 * screen to a lower resolution, and the current mode is returned unchanged when nothing faster
 * exists, which avoids a pointless mode switch.
 */
fun fastestModeIdForCurrentResolution(
    modes: List<DisplayModeSpec>,
    currentModeId: Int,
): Int? {
    val current = modes.firstOrNull { it.modeId == currentModeId } ?: return null
    val fastest = modes
        .filter { it.width == current.width && it.height == current.height }
        .maxByOrNull { it.refreshRate }
        ?: return null
    return fastest.modeId.takeIf { fastest.refreshRate > current.refreshRate + REFRESH_RATE_EPSILON }
}

private const val REFRESH_RATE_EPSILON = 0.5f
