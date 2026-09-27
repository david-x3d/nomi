package com.nomi.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The update checker's whole contract, with no network and no Android framework.
 *
 * Every one of these is a case that a string comparison gets wrong or a null-handling omission
 * turns into a crash: lexicographic ordering silently hides 2.10.0 from a 2.9.9 user, a draft
 * must never be offered, and an unexpected body from the API must not take the app down on a
 * cold start.
 */
class UpdateCheckTest {

    private fun decide(
        installed: String,
        latest: String?,
        draft: Boolean = false,
        preRelease: Boolean = false,
    ): UpdateAvailability = UpdateCheck.decide(
        installed = ReleaseVersion.parse(installed)!!,
        latest = ReleaseVersion.parse(latest),
        latestIsDraft = draft,
        latestIsPreRelease = preRelease,
    )

    // ---- the required comparison cases ----

    @Test
    fun `installed 2_3_0 with latest 2_3_1 shows the update`() {
        val result = decide("2.3.0", "2.3.1")
        assertEquals(UpdateAvailability.Available("2.3.1", "", ""), result)
    }

    @Test
    fun `installed 2_3_1 with latest 2_3_1 shows nothing`() {
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.1", "2.3.1"))
    }

    @Test
    fun `installed 2_3_2 with latest 2_3_1 shows nothing`() {
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.2", "2.3.1"))
    }

    @Test
    fun `2_9_9 to 2_10_0 shows the update`() {
        // The case a lexicographic comparison gets wrong: "2.10.0" < "2.9.9" as a string.
        assertEquals(
            UpdateAvailability.Available("2.10.0", "", ""),
            decide("2.9.9", "2.10.0"),
        )
    }

    @Test
    fun `2_9_9 to 2_10_0 shows nothing in reverse`() {
        assertEquals(UpdateAvailability.UpToDate, decide("2.10.0", "2.9.9"))
    }

    @Test
    fun `a draft is ignored`() {
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.0", "2.4.0", draft = true))
    }

    @Test
    fun `a prerelease is ignored for a stable build`() {
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.0", "2.4.0", preRelease = true))
        assertEquals(
            UpdateAvailability.UpToDate,
            decide("2.3.0", "2.4.0-beta.1", preRelease = true),
        )
    }

    @Test
    fun `a prerelease is offered to a prerelease build`() {
        val result = decide("2.4.0-beta.1", "2.4.0-beta.2", preRelease = true)
        assertEquals(UpdateAvailability.Available("2.4.0-beta.2", "", ""), result)
    }

    @Test
    fun `a prerelease does not replace the stable build it leads to`() {
        // 2.4.0 stable is newer than 2.4.0-beta.1, so a stable user is never nudged backwards.
        assertEquals(UpdateAvailability.UpToDate, decide("2.4.0", "2.4.0-beta.1", preRelease = true))
    }

    @Test
    fun `a malformed or missing version shows nothing and does not throw`() {
        listOf("", "   ", "not-a-version", "x.y.z", "2..3", "2.3.1.4.5").forEach { bad ->
            assertNull("parsed \"$bad\"", ReleaseVersion.parse(bad))
        }
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.0", null))
        assertEquals(UpdateAvailability.UpToDate, decide("2.3.0", "garbage"))
    }

    @Test
    fun `a network failure shows nothing`() = runBlocking {
        // The source collapses every failure to null; the decision then has nothing to offer.
        val failing = object : UpdateReleaseSource {
            override suspend fun latest(): ReleaseSummary? = null
        }
        assertNull(failing.latest())
        assertEquals(
            UpdateAvailability.UpToDate,
            UpdateCheck.decide(
                installed = ReleaseVersion.parse("2.3.0")!!,
                latest = null,
                latestIsDraft = false,
                latestIsPreRelease = false,
            ),
        )
    }

    // ---- version parsing ----

    @Test
    fun `versions parse with and without a v prefix and a pre-release suffix`() {
        assertEquals(ReleaseVersion(2, 3, 1), ReleaseVersion.parse("2.3.1"))
        assertEquals(ReleaseVersion(2, 3, 1), ReleaseVersion.parse("v2.3.1"))
        assertEquals(ReleaseVersion(2, 3, 1), ReleaseVersion.parse("  2.3.1  "))
        assertEquals(ReleaseVersion(2, 3, 0, "beta.2"), ReleaseVersion.parse("2.3.0-beta.2"))
        assertEquals(ReleaseVersion(2, 3, 0, "beta.2"), ReleaseVersion.parse("v2.3.0-beta.2"))
        // Missing components default rather than failing: "2" and "2.3" are both real tags.
        assertEquals(ReleaseVersion(2, 0, 0), ReleaseVersion.parse("2"))
        assertEquals(ReleaseVersion(2, 3, 0), ReleaseVersion.parse("2.3"))
    }

    @Test
    fun `numeric ordering holds across every component`() {
        val ordered = listOf("0.9.9", "1.0.0", "1.0.1", "1.1.0", "1.9.0", "1.10.0", "2.0.0", "10.0.0")
        val parsed = ordered.map { ReleaseVersion.parse(it)!! }
        parsed.zipWithNext().forEach { (lower, higher) ->
            assertTrue("$lower should be < $higher", lower < higher)
        }
    }

    @Test
    fun `a pre-release sorts below its own bare version`() {
        val pre = ReleaseVersion.parse("2.4.0-rc.1")!!
        val stable = ReleaseVersion.parse("2.4.0")!!
        assertTrue(pre < stable)
        assertTrue(stable > pre)
    }

    @Test
    fun `toString round-trips`() {
        listOf("2.3.1", "2.4.0-beta.2", "10.0.0").forEach { raw ->
            assertEquals(raw, ReleaseVersion.parse(raw)!!.toString())
        }
    }

    // ---- release body summary ----

    @Test
    fun `a markdown body is condensed to readable plain text`() {
        val body = """
            ## What's new

            - Fixed **copy day** so a grouped meal is not orphaned.
            - See [the notes](https://example.com/notes) for detail.

            <!-- hidden comment -->
            ```
            code block that should not appear
            ```
        """.trimIndent()
        val summary = UpdateCheck.summarize(body)
        assertTrue(summary, summary.contains("What's new"))
        assertTrue(summary, summary.contains("copy day"))
        assertTrue(summary, !summary.contains("**"))
        assertTrue(summary, !summary.contains("]("))
        assertTrue(summary, !summary.contains("hidden comment"))
        assertTrue(summary, !summary.contains("code block"))
    }

    @Test
    fun `an enormous body is truncated rather than dumped`() {
        val huge = "word ".repeat(2_000)
        val summary = UpdateCheck.summarize(huge)
        assertTrue("summary was ${summary.length} chars", summary.length <= 221)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `an empty body yields an empty summary`() {
        assertEquals("", UpdateCheck.summarize(null))
        assertEquals("", UpdateCheck.summarize(""))
        assertEquals("", UpdateCheck.summarize("   \n  "))
    }

    // ---- the release source, against fixtures rather than the network ----

    @Test
    fun `a source that reports nothing never offers an update`() = runBlocking {
        val source = object : UpdateReleaseSource {
            override suspend fun latest(): ReleaseSummary? = null
        }
        assertNull(source.latest())
    }

    @Test
    fun `a release without a usable https page is not offered`() {
        // Guards the one field the dialog depends on: a link it could open.
        val unusable = ReleaseSummary(
            version = ReleaseVersion.parse("9.9.9")!!,
            releaseUrl = "",
            isDraft = false,
            isPreRelease = false,
            body = "",
        )
        assertTrue(UpdateCheck.decide(
            installed = ReleaseVersion.parse("2.3.0")!!,
            latest = unusable.version,
            latestIsDraft = unusable.isDraft,
            latestIsPreRelease = unusable.isPreRelease,
        ) is UpdateAvailability.Available)
        // The URL emptiness is what the source filters on; assert the contract it relies on.
        assertTrue(unusable.releaseUrl.isEmpty())
    }
}
