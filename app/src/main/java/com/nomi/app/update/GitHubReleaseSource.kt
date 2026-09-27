package com.nomi.app.update

import com.nomi.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the checker needs to know about a published release, and nothing more.
 *
 * An interface rather than a concrete client so the decision can be tested against fixtures
 * without a network, and so the "no update" path is provably reachable rather than asserted.
 */
interface UpdateReleaseSource {
    /** Returns the latest release, or `null` if there is none, or on any failure. */
    suspend fun latest(): ReleaseSummary?
}

data class ReleaseSummary(
    val version: ReleaseVersion,
    val releaseUrl: String,
    val isDraft: Boolean,
    val isPreRelease: Boolean,
    val body: String,
)

/**
 * Reads the latest release from the public GitHub Releases API.
 *
 * Deliberately conservative, because this runs on every cold start:
 *  - no authentication, so there is no token to leak and no rate-limit tier to exhaust;
 *  - a short timeout, because a slow GitHub must never be felt as a slow app;
 *  - every failure is swallowed into `null`. A user offline, behind a captive portal, or during
 *    a GitHub outage sees nothing at all rather than an error.
 *  - a `User-Agent`, because GitHub asks clients to identify themselves.
 */
class GitHubReleaseSource(
    private val repository: String = DEFAULT_REPOSITORY,
    private val httpClient: HttpClient = defaultClient(),
) : UpdateReleaseSource {

    override suspend fun latest(): ReleaseSummary? = try {
        val response: HttpResponse = httpClient.get("$API_BASE/repos/$repository/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (response.status.value !in 200..299) {
            null
        } else {
            response.body<GitHubRelease>().toSummary()
        }
    } catch (_: Exception) {
        // Offline, DNS failure, TLS problem, malformed body, unexpected status: all of these are
        // simply "no update to show".
        null
    }

    private fun GitHubRelease.toSummary(): ReleaseSummary? {
        val parsed = ReleaseVersion.parse(tagName ?: name) ?: return null
        // A release with no page is nothing to open, so it is treated as nothing to show.
        val url = htmlUrl?.takeIf { it.startsWith("https://") } ?: return null
        return ReleaseSummary(
            version = parsed,
            releaseUrl = url,
            isDraft = draft,
            isPreRelease = prerelease,
            body = body.orEmpty(),
        )
    }

    private companion object {
        const val API_BASE = "https://api.github.com"

        fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(UserAgent) { agent = "Nomi/${BuildConfig.VERSION_NAME} (Android)" }
            install(HttpTimeout) {
                // Short on purpose: this is a background nicety, not something to wait for.
                requestTimeoutMillis = 6_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 6_000
            }
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
)

const val DEFAULT_REPOSITORY = "david-x3d/nomi"

/** The version this build reports, taken from the single source of truth in the build file. */
val installedVersion: ReleaseVersion? = ReleaseVersion.parse(BuildConfig.VERSION_NAME)
