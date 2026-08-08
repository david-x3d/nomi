package com.nomi.app.data.remote.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Strict provider-side shape for Sonar nutrition research responses. */
internal fun nutritionResearchResponseFormat(): ResponseFormat = ResponseFormat(
    type = "json_schema",
    jsonSchema = JsonSchemaDefinition(
        name = "nomi_nutrition_research",
        schema = NUTRITION_RESEARCH_SCHEMA,
    ),
)

private val NUTRITION_RESEARCH_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", stringArray("items"))
    put("properties", buildJsonObject {
        put("items", buildJsonObject {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", 50)
            put("items", nutritionItemSchema())
        })
        put("overallConfidence", nullable(confidenceSchema()))
    })
}

private fun nutritionItemSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put(
        "required",
        stringArray(
            "name",
            "quantity",
            "unit",
            "calories",
            "proteinGrams",
            "carbohydrateGrams",
            "fatGrams",
            "sourceServingQuantity",
            "sourceServingUnit",
            "isEstimate",
        ),
    )
    put("properties", buildJsonObject {
        put("name", stringSchema())
        put("brand", nullable(stringSchema()))
        put("quantity", positiveNumberSchema())
        put("unit", stringSchema())
        put("gramsEquivalent", nullable(positiveNumberSchema()))
        put("calories", nonNegativeNumberSchema())
        put("proteinGrams", nonNegativeNumberSchema())
        put("carbohydrateGrams", nonNegativeNumberSchema())
        put("fatGrams", nonNegativeNumberSchema())
        put("fiberGrams", nullable(nonNegativeNumberSchema()))
        put("sourceName", nullable(stringSchema()))
        put("sourceServingQuantity", positiveNumberSchema())
        put("sourceServingUnit", stringSchema())
        put("sourceServingGramsEquivalent", nullable(positiveNumberSchema()))
        put("sourceCountry", nullable(stringSchema()))
        put("sourcePackageQuantity", nullable(positiveNumberSchema()))
        put("sourcePackageUnit", nullable(stringSchema()))
        put("isEstimate", booleanSchema())
        put("confidence", nullable(confidenceSchema()))
        put("assumptions", stringListSchema())
    })
}

private fun stringSchema(): JsonObject = buildJsonObject {
    put("type", "string")
    put("minLength", 1)
}

private fun booleanSchema(): JsonObject = buildJsonObject {
    put("type", "boolean")
}

private fun positiveNumberSchema(): JsonObject = buildJsonObject {
    put("type", "number")
    put("exclusiveMinimum", 0)
}

private fun nonNegativeNumberSchema(): JsonObject = buildJsonObject {
    put("type", "number")
    put("minimum", 0)
}

private fun confidenceSchema(): JsonObject = buildJsonObject {
    put("type", "number")
    put("minimum", 0)
    put("maximum", 1)
}

private fun stringListSchema(): JsonObject = buildJsonObject {
    put("type", "array")
    put("maxItems", 50)
    put("items", stringSchema())
}

private fun nullable(schema: JsonObject): JsonObject = buildJsonObject {
    put("anyOf", buildJsonArray {
        add(schema)
        add(buildJsonObject { put("type", "null") })
    })
}

private fun stringArray(vararg values: String): JsonArray = buildJsonArray {
    values.forEach { add(JsonPrimitive(it)) }
}
