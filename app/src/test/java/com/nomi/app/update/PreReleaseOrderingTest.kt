package com.nomi.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pre-release suffixes are ordered by semver precedence, not as text. */
class PreReleaseOrderingTest {
    private fun v(raw: String) = ReleaseVersion.parse(raw)!!

    @Test
    fun `beta 10 is newer than beta 2`() {
        assertTrue(v("2.4.0-beta.10") > v("2.4.0-beta.2"))
        assertTrue(v("2.4.0-beta.2") < v("2.4.0-beta.10"))
    }

    @Test
    fun `a beta 10 tester is not offered beta 2`() {
        val decision = UpdateCheck.decide(
            installed = v("2.4.0-beta.10"),
            latest = v("2.4.0-beta.2"),
            latestIsDraft = false,
            latestIsPreRelease = true,
        )

        assertEquals(UpdateAvailability.UpToDate, decision)
    }

    @Test
    fun `semver precedence holds across identifier kinds`() {
        val ordered = listOf(
            "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
            "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0",
        ).map(::v)

        assertEquals(ordered, ordered.shuffled(java.util.Random(7)).sorted())
    }
}
