package com.nomi.app.ai.prompt

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiPrompts {
    fun parseFood(text: String): String = """
        You convert natural-language meal descriptions into strict JSON.
        The input may be German or English. Preserve the user's language but normalize units.
        Do not invent nutrition values in this step. Extract every food, quantity, unit, brand,
        preparation, and any assumptions needed. A saved-meal phrase such as "mein übliches
        Frühstück" should be returned in mealReference.

        An explicit user quantity or package size is authoritative. Never replace it with a
        currently sold size found online or with a typical serving. Resolve package math to the
        consumed g/ml amount: "55% of a 320g package" is quantity=176, unit="g";
        "half of a 200g bag" is 100 g; "two thirds of a 200g bag" is 133.33333333333334 g.
        Preserve the original fraction/percentage wording in assumptions. If no size is stated,
        do not introduce a US package size. Deterministic app code reconciles this after parsing,
        so provider output cannot override it.
        Recognize mg, g, kg, ml, EL/Essloeffel/tbsp/tablespoon, and
        TL/Teeloeffel/tsp/teaspoon. In German, an unqualified Löffel/Loeffel means one
        Esslöffel unless the user says Teelöffel/TL; record that interpretation in assumptions.
        Normalize mg and kg to grams. One German tablespoon/EL is exactly 15 ml and one German
        teaspoon/TL is exactly 5 ml. An explicit spoon quantity is authoritative. Do not invent
        nutrition values in this parsing step.

        Return only this JSON shape:
        {
          "originalText": string,
          "language": string|null,
          "mealReference": string|null,
          "items": [{
            "name": string,
            "brand": string|null,
            "quantity": number|null,
            "unit": string|null,
            "gramsEquivalent": number|null,
            "preparation": string|null,
            "assumptions": [string]
          }]
        }

        User input: ${text.trim()}
    """.trimIndent()

    fun researchNutrition(
        intent: ParsedFoodIntent,
        json: Json,
        localeCountry: String? = null,
    ): String = """
        Research nutrition for the structured meal below. For branded and restaurant foods,
        prefer official manufacturer or restaurant sources, then reliable food databases,
        then Open Food Facts. For generic food, use reputable reference values. Label every
        uncertain portion or nutrient estimate. Do not include prose outside JSON.
        For users in Germany, apply this exact source order:
        1. official German manufacturer or restaurant product page;
        2. official EU/German product data;
        3. major German retailer product page;
        4. reliable nutrition database;
        5. international/US source;
        6. AI estimate only when reliable product data cannot be found.
        A lower-priority source is a fallback, not permission to replace a German quantity.
        The user's locale country is ${localeCountry?.takeIf { it.isNotBlank() } ?: "unknown"}.
        If it is DE, prefer the official German manufacturer/restaurant product page and German
        product formulation. Use another country's source only when no appropriate German source
        is available, and identify that source country.

        Return exactly one result for every structured input item, in the same order. `quantity`
        and `unit` MUST describe the amount the user logged. The calories and macro fields MUST
        describe the cited source serving, whose amount MUST be stated separately in
        `sourceServingQuantity` and `sourceServingUnit`. Never silently treat source-serving
        nutrition as nutrition for the logged amount. Nomi app code will first normalize those
        nutrient values to per 100 g/ml (or per 100 compatible count units) and then scale them
        to the logged amount.
        COUNT-VS-MASS CONVERSIONS MUST INCLUDE A TOTAL GRAM EQUIVALENT. When the logged amount is
        a count (piece/Stück) but the source serving is mass, `gramsEquivalent` MUST be the total
        grams for the entire logged count, not grams per piece. When the source serving is a count
        but the logged amount is mass, `sourceServingGramsEquivalent` MUST be the total grams for
        the entire source count. For example, two estimated medium apples researched from per-100-g
        values require quantity=2, unit="pieces", gramsEquivalent=364, isEstimate=true, and a clear
        assumption. Never omit the applicable total gram equivalent in a count-vs-mass result, and
        never change an explicit logged count while supplying it.
        UNIT NORMALIZATION IS EXACT: 1 mg = 0.001 g, 1 kg = 1000 g,
        1 EL/Essloeffel/tbsp/tablespoon = 15 ml, and
        1 TL/Teeloeffel/tsp/teaspoon = 5 ml. An unqualified German Löffel/Loeffel means EL
        (15 ml), not TL. Preserve every explicit quantity before applying these conversions.
        Spoons are volume units. If a spoon or ml amount is researched from a gram-based source,
        `gramsEquivalent` MUST be the total mass for the entire logged amount. Prefer an official
        product serving weight or reputable food-specific density. If neither exists, provide a
        clearly labeled reasonable food-specific estimate, set `isEstimate=true`, and explain it
        in assumptions. Example: 1.5 EL is exactly 22.5 ml; jam is commonly about 20 g per EL, so
        1.5 EL jam may use gramsEquivalent=30 with an explicit density assumption. Never equate
        milliliters and grams silently.

        QUANTITY PRECEDENCE IS ABSOLUTE: explicit user quantity/package math > locally appropriate
        default quantity > source serving or package. Structured `quantity` and `unit` are
        authoritative. A website may provide nutrition and describe its source serving, but it
        MUST NOT replace the logged amount. If the user supplied a 320 g pack and today's page
        lists 380 g, keep the user's calculated amount and report 380 g only in the optional
        `sourcePackageQuantity`/`sourcePackageUnit` fields.

        Example: for a logged 250 ml drink whose US source lists a 12 US fl oz / 355 ml can,
        return quantity=250, unit="ml", sourceServingQuantity=12,
        sourceServingUnit="US fl oz", and the nutrient fields for the full 12 fl oz source
        serving. Do NOT return quantity=355 and do NOT claim the full can nutrition is for 250 ml.

        For Germany, when the structured input resolves an unspecified Red Bull can or Red Bull
        Edition (including Juneberry) to 250 ml, keep exactly 250 ml. Explicit 355 ml, 473 ml, or
        half of a 250 ml can always wins. Normalize any differently sized international source
        serving to per 100 ml before applying the structured quantity. Do not use a source package
        as the logged amount. `sourcePackageQuantity` is informational and distinct from
        `sourceServingQuantity`.

        Return only:
        {
          "items": [{
            "name": string,
            "brand": string|null,
            "quantity": positive number,

            "unit": string,
            "gramsEquivalent": positive number|null,
            "calories": non-negative number,
            "proteinGrams": non-negative number,
            "carbohydrateGrams": non-negative number,
            "fatGrams": non-negative number,
            "fiberGrams": non-negative number|null,
            "sourceName": string|null,
            "sourceUrl": string|null,
            "sourceServingQuantity": positive number,
            "sourceServingUnit": string,
            "sourceServingGramsEquivalent": positive number|null,
            "sourceCountry": ISO-3166 alpha-2 country code|null,
            "sourcePackageQuantity": positive number|null,
            "sourcePackageUnit": string|null,
            "isEstimate": boolean,
            "confidence": number|null,
            "assumptions": [string]
          }],
          "overallConfidence": number|null
        }

        Structured meal: ${json.encodeToString(intent)}
    """.trimIndent()

    fun adjustPortion(current: PortionContext, correction: String, json: Json): String = """
        Interpret only the requested portion change. Do not calculate new calories or macros.
        Return a mathematically consistent quantity multiplier for the app to validate and apply.
        Recognize mg/g/kg and EL/Essloeffel/tbsp/tablespoon or
        TL/Teeloeffel/tsp/teaspoon. An unqualified German Löffel/Loeffel means EL. Use exactly
        15 ml per tablespoon/EL and 5 ml per teaspoon/TL.
        Do not convert spoon volume to mass unless an exact total gram equivalent is already
        supplied; never assume density.
        If the statement is ambiguous, set requiresConfirmation true and describe the proposed
        interpretation. Return only strict JSON.

        Current item: ${json.encodeToString(current)}
        User correction: ${correction.trim()}

        Shape:
        {
          "newQuantity": positive number,
          "newUnit": string,
          "multiplier": positive number,
          "newGrams": positive number|null,
          "interpretation": string,
          "requiresConfirmation": boolean
        }
    """.trimIndent()

    fun identifyFoodFromPhoto(): String = """
        Identify visible foods and estimate portions from this image. Do not claim exact calories.
        Return only strict JSON in this shape:
        {
          "items": [{
            "name": string,
            "visibleIngredients": [string],
            "estimatedQuantity": positive number|null,
            "unit": string|null,
            "estimatedGrams": positive number|null,
            "confidence": number|null
          }],
          "notes": [string]
        }
    """.trimIndent()
}
