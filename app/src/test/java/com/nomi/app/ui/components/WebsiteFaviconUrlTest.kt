package com.nomi.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteFaviconUrlTest {
    @Test
    fun `normalizes a public HTTPS hostname and strips www`() {
        assertEquals(
            "openfoodfacts.org",
            WebsiteFaviconUrl.normalizePublicHttpsHostname(
                "https://WWW.OpenFoodFacts.org/product/123?token=private#nutrition",
            ),
        )
    }

    @Test
    fun `favicon request contains only normalized hostname from source`() {
        val faviconUrl = requireNotNull(
            WebsiteFaviconUrl.build(
                "https://www.dominos.com/menu/pizza?session=do-not-send#details",
            ),
        )

        assertEquals(
            "https://www.google.com/s2/favicons?domain=dominos.com&sz=128",
            faviconUrl,
        )
        assertFalse(faviconUrl.contains("menu"))
        assertFalse(faviconUrl.contains("session"))
        assertFalse(faviconUrl.contains("do-not-send"))
    }

    @Test
    fun `rejects non HTTPS schemes and missing schemes`() {
        assertNull(WebsiteFaviconUrl.build("http://openfoodfacts.org/product/123"))
        assertNull(WebsiteFaviconUrl.build("ftp://openfoodfacts.org/file"))
        assertNull(WebsiteFaviconUrl.build("openfoodfacts.org/product/123"))
        assertNull(WebsiteFaviconUrl.build("javascript:alert(1)"))
    }

    @Test
    fun `rejects credentials ambiguous authorities and IP literals`() {
        assertNull(WebsiteFaviconUrl.build("https://user:secret@openfoodfacts.org/product/123"))
        assertNull(WebsiteFaviconUrl.build("https://openfoodfacts.org%40evil.com/product/123"))
        assertNull(WebsiteFaviconUrl.build("https://127.0.0.1/admin"))
        assertNull(WebsiteFaviconUrl.build("https://[::1]/admin"))
        assertNull(WebsiteFaviconUrl.build("https://2130706433/admin"))
    }

    @Test
    fun `rejects local reserved malformed and documentation hosts`() {
        val invalidUrls = listOf(
            "https://localhost/admin",
            "https://nutrition.local/item",
            "https://service.internal/item",
            "https://example.com/item",
            "https://subdomain.example.org/item",
            "https://bad_host.com/item",
            "https://-bad.com/item",
            "https://bad-.com/item",
            "https://singlelabel/item",
        )

        assertTrue(invalidUrls.all { WebsiteFaviconUrl.build(it) == null })
    }

    @Test
    fun `accepts standard subdomains and punycode hostnames`() {
        assertEquals(
            "world.openfoodfacts.org",
            WebsiteFaviconUrl.normalizePublicHttpsHostname("https://world.openfoodfacts.org/product/1"),
        )
        assertEquals(
            "xn--bcher-kva.de",
            WebsiteFaviconUrl.normalizePublicHttpsHostname("https://xn--bcher-kva.de/lebensmittel"),
        )
    }

    @Test
    fun `research stack always reserves three calm source orbs`() {
        assertEquals(listOf(null, null, null), researchSourceIconSlots(emptyList()))

        assertEquals(
            listOf(
                "https://brand.com/nutrition",
                "https://retailer.de/product",
                null,
            ),
            researchSourceIconSlots(
                listOf(
                    "https://brand.com/nutrition",
                    "https://www.brand.com/duplicate",
                    "http://insecure.net/rejected",
                    "https://retailer.de/product",
                ),
            ),
        )
    }
}
