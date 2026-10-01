package com.nomi.app.data.remote.openfoodfacts

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenFoodFactsClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /**
     * Open Food Facts answers an unknown barcode with HTTP 404 and a "product not found" body.
     * That is "no such product", which the barcode flow answers by researching the product; it
     * used to escape as an exception and show an AI-provider error instead.
     */
    @Test
    fun `an unknown barcode is no product rather than an error`() = runBlocking {
        val client = client(
            HttpStatusCode.NotFound,
            """{"code":"4099887766554","status":0,"status_verbose":"product not found"}""",
        )

        assertNull(client.findByBarcode("4099887766554"))
    }

    @Test
    fun `a known barcode is read with sodium in milligrams`() = runBlocking {
        val client = client(
            HttpStatusCode.OK,
            """{"code":"4000000000001","status":1,"product":{"product_name":"Skyr","brands":"Testmarke",
               "nutriments":{"energy-kcal_100g":63,"proteins_100g":11,"carbohydrates_100g":4,
               "fat_100g":0.2,"sodium_100g":0.05}}}""",
        )

        val product = client.findByBarcode("4000000000001")!!

        assertEquals("Skyr", product.name)
        assertEquals(63.0, product.caloriesPer100g!!, 0.0)
        assertEquals(50.0, product.sodiumMilligramsPer100g!!, 1e-9)
    }

    @Test
    fun `a server failure is still an error`() {
        val client = client(HttpStatusCode.Forbidden, """{"status":0}""")

        assertThrows(ResponseException::class.java) {
            runBlocking { client.findByBarcode("4000000000001") }
        }
    }

    private fun client(status: HttpStatusCode, body: String) = OpenFoodFactsClient(
        HttpClient(MockEngine { respond(body, status, jsonHeaders) }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
            expectSuccess = true
        },
    )
}
