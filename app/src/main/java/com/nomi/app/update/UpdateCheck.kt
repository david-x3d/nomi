package com.nomi.app.update

/**
 * A release version, compared the way a human reads one.
 *
 * This exists because the obvious implementation - `latest > installed` on strings - is wrong in
 * exactly the case that matters: "2.10.0" sorts *before* "2.9.9" lexicographically, so a user on
 * 2.9.9 would never be offered 2.10.0. Comparison is numeric per component, with a pre-release
 * suffix ranked below the same bare version the way semver says it should be.
 */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** e.g. `beta.2` in `2.4.0-beta.2`; empty for a stable release. */
    val preRelease: String = "",
) : Comparable<ReleaseVersion> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: ReleaseVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        // A pre-release is older than the bare version it leads up to: 2.4.0-beta.1 < 2.4.0.
        if (isPreRelease && !other.isPreRelease) return -1
        if (!isPreRelease && other.isPreRelease) return 1
        return comparePreRelease(preRelease, other.preRelease)
    }

    /**
     * Semver precedence for the part after the dash: identifiers are compared one at a time,
     * numbers as numbers, a number below a word, and a shorter list below a longer one it
     * prefixes. Comparing the whole suffix as text put `beta.10` below `beta.2`, so a beta.10
     * tester was offered beta.2 as an update - the same mistake this class exists to prevent.
     */
    private fun comparePreRelease(first: String, second: String): Int {
        val a = first.split('.')
        val b = second.split('.')
        for (index in 0 until minOf(a.size, b.size)) {
            val left = a[index]
            val right = b[index]
            val leftNumber = left.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toBigIntegerOrNull()
            val rightNumber = right.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toBigIntegerOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (isPreRelease) "-$preRelease" else ""

    companion object {
        /**
         * Parses `2.3.1` and `v2.3.1`, optionally with a `-beta.2` suffix.
         *
         * Returns `null` for anything it does not understand rather than guessing, because a
         * mis-parsed version is how an update dialog ends up offering a downgrade.
         */
        fun parse(raw: String?): ReleaseVersion? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val withoutV = text.removePrefix("v").removePrefix("V")
            val dash = withoutV.indexOf('-')
            val core = if (dash >= 0) withoutV.substring(0, dash) else withoutV
            val pre = if (dash >= 0) withoutV.substring(dash + 1) else ""
            val parts = core.split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val numbers = parts.map { part ->
                part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() ?: return null
            }
            return ReleaseVersion(
                major = numbers[0],
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
                preRelease = pre,
            )
        }
    }
}

/**
 * What the checker decided, and what the dialog needs in order to render.
 *
 * A sealed type so "no update" is a value the UI can hold rather than a null it has to
 * distinguish from "not checked yet", and so nothing has to guess whether a blank means offline.
 */
sealed interface UpdateAvailability {
    /** Not checked, or checked and nothing newer exists. Nothing is shown. */
    data object UpToDate : UpdateAvailability

    data class Available(
        val version: String,
        /** The exact release page, never the repository homepage. */
        val releaseUrl: String,
        /** A short plain-text summary, already stripped of Markdown; may be blank. */
        val summary: String,
    ) : UpdateAvailability
}

/**
 * The decision, kept free of Android, networking and Compose so it can be tested directly.
 */
object UpdateCheck {

    /** Longest release body worth summarising; GitHub bodies can be enormous. */
    private const val MAX_SUMMARY_CHARS = 220

    /**
     * Decides whether [latest] should be offered to someone running [installed].
     *
     * A draft is never offered. A pre-release is only offered to someone already running a
     * pre-release, so a stable user is never opted into a beta channel by accident.
     */
    fun decide(
        installed: ReleaseVersion,
        latest: ReleaseVersion?,
        latestIsDraft: Boolean,
        latestIsPreRelease: Boolean,
    ): UpdateAvailability {
        if (latest == null) return UpdateAvailability.UpToDate
        if (latestIsDraft) return UpdateAvailability.UpToDate
        if (latestIsPreRelease && !installed.isPreRelease) return UpdateAvailability.UpToDate
        if (latest.isPreRelease && !installed.isPreRelease) return UpdateAvailability.UpToDate
        return if (latest > installed) {
            UpdateAvailability.Available(latest.toString(), "", "")
        } else {
            UpdateAvailability.UpToDate
        }
    }

    /**
     * Condenses a GitHub release body into something a dialog can show.
     *
     * Markdown is stripped rather than rendered: this is a hint that a release exists, not a
     * document reader, and a raw dump of a 4 000-character changelog would swamp a two-button
     * dialog. HTML comments, code fences, links and heading marks all go; the prose stays.
     */
    fun summarize(body: String?): String {
        if (body.isNullOrBlank()) return ""
        var text = body
        text = Regex("(?s)<!--.*?-->").replace(text, " ")
        text = Regex("(?s)```.*?```").replace(text, " ")
        text = Regex("(?s)~~~.*?~~~").replace(text, " ")
        text = Regex("(?m)^\\s{0,3}#{1,6}\\s*").replace(text, "")
        text = Regex("(?m)^\\s{0,3}>\\s?").replace(text, "")
        text = Regex("!\\[[^]]*]\\([^)]*\\)").replace(text, "")
        text = Regex("\\[([^]]*)]\\([^)]*\\)").replace(text, "$1")
        text = Regex("[*_`]{1,3}").replace(text, "")
        text = Regex("(?m)^\\s*[-=*_]{3,}\\s*$").replace(text, " ")
        text = text.replace('\u00A0', ' ')
        text = Regex("[ \\t]+").replace(text, " ")
        text = Regex("\\n{2,}").replace(text, "\n")
        val trimmed = text.trim()
        if (trimmed.length <= MAX_SUMMARY_CHARS) return trimmed
        val cut = trimmed.take(MAX_SUMMARY_CHARS)
        val lastSpace = cut.lastIndexOf(' ')
        val body2 = if (lastSpace > MAX_SUMMARY_CHARS / 2) cut.take(lastSpace) else cut
        return "$body2…"
    }
}
