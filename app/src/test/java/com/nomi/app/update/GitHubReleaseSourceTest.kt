package com.nomi.app.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseSourceTest {
    @Test
    fun `GitHub JSON with extra fields produces an available update`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://api.github.com/repos/david-x3d/nomi/releases/latest", request.url.toString())
            respond("""{"tag_name":"v2.7","html_url":"https://github.com/david-x3d/nomi/releases/tag/v2.7","body":"Share multiple foods","draft":false,"prerelease":false,"assets":[],"id":123}""")
        })
        try {
            val release = GitHubReleaseSource(httpClient = client).latest()!!
            assertEquals(ReleaseVersion(2, 7, 0), release.version)
            assertTrue(release.releaseUrl.endsWith("/v2.7"))
            assertTrue(UpdateCheck.decide(ReleaseVersion(2, 6, 2), release.version, release.isDraft, release.isPreRelease) is UpdateAvailability.Available)
            assertEquals(UpdateAvailability.UpToDate, UpdateCheck.decide(ReleaseVersion(2, 7, 0), release.version, false, false))
        } finally { client.close() }
    }

    @Test
    fun `malformed and unsuccessful responses fail quietly`() = runBlocking {
        for ((body, status) in listOf("not json" to HttpStatusCode.OK, "{}" to HttpStatusCode.Forbidden, """{"tag_name":"v2.7"}""" to HttpStatusCode.OK)) {
            val client = HttpClient(MockEngine { respond(body, status) })
            try { assertNull(GitHubReleaseSource(httpClient = client).latest()) }
            finally { client.close() }
        }
    }
}
