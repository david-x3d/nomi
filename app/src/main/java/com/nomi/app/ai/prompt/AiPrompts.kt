package com.nomi.app.ai.prompt

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiPrompts {
    fun parseFood(text: String): String = """
        You convert natural-language meal descriptions into strict JSON.
        The input may be in any language Nomi supports - English, German, Spanish, French,
        Italian, Dutch, Portuguese, Albanian, Swedish, or Turkish. Preserve the user's language
        but normalize units.
        Do not invent nutrition values in this step. Extract every food, quantity, unit, brand,
        preparation, and any assumptions needed. A saved-meal phrase such as "mein übliches
        Frühstück" should be returned in mealReference.

        NAME EACH FOOD THE WAY A PERSON WOULD WRITE IT DOWN. `name` is what the user will read
        in their food log, so it is the food itself, never the sentence they typed.
        "mcdonalds cheeseburger with fries and coke" is three items: "Cheeseburger" and "Fries"
        with brand "McDonald's", and "Coca-Cola" with brand "Coca-Cola". The brand belongs in
        `brand`, the amount in `quantity`/`unit`, the preparation in `preparation` - none of
        them may be repeated in `name`.
        Correct spelling and capitalization in the user's own language, following that
        language's own rules, and answer in that language: German input gives German names with
        capitalized nouns ("pommes mit majo" gives "Pommes" and "Mayonnaise"), English input
        gives English names, French input gives French names ("pates au beurre" gives "Pâtes"
        and "Beurre"), and so on for every supported language. Expand a
        colloquial short form to the food's common name ("coke" gives "Coca-Cola"). Keep any
        word that changes what the food is or contains, such as "Zero", "Vanille", or "vegan".
        Never invent a variant, brand, or ingredient the user did not write.

        EXPECT TYPOS. This is typed quickly on a phone, so assume slips rather than new
        products: transposed and dropped letters ("red bull junebrry" is Red Bull Juneberry,
        "haferflcken" is Haferflocken), missing or extra spaces ("redbull", "hafer flocken"),
        accented letters typed without their accents or spelled out ("muesli" is Müsli, "kaese"
        is Käse, "creme brulee" is crème brûlée, "acucar" is açúcar, "cig kofte" is çiğ köfte),
        and phonetic spellings. Resolve the intended product and return its correct name with
        the accents its language requires.
        CORRECTING A SLIP IS NOT THE SAME AS CHANGING THE PRODUCT. A variant is often one short
        word, and swapping it logs a different food behind a confident name. Never turn one
        edition, flavour, or variant into another because the spelling is close: "Juneberry"
        must not become "Watermelon", "Zero" must not become "Original". When the intended
        product genuinely cannot be resolved, keep what the user wrote rather than picking the
        nearest famous product, and say so in assumptions.

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
        You MUST perform live web research for every structured input item. Never answer from model
        memory and never return a model-only guess. For generic foods and branded foods without an
        exact fetched official manufacturer table, compare at least two independent websites with
        different hostnames for EACH item. Every result, including an estimate, MUST be grounded in
        provider-returned web evidence. Nomi attaches provider evidence outside the JSON.
        Do not return `sourceUrl` or `supportingSourceUrls`, and never invent or rewrite URLs.
        If neither an exact fetched official table nor two relevant cited websites can be found after
        the required search-and-fetch retries, fail instead of guessing.
        FAILING MEANS RETURNING AN ERROR, NEVER FAKE DATA: when live search finds no usable
        nutrition data of any kind for an item, return exactly {"error": "<short reason>"} as
        the entire response. NEVER return zero calories and zero macros as a substitute for
        missing data, and NEVER write failure text such as "no evidence found" into name,
        sourceName, sourceProductName, or any other data field.
        THE ERROR IS A LAST RESORT, NOT A DEFAULT. Do NOT return an error merely because a food
        is generic, because its exact variety or cut is unspecified, because no branded product
        matches, or because the search results were thin. Common foods always have reputable
        reference data; answer them from it.
        Never pretend that two pages on the same website are independent confirmation.
        IDENTIFY THE EXACT PRODUCT FIRST: brand, product name, variant/flavour, market, and
        package size when relevant. Never silently substitute a similar product. "iglo Chicken
        Nuggets im Backteig" must not be answered with another iglo nugget product, a restaurant
        chain's nuggets, or generic chicken nuggets. If only a similar product can be found,
        set isEstimate=true and name the substitution in assumptions.
        GENERIC FOODS ARE DIFFERENT AND MUST STILL BE ANSWERED: an unbranded everyday food such
        as "Steak", "Apfel", "Reis", or "Haferflocken" has no exact product to match, so an
        exact branded match is NOT required for it. Research it from reputable nutrition
        reference databases, official food-composition tables, or public-health sources. When
        the variety, cut, or preparation is unspecified, choose the most common one for the
        user's market, set isEstimate=true, and state that choice in assumptions - for example
        "Steak" without a cut may use a typical beef steak, grilled, and say so. Returning an
        error for a common generic food is wrong.
        `name` IS A DISPLAY NAME, NOT A SOURCE TITLE: it is the line the user reads in their
        food log, so keep it the short everyday name of the food - "Cheeseburger", "Pommes",
        "Coca-Cola". Never copy the cited page's product title into it; that is exactly what
        `sourceProductName` is for and it must still be the page's exact title. The brand
        belongs in `brand`, the amount in `quantity`/`unit`, and the packaging and preparation
        in `assumptions`, so none of them are repeated in `name`. Keep every word that changes
        what the food is or contains - "Coca-Cola Zero" and "Skyr Vanille" stay whole, because
        dropping the variant would put the wrong food in the log. Spell and capitalize the name
        in the language of the user's input, following that language's own rules: German input
        gives German names with capitalized nouns, English input gives English names, and the
        same holds for every other language the user may write in. A shortened display name
        NEVER licenses
        researching a different, less specific product: identify the exact product first as
        required below, then name it for the log.
        SOURCE AND DATA MUST MATCH: `sourceProductName` MUST be the product title exactly as the
        cited page prints it, and `sourceDomain` MUST be the hostname of the page the nutrition
        values were actually read from. Never attribute one site's values to another site and
        never invent a domain, title, or citation. Before answering, sanity-check each item:
        protein*4 + carbohydrates*4 + fat*9 must roughly match the source calories; if it is far
        off, re-read the source for unit or serving mistakes instead of returning it.
        BRANDED SEARCH-AND-FETCH WORKFLOW - FOLLOW THESE STEPS IN ORDER:
        1. EXACT-PRODUCT SEARCH: use the available web-search tool with the quoted brand, complete
        product name, variant/flavour, market, and a nutrition term such as Naehrwerte or nutrition.
        Search for the exact product before trying retailers, databases, or a generic substitute.
        2. OFFICIAL-PAGE FETCH: identify likely official manufacturer product URLs from the results,
        preferring the manufacturer's own domain. Use the available page-fetch/open tool on those
        URLs and read the returned page content, including accordions or extracted nutrition-table
        text. A search-result snippet is discovery evidence only and is NEVER the final source of
        truth for a branded food. Missing kcal or macros in a snippet does NOT mean the product page
        lacks them and is not a reason to return an error.
        3. ALTERNATIVE QUERY: if the exact search finds no usable official table, or a candidate page
        cannot be fetched, perform at least one materially different query - for example a shorter
        exact product name plus the brand, or a site-restricted manufacturer-domain query. Fetch
        every likely official product page found by that alternative query before using a lower-
        priority source.
        4. REFUSAL GATE: return the error envelope only after the exact-product search, at least one
        alternative query, and fetch attempts for all likely official product pages have failed to
        produce usable nutrition data. Search snippets without nutrition values do not satisfy this
        gate. If an exact fetched manufacturer page contains per-100 g or per-100 ml values, that
        nutrition table is canonical and sufficient. Seek a second independent site to corroborate
        product identity when available, but never replace the manufacturer's printed values with
        retailer or database values.

        BRANDED PACKAGED FOODS: ALWAYS search the manufacturer's official website for the exact
        product first and read its printed nutrition table. When that official table exists,
        never return an estimate and never replace its numbers with another site's numbers.
        Extract the per-100 g / per-100 ml column directly: return it as
        sourceServingQuantity=100 with sourceServingUnit="g" or "ml" and the nutrient fields
        exactly as the table prints them. Fall back to a per-serving/per-piece basis only when
        the label provides no per-100 values.
        VERIFIED VALUES ARE EXACT: when reliable nutrition data is available, report the
        source's printed values digit for digit. Never round, smooth, average, or otherwise
        modify a verified value. Only when no exact value can be confirmed and an estimate is
        unavoidable, set isEstimate=true and choose the slightly higher plausible calorie and
        macro values rather than underestimating, so tracking errs against under-counting.
        CALORIE EXPLANATION IS REQUIRED FOR EVERY ITEM: return a short, user-facing
        `calorieExplanation` in the language of the user's input. Explain the main reason for
        this item's calorie total using only the returned macros and portion: compare the energy
        contribution of fat (9 kcal/g), carbohydrates (4 kcal/g), and protein (4 kcal/g), and
        mention the logged portion when it materially increases the total. Say, for example,
        that a cheese is calorie-dense mainly because it contains a lot of fat. Do not invent
        ingredients, health claims, or unsupported causes. This is a concise result summary, not
        hidden chain-of-thought; keep it to one or two sentences and provide it for every item.
        Research nutrition for the structured meal below. For branded and restaurant foods,
        prefer official manufacturer or restaurant sources, then reliable food databases and Open
        Food Facts. Supermarket, grocery, retailer, and reseller product pages are explicitly allowed
        when they identify the exact product and show its nutrition label. Use them to cross-check the
        manufacturer or as the best available product source. For generic food, compare reputable
        reference databases or public-health sources. Label every uncertain portion or nutrient
        estimate. If sources disagree, prefer the current official product label for the user's market
        and record the conflict in assumptions. Do not include prose outside JSON.
        For users in Germany, apply this exact source order:
        1. official German manufacturer or restaurant product page;
        2. official EU/German product data;
        3. major German retailer, reseller, supermarket, or grocery product page;
        4. reliable nutrition database;
        5. international/US source;
        6. a clearly labeled estimate only after live search found no reliable product-specific data.
        A lower-priority source is a fallback, not permission to replace a German quantity.
        The user's locale country is ${localeCountry?.takeIf { it.isNotBlank() } ?: "unknown"}.
        If it is DE, prefer the official German manufacturer/restaurant product page and German
        product formulation. Use another country's source only when no appropriate German source
        is available, and identify that source country.

        PRODUCT IDENTITY: Treat `name`, `brand`, and product-identity assumptions in the structured
        input as search hints for the exact product, not as generic words to translate. Search the
        quoted product name together with its brand and Germany. A short unfamiliar token in a food
        log may be a German-market product name: try an exact product search before declaring it
        unknown or interpreting its ordinary meaning. For example, Duplo in a German food log is
        the Ferrero chocolate-covered wafer bar, not a toy, building block, or duplicate. Preserve
        stated variants such as White, Dark, XXL, vegan, or a flavour; never silently substitute
        the classic version. Do not invent a variant when none was stated.
        Public web search may use any accessible website that contains relevant nutrition or
        portion evidence. Source quality and independent cross-checking still determine trust.

        Return exactly one result for every structured input item, in the same order. `quantity`
        and `unit` MUST describe the amount the user logged. The calories and macro fields MUST
        describe the cited source serving, whose amount MUST be stated separately in
        `sourceServingQuantity` and `sourceServingUnit`. Never silently treat source-serving
        nutrition as nutrition for the logged amount. NEVER calculate the consumed amount's
        calories or macros yourself: Nomi app code deterministically normalizes your reported
        values to per 100 g/ml (or per 100 compatible count units) and scales them to the
        logged amount, and it rejects results whose basis does not reconcile.

        `uncertaintyPercent` IS REQUIRED WHENEVER isEstimate IS TRUE and must be null otherwise.
        It is the half-width of the plausible range around your values, in percent: a food that
        could reasonably be 500 to 700 kcal reported as 600 has an uncertainty of 17. An exact
        figure read from a manufacturer's own table is not an estimate and carries no range.

        MICRONUTRIENTS ARE REPORTED ONLY WHEN PUBLISHED. `sugarGrams`, `saturatedFatGrams`, and
        `sodiumMilligrams` follow the same serving basis as the macros. Report a value only when
        the cited source actually publishes that row; otherwise return null. Never infer, derive,
        or default a micronutrient to zero, because a null means "not published" and a zero is a
        claim about the food. `sugarGrams` is total sugars and cannot exceed `carbohydrateGrams`;
        `saturatedFatGrams` cannot exceed `fatGrams`. Sodium is reported in MILLIGRAMS: if the
        source prints salt in grams, convert with sodium_mg = salt_g * 400, and if it prints
        sodium in grams, multiply by 1000.

        CRITICAL SERVING-BASIS RULE: `calories`, `proteinGrams`, `carbohydrateGrams`,
        `fatGrams`, and `fiberGrams` MUST describe EXACTLY the amount given by
        `sourceServingQuantity` and `sourceServingUnit`. They MUST NEVER describe the user's
        logged quantity unless the cited source itself explicitly publishes nutrition for
        exactly that serving. Whenever reliable per-100-g or per-100-ml nutrition exists,
        ALWAYS return sourceServingQuantity=100 with the nutrient values PER 100 g/ml. Do NOT
        pre-scale them to the consumed amount; Nomi performs all portion scaling itself.
        Example: the user logged 329 g steak and the source publishes per 100 g
        172 kcal, 21 g protein, 0 g carbohydrates, 9.5 g fat.
        CORRECT: quantity=329, unit="g", sourceServingQuantity=100, sourceServingUnit="g",
        calories=172, proteinGrams=21, carbohydrateGrams=0, fatGrams=9.5.
        WRONG: calories=566, proteinGrams=69, fatGrams=31.3 - those are already scaled to
        329 g, so Nomi would scale them a second time. Never do this.
        Before returning JSON, verify for every item: if sourceServingQuantity=100 and
        sourceServingUnit is "g" or "ml", then every nutrient field is the PER-100 value from
        the cited source, regardless of `quantity`.
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
            "calorieExplanation": non-empty string,
            "fiberGrams": non-negative number|null,
            "sugarGrams": non-negative number|null,
            "saturatedFatGrams": non-negative number|null,
            "sodiumMilligrams": non-negative number|null,
            "sourceName": non-empty string,
            "sourceProductName": non-empty string,
            "sourceDomain": hostname string|null,
            "sourceServingQuantity": positive number,
            "sourceServingUnit": string,
            "sourceServingGramsEquivalent": positive number|null,
            "sourceCountry": ISO-3166 alpha-2 country code|null,
            "sourcePackageQuantity": positive number|null,
            "sourcePackageUnit": string|null,
            "isEstimate": boolean,
            "uncertaintyPercent": number|null,
            "confidence": number|null,
            "assumptions": [string]
          }],
          "overallConfidence": number|null
        }

        Structured meal: ${json.encodeToString(intent)}
    """.trimIndent()

    fun researchNutritionAmountResolution(
        intent: ParsedFoodIntent,
        json: Json,
        localeCountry: String? = null,
        unresolvedItemIndexes: List<Int>,
    ): String {
        val targets = unresolvedItemIndexes.joinToString(", ") { index ->
            val item = intent.items.getOrNull(index)
            "item ${index + 1} (${item?.brand?.let { "$it " }.orEmpty()}${item?.name ?: "unknown"})"
        }
        return researchNutrition(intent, json, localeCountry) + "\n\n" + """
            AMOUNT-RESOLUTION RETRY: The previous live search found nutrition, but it omitted the
            exact conversion required to match the user's amount for: $targets.
            Perform a fresh, targeted live web search for each listed product's individual weight.
            For a branded packaged food, search the current official manufacturer page first, then
            German retailer or product-catalog pages for an explicit per-piece weight or a pack
            declaration containing both net mass and piece count. Use either the stated individual
            weight or deterministic pack math: pack net grams / pack piece count = grams per piece;
            logged piece count * grams per piece = `gramsEquivalent`.
            Example evidence "5 x 28 g" with quantity=1 and unit="piece" means
            gramsEquivalent=28. Keep quantity=1 and unit="piece"; DO NOT replace them with 28 g.
            If the logged count is greater than one, `gramsEquivalent` is the TOTAL mass of all
            logged pieces. Record the exact pack calculation and the market in assumptions.
            Do not use a different flavour, size, edition, or country formulation. For a branded
            product, never invent or estimate a piece weight: return the error envelope if no exact
            product-specific weight or exact pack math can be verified on the live web.
            For an unbranded generic food only, a reputable food-specific serving-weight estimate
            is allowed when exact data does not exist; then set isEstimate=true and explain it.
            Return the complete FoodAnalysis JSON for every input item in the original order, even
            though only the listed items need an amount conversion. All normal source, serving,
            citation, product-identity, and JSON-schema rules above still apply.
        """.trimIndent()
    }

    /**
     * The last resort when sourced research cannot produce a usable result.
     *
     * Everything about sourcing is dropped here on purpose: the user ate something and wants it
     * in their log, and a labeled estimate is far more useful to them than an error. The serving
     * contract stays identical to [researchNutrition] so the same deterministic normalizer scales
     * the answer, and every item is marked as an estimate before it is shown.
     */
    fun estimateNutrition(
        intent: ParsedFoodIntent,
        json: Json,
        localeCountry: String? = null,
    ): String = """
        Estimate the nutrition of the structured meal below from your own food knowledge.
        No web research is required and no citation is expected. You MUST return a result for
        every item: never return an error, never return an empty list, and never return zero
        calories with zero macros for a food that contains energy.

        Answer for the user's market (locale country
        ${localeCountry?.takeIf { it.isNotBlank() } ?: "unknown"}) and for the most common
        variety, cut, brand formulation, or preparation when the input does not specify one.
        State every such choice in `assumptions` in the language of the input. Prefer the
        slightly higher plausible values so tracking errs against under-counting.

        SERVING BASIS - THIS IS THE PART THAT MUST BE EXACT:
        - `quantity` and `unit` MUST repeat the amount the user logged, unchanged.
        - `sourceServingQuantity` MUST be 100 and `sourceServingUnit` MUST be "g" for foods or
          "ml" for drinks.
        - `calories`, `proteinGrams`, `carbohydrateGrams`, `fatGrams`, and `fiberGrams` MUST be
          the values for 100 g / 100 ml, NOT for the logged amount. Nomi scales them itself, so
          pre-scaled values would be counted twice.
        - When the logged unit is not g or ml (piece, slice, serving, EL, TL, cup, ...),
          `gramsEquivalent` MUST be the total grams or millilitres of the ENTIRE logged amount,
          for example quantity=2, unit="slices", gramsEquivalent=60 for two 30 g slices.
        - protein*4 + carbohydrates*4 + fat*9 must roughly match the calories per 100.

        REPORT HOW UNCERTAIN YOU ARE. `uncertaintyPercent` is the half-width of the plausible
        range around your values, as a percentage of them: if this food could reasonably be
        anywhere from 500 to 700 kcal and you answered 600, that is 17. Use a small number for a
        standard packaged item and a large one for a homemade dish whose recipe you cannot see.
        Nomi uses it only to honour the user's own over- or under-estimate preference, so report
        it honestly rather than defensively.

        For every item, also return a concise `calorieExplanation` in the language of the input.
        Explain whether fat, carbohydrates, protein, or the logged portion contributes most to
        the calories, using only the nutrition values you returned. Fat provides 9 kcal/g;
        carbohydrates and protein provide 4 kcal/g. This is a user-facing summary, not hidden
        chain-of-thought, and it must never invent ingredients or unsupported health claims.

        Set `isEstimate` to true for every item. Set `sourceName` to "Estimate" and leave
        `sourceUrl`, `sourceProductName`, and `sourceDomain` out. Report `sugarGrams`,
        `saturatedFatGrams`, and `sodiumMilligrams` only when you are reasonably confident,
        otherwise null. Keep `name` the short everyday display name in the user's language,
        with the brand in `brand`. Return exactly one result per input item, in the same order.

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
            "calorieExplanation": non-empty string,
            "fiberGrams": non-negative number|null,
            "sugarGrams": non-negative number|null,
            "saturatedFatGrams": non-negative number|null,
            "sodiumMilligrams": non-negative number|null,
            "sourceName": "Estimate",
            "sourceServingQuantity": 100,
            "sourceServingUnit": "g"|"ml",
            "isEstimate": true,
            "uncertaintyPercent": number,
            "confidence": number|null,
            "assumptions": [string]
          }],
          "overallConfidence": number|null
        }

        Structured meal: ${json.encodeToString(intent)}
    """.trimIndent()

    /**
     * The routing decision, made by the cheapest model available.
     *
     * Its only job is to keep a web search from running for something multiplication can
     * answer. The asymmetry stated in the prompt is the important part: researching an edit
     * that only needed arithmetic wastes a search, while scaling an edit that actually changed
     * the food leaves the wrong nutrition behind a number the user trusts more for having
     * corrected it. So anything uncertain is sent to research.
     */
    fun classifyFoodEdit(current: PortionContext, edit: String, json: Json): String = """
        Classify what the user's correction changes about an already-researched food. Return
        strict JSON only. Do not calculate nutrition and do not restate the food.

        Types:
        - "PORTION_ONLY": only how much of the SAME food was eaten changed. Examples: "half",
          "1/2", "50%", "2x", "double", "only ate 3 of the 6 pieces", "200g instead of 400g",
          "one third", "75% of it", "55% of the package".
        - "CONTENT_CHANGE": the food, its ingredients, brand, restaurant, product, or
          preparation changed. Examples: "actually it was chicken, not tuna", "remove the
          cheese", "it was the large McDonald's fries", "this was from Burger King", "add 20g
          mayonnaise", "wrong brand", "different product".
        - "RESEARCH_REQUIRED": the correction cannot be resolved without looking the food up
          again, including anything you are unsure about.

        WHEN IN DOUBT, DO NOT CHOOSE PORTION_ONLY. Being wrong in that direction leaves
        incorrect nutrition attached to a food the user believes they just corrected. Being
        wrong the other way only costs one extra search. Report honest `confidence`.

        For PORTION_ONLY, also return the arithmetic as `portion`. Nomi computes every nutrient
        itself from that instruction, so never return nutrition values:
        - a share of the current amount: {"operation":"SCALE","factor":0.5}
        - a replacement amount: {"operation":"SET_QUANTITY","quantity":176,"unit":"g"}
        Use SCALE for fractions, percentages and multipliers; use SET_QUANTITY only when the
        user names an explicit new amount with a unit. Omit `portion` for every other type.

        Recognize mg/g/kg and EL/Essloeffel/tbsp/tablespoon or TL/Teeloeffel/tsp/teaspoon. An
        unqualified German Löffel/Loeffel means EL.

        Current item: ${json.encodeToString(current)}
        User correction: ${edit.trim()}

        Shape:
        {
          "type": "PORTION_ONLY"|"CONTENT_CHANGE"|"RESEARCH_REQUIRED",
          "confidence": number between 0 and 1,
          "reason": short string,
          "portion": {
            "operation": "SCALE"|"SET_QUANTITY",
            "factor": positive number|null,
            "quantity": positive number|null,
            "unit": string|null
          }|null
        }
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

    /**
     * Reading a printed nutrition table. Nothing here is researched or estimated - the numbers
     * are on the package in the user's hand, so the only failure mode worth allowing is
     * admitting the photo is unreadable.
     */
    fun readNutritionLabel(): String = """
        Read the nutrition table printed on the packaging in this image. Report ONLY what the
        label actually prints. Never research, never estimate, never fill a value from memory
        or from what the product usually contains.

        If the table is unreadable, cut off, blurred, or not present in the image, return
        exactly {"error": "<short reason>"} as the entire response. That is the correct answer
        for a bad photo. NEVER invent numbers to avoid returning an error, and never write
        failure text into a data field.

        Read one single column and say which one it is. Prefer the per-100 g or per-100 ml
        column when the label has one: then basisQuantity=100 and basisUnit is "g" or "ml".
        Only if the label has no per-100 column, read the per-serving/per-piece column and set
        basisQuantity and basisUnit to exactly that serving. Never mix values from two columns
        and never convert between columns yourself - Nomi scales the values it is given.

        Energy: report kilocalories in `calories`. If the label prints only kJ, convert with
        1 kcal = 4.184 kJ and say so in notes. Report grams for the macro fields exactly as
        printed, including decimals; "<0,5 g" is 0. German labels use a comma as the decimal
        separator, so "1,5 g" is 1.5.

        EU tables print "davon gesättigte Fettsäuren" (saturates) indented under fat and "davon
        Zucker" (sugars) indented under carbohydrate. Read those indented rows into
        `saturatedFatGrams` and `sugarGrams`; they are parts of the row above, so they can never
        exceed it. Salt is printed as "Salz"/"Salt" in grams: report SODIUM in milligrams with
        sodium_mg = salt_g * 400. A label that prints sodium directly in grams is * 1000. Leave
        any of these null when that row is not printed - never derive one from another.

        `productName` and `brand` come from the front of the package if they are visible in the
        image; leave them null rather than guessing. `packageQuantity`/`packageUnit` are the net
        content ("500 g", "0,33 l") and are informational only. `servingLabel` is the serving
        size as the label words it ("1 Portion (30 g)"), if it prints one.

        Return only this JSON shape:
        {
          "productName": string|null,
          "brand": string|null,
          "basisQuantity": positive number,
          "basisUnit": string,
          "calories": non-negative number,
          "proteinGrams": non-negative number,
          "carbohydrateGrams": non-negative number,
          "fatGrams": non-negative number,
          "fiberGrams": non-negative number|null,
          "sugarGrams": non-negative number|null,
          "saturatedFatGrams": non-negative number|null,
          "sodiumMilligrams": non-negative number|null,
          "packageQuantity": positive number|null,
          "packageUnit": string|null,
          "servingLabel": string|null,
          "confidence": number|null,
          "notes": [string]
        }
    """.trimIndent()

    /**
     * Describes a photo. It does not price it.
     *
     * This model is fast and cheap and cannot search the web, so it is asked for the one thing
     * a picture can actually establish: what is on the plate and roughly how much. Every
     * nutrition number comes from the research step that follows, working from this
     * description. A calorie figure guessed from pixels would look exactly as authoritative as
     * a researched one and be worth nothing, so it is forbidden outright.
     */
    fun identifyFoodFromPhoto(): String = """
        Describe the food in this image so it can be researched afterwards. You are the eyes of
        this pipeline, not its source of nutrition.

        NEVER report calories, macros, or any nutrition value, and never estimate them silently.
        A separate research step looks up real published nutrition from your description. Your
        only job is to say what is there and how much of it, precisely enough to be looked up.

        For each distinct food, drink, side, sauce, or topping you can see, report:
        - `name`: what it is, as specifically as the image supports ("salmon nigiri", not "sushi").
          Include the preparation when it is visible: grilled, fried, breaded, raw, steamed.
        - `visibleIngredients`: components you can actually see - toppings, garnishes, dressings,
          visible cheese, sauces on or beside the dish. Do not list ingredients you merely expect
          a dish to contain.
        - `estimatedQuantity` and `unit`: count the pieces when they are countable ("8", "pieces");
          otherwise estimate the served portion.
        - `estimatedGrams`: your best estimate of the total mass of that item, for the whole
          amount shown, not per piece.
        - `confidence`: your honest confidence for that item.

        Count countable things exactly rather than approximating: eight pieces of sashimi is
        eight, not "some". Use everyday objects in frame for scale where you can.

        List dips, sauces, and drinks as their own items when they are visible, and put anything
        served on the side but plainly not eaten into `notes` instead of `items` - for example a
        soy sauce dish that is still full.

        Together the items should read like a careful description of the plate, for example:
        8 pieces salmon sashimi around 160 g total, a small portion of shredded daikon, with
        soy sauce present but not counted.

        Say what you are unsure about in `notes`, including anything hidden, stacked, or cut off
        by the frame. The user reads and corrects this description before anything is researched,
        so an honest doubt is more useful than a confident guess.

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

    fun readRestaurantMenu(): String = """
        Read every orderable food and drink that is visibly printed on this restaurant menu
        page. This is transcription and organization, not nutrition research. Never invent an
        item, ingredient, price, number, restaurant, or missing word.

        Return a complete list, including starters, mains, sides, sauces, desserts, drinks,
        variants, and numbered combination meals. Do not stop after a sample. Keep the printed
        language. Ignore opening hours, addresses, legal text, allergens-only legends, decorative
        slogans, and crossed-out items.

        For every item return:
        - number: the printed ordering number or code, otherwise null.
        - name: the exact useful dish name, without repeating its number or price.
        - description: the printed description and ingredients, combined into readable plain
          text; null when none is printed. Do not add ingredients from general knowledge.
        - category: the nearest visible menu heading, such as Pizza, Burgers, Drinks, or Desserts.
        - price: the price exactly as printed, including currency; null when unreadable.
        - quantityText: the exact printed quantity for the whole orderable serving, otherwise null.
          Keep combined serving forms such as "3 Kugeln / 165 g" together. Do not put ingredient
          amounts here: in "1 Stueck - 180 g Rind", quantityText is "1 Stueck" because
          180 g describes only the beef component.

        If one dish has separately orderable sizes or variants, return separate items when each
        has its own price or number. Preserve accents and menu-specific names. Use notes for cut
        off, blurred, or uncertain areas instead of guessing them.

        Return only strict JSON in this shape:
        {
          "restaurantName": string|null,
          "items": [{
            "number": string|null,
            "name": string,
            "description": string|null,
            "category": string|null,
            "price": string|null,
            "quantityText": string|null
          }],
          "notes": [string]
        }
    """.trimIndent()
}
