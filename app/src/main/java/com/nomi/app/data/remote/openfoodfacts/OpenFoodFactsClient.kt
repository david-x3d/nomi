package com.nomi.app.data.remote.openfoodfacts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenFoodFactsClient(
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
        expectSuccess = true
    },
) : AutoCloseable {

    suspend fun findByBarcode(barcode: String): BarcodeProduct? {
        require(barcode.matches(Regex("[0-9]{8,14}"))) { "Invalid barcode" }
        val response = httpClient.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json") {
            parameter(
                "fields",
                "code,status,product_name,brands,image_front_small_url,serving_size," +
                    "product_quantity_unit,nutriments",
            )
        }.body<OpenFoodFactsResponse>()
        if (response.status != 1 || response.product == null) return null
        return response.product.toDomain(response.code ?: barcode)
    }

    override fun close() = httpClient.close()
}

data class BarcodeProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val servingSize: String?,
    /** `_100g` fields are per 100 ml when OFF identifies the product as a liquid. */
    val nutritionBasisUnit: String = "g",
    val caloriesPer100g: Double?,
    val proteinPer100g: Double?,
    val carbohydratesPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    val sourceName: String = "Open Food Facts",
    val sourceUrl: String,
)

@Serializable
private data class OpenFoodFactsResponse(
    val code: String? = null,
    val status: Int = 0,
    val product: OpenFoodFactsProduct? = null,
)

@Serializable
private data class OpenFoodFactsProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("image_front_small_url") val imageUrl: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    val nutriments: Nutriments = Nutriments(),
) {
    fun toDomain(barcode: String): BarcodeProduct? {
        val name = productName?.trim().orEmpty()
        if (name.isBlank()) return null
        return BarcodeProduct(
            barcode = barcode,
            name = name,
            brand = brands?.takeIf(String::isNotBlank),
            imageUrl = imageUrl,
            servingSize = servingSize,
            nutritionBasisUnit = if (productQuantityUnit.equals("ml", ignoreCase = true)) "ml" else "g",
            caloriesPer100g = nutriments.caloriesPer100g,
            proteinPer100g = nutriments.proteinPer100g,
            carbohydratesPer100g = nutriments.carbohydratesPer100g,
            fatPer100g = nutriments.fatPer100g,
            fiberPer100g = nutriments.fiberPer100g,
            sourceUrl = "https://world.openfoodfacts.org/product/$barcode",
        )
    }
}

@Serializable
private data class Nutriments(
    @SerialName("energy-kcal_100g") val caloriesPer100g: Double? = null,
    @SerialName("proteins_100g") val proteinPer100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydratesPer100g: Double? = null,
    @SerialName("fat_100g") val fatPer100g: Double? = null,
    @SerialName("fiber_100g") val fiberPer100g: Double? = null,
)
