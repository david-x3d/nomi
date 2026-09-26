package com.nomi.app.ai.model

import java.util.Locale

/** Unit identity only; a package, serving or bar is never interchangeable with a piece. */
object QuantityUnits {
    fun normalize(raw: String): String = when (val unit = raw.trim().lowercase(Locale.ROOT)) {
        "pieces", "item", "items", "pc", "pcs", "each", "stück", "stücke", "stuck", "stucke", "stueck", "stuecke" -> "piece"
        "servings", "portion", "portions", "portionen" -> "serving"
        "package", "packages", "packs", "packet", "packets", "packung", "packungen" -> "pack"
        "bars", "riegel" -> "bar"
        "slices", "scheibe", "scheiben" -> "slice"
        "bottles", "flasche", "flaschen" -> "bottle"
        "cans", "dose", "dosen" -> "can"
        "cups", "tasse", "tassen" -> "cup"
        "tablespoon", "tablespoons", "tbs", "el", "esslöffel", "essloeffel", "essloffel" -> "tbsp"
        "teaspoon", "teaspoons", "tl", "teelöffel", "teeloeffel", "teeloffel" -> "tsp"
        "grams", "gram", "gramm" -> "g"
        "kilograms", "kilogram", "kilogramm" -> "kg"
        "milliliter", "milliliters", "millilitre", "millilitres" -> "ml"
        "liter", "liters", "litre", "litres" -> "l"
        else -> unit
    }

    val counted = setOf("piece", "serving", "pack", "bar", "slice", "bottle", "can", "cup")
    fun isCount(raw: String): Boolean = normalize(raw) in counted
}
