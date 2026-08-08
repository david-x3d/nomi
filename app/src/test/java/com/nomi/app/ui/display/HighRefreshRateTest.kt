package com.nomi.app.ui.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighRefreshRateTest {
    @Test
    fun `the fastest mode at the current resolution is chosen`() {
        val chosen = fastestModeIdForCurrentResolution(
            modes = listOf(
                DisplayModeSpec(modeId = 1, width = 1220, height = 2712, refreshRate = 60f),
                DisplayModeSpec(modeId = 2, width = 1220, height = 2712, refreshRate = 120f),
            ),
            currentModeId = 1,
        )

        assertEquals(2, chosen)
    }

    @Test
    fun `a faster mode at another resolution is never selected`() {
        val chosen = fastestModeIdForCurrentResolution(
            modes = listOf(
                DisplayModeSpec(modeId = 1, width = 1220, height = 2712, refreshRate = 60f),
                DisplayModeSpec(modeId = 2, width = 1080, height = 2400, refreshRate = 144f),
            ),
            currentModeId = 1,
        )

        assertNull(chosen)
    }

    @Test
    fun `already running at the fastest mode requests no switch`() {
        val chosen = fastestModeIdForCurrentResolution(
            modes = listOf(
                DisplayModeSpec(modeId = 1, width = 1220, height = 2712, refreshRate = 60f),
                DisplayModeSpec(modeId = 2, width = 1220, height = 2712, refreshRate = 120f),
            ),
            currentModeId = 2,
        )

        assertNull(chosen)
    }

    @Test
    fun `a negligible refresh rate difference is not worth a mode switch`() {
        val chosen = fastestModeIdForCurrentResolution(
            modes = listOf(
                DisplayModeSpec(modeId = 1, width = 1220, height = 2712, refreshRate = 60f),
                DisplayModeSpec(modeId = 2, width = 1220, height = 2712, refreshRate = 60.2f),
            ),
            currentModeId = 1,
        )

        assertNull(chosen)
    }

    @Test
    fun `an unknown current mode is left alone`() {
        val chosen = fastestModeIdForCurrentResolution(
            modes = listOf(
                DisplayModeSpec(modeId = 1, width = 1220, height = 2712, refreshRate = 120f),
            ),
            currentModeId = 99,
        )

        assertNull(chosen)
    }
}
