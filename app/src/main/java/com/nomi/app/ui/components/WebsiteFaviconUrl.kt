package com.nomi.app.ui.components

import java.net.URI
import java.util.Locale

/**
 * Builds privacy-bounded favicon requests from AI-provided source URLs.
 *
 * The original URL is never used as an image request. Only a strictly validated, normalized
 * public HTTPS hostname is sent to the fixed favicon endpoint below.
 */
object WebsiteFaviconUrl {
    private const val MAX_SOURCE_URL_LENGTH = 2_048
    private const val FAVICON_ENDPOINT = "https://www.google.com/s2/favicons"

    private val reservedSuffixes = setOf(
        "arpa",
        "corp",
        "example",
        "home",
        "internal",
        "invalid",
        "lan",
        "local",
        "localdomain",
        "localhost",
        "onion",
        "test",
    )

    private val reservedDocumentationHosts = setOf(
        "example.com",
        "example.net",
        "example.org",
    )

    fun normalizePublicHttpsHostname(sourceUrl: String?): String? {
        val input = sourceUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (input.length > MAX_SOURCE_URL_LENGTH) return null
        if (input.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null

        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.isOpaque || uri.rawUserInfo != null) return null

        // URI.host is intentionally required. It rejects ambiguous authorities and Unicode that
        // has not already been encoded as a standards-compliant punycode hostname.
        val rawAuthority = uri.rawAuthority ?: return null
        if ('@' in rawAuthority || '%' in rawAuthority) return null
        val rawHost = uri.host ?: return null
        if (':' in rawHost || rawHost.startsWith('[') || rawHost.endsWith(']')) return null

        var host = rawHost.lowercase(Locale.ROOT)
        if (host.endsWith('.')) return null
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        if (host.length !in 4..253) return null

        val labels = host.split('.')
        if (labels.size < 2 || labels.any { !it.isValidHostnameLabel() }) return null

        val topLevelDomain = labels.last()
        val validTopLevelDomain = if (topLevelDomain.startsWith("xn--")) {
            topLevelDomain.length > 4
        } else {
            topLevelDomain.length >= 2 && topLevelDomain.all { it in 'a'..'z' }
        }
        if (!validTopLevelDomain) return null
        if (topLevelDomain in reservedSuffixes) return null
        if (reservedDocumentationHosts.any { host == it || host.endsWith(".$it") }) return null
        if (labels.any { it == "localhost" }) return null

        return host
    }

    fun build(sourceUrl: String?): String? =
        normalizePublicHttpsHostname(sourceUrl)?.let { hostname ->
            // hostname contains only RFC-style label characters, so no original URL path, query,
            // fragment, port, or user information can reach this endpoint.
            "$FAVICON_ENDPOINT?domain=$hostname&sz=128"
        }

    private fun String.isValidHostnameLabel(): Boolean =
        length in 1..63 &&
            first().isAsciiLetterOrDigit() &&
            last().isAsciiLetterOrDigit() &&
            all { it.isAsciiLetterOrDigit() || it == '-' }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in '0'..'9'
}
