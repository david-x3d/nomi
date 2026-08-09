package com.nomi.app.ai.parsing

import com.nomi.app.ai.model.ParsedFoodItem
import java.text.Normalizer
import java.util.Locale

/**
 * Adds stable identity hints for distinctive products commonly logged in Germany.
 *
 * Nutrition never lives in this catalog: recipes, package sizes, and variants can change. The
 * resolver only gives the research provider enough identity to find the current German product.
 * Unknown products continue through research unchanged.
 */
object GermanProductResolver {
    private val genericSuffixes = setOf(
        "bar", "riegel", "snack", "stuck", "stueck", "piece",
    )

    private val products = listOf(
        ProductHint("Duplo", "Ferrero", "chocolate-covered wafer bar", "duplo", "duplo riegel"),
        ProductHint("Kinder Riegel", "Ferrero", "milk chocolate bar", "kinder riegel", "kinderriegel"),
        ProductHint("Kinder Bueno", "Ferrero", "filled wafer bar", "kinder bueno"),
        ProductHint("Kinder Milch-Schnitte", "Ferrero", "chilled milk snack", "milch schnitte", "milchschnitte", "kinder milch schnitte"),
        ProductHint("Kinder Pingui", "Ferrero", "chilled milk snack", "kinder pingui", "pingui"),
        ProductHint("hanuta", "Ferrero", "hazelnut wafer", "hanuta"),
        ProductHint("nutella", "Ferrero", "hazelnut cocoa spread", "nutella"),
        ProductHint("Giotto", "Ferrero", "hazelnut confectionery", "giotto"),
        ProductHint("Raffaello", "Ferrero", "coconut confectionery", "raffaello"),
        ProductHint("Mon Chéri", "Ferrero", "cherry praline", "mon cheri"),
        ProductHint("Yogurette", "Ferrero", "yogurt-flavoured chocolate bar", "yogurette"),
        ProductHint("Tic Tac", "Ferrero", "mint or fruit sweet", "tic tac", "tictac"),
        ProductHint("Knoppers", "Storck", "wafer snack", "knoppers", "knopper"),
        ProductHint("Toffifee", "Storck", "hazelnut caramel confectionery", "toffifee"),
        ProductHint("merci", "Storck", "chocolate confectionery", "merci"),
        ProductHint("nimm2", "Storck", "fruit sweet", "nimm2", "nimm 2"),
        ProductHint("Werther's Original", "Storck", "caramel sweet", "werthers original", "werther original"),
        ProductHint("MAOAM", "HARIBO", "chewy sweet", "maoam"),
        ProductHint("HARIBO Goldbären", "HARIBO", "gummy bears", "haribo goldbaren", "goldbaren", "goldbaeren"),
        ProductHint("müllermilch", "Müller", "flavoured milk drink", "mullermilch", "muellermilch"),
        ProductHint("Actimel", "Danone", "fermented milk drink", "actimel"),
        ProductHint("FruchtZwerge", "Danone", "fresh cheese snack", "fruchtzwerge", "frucht zwerg"),
        ProductHint("Mini Babybel", "Babybel", "portion cheese", "babybel", "mini babybel"),
        ProductHint("CORNY", "CORNY", "cereal bar", "corny", "corny riegel"),
        ProductHint("LEIBNIZ Butterkeks", "Bahlsen", "butter biscuit", "leibniz keks", "leibniz butterkeks"),
        ProductHint("Prinzen Rolle", "DeBeukelaer", "sandwich biscuit", "prinzenrolle", "prinzen rolle"),
    )

    fun enrich(
        userText: String,
        item: ParsedFoodItem,
        itemCount: Int,
        localeCountry: String?,
    ): ParsedFoodItem {
        if (!localeCountry.equals("DE", ignoreCase = true)) return item

        val identity = listOfNotNull(item.brand, item.name).joinToString(" ").normalizedProductText()
        val identityMatch = bestMatch(identity)
        val match = identityMatch ?: if (itemCount == 1) bestMatch(userText.normalizedProductText()) else null
        match ?: return item

        val currentName = item.name.normalizedProductText()
        val canonicalizeName = identityMatch == null || match.matchesBareName(currentName)
        val assumption = "German product identity: ${match.canonicalName} by ${match.brand} " +
            "(${match.description}). Research the exact German product and preserve any stated variant."

        return item.copy(
            name = if (canonicalizeName) match.canonicalName else item.name,
            brand = match.brand,
            assumptions = (item.assumptions + assumption).distinct().takeLast(12),
        )
    }

    private fun bestMatch(text: String): ProductHint? = products
        .asSequence()
        .filter { hint -> hint.aliases.any { alias -> text.containsWholePhrase(alias) } }
        .maxByOrNull { hint -> hint.aliases.maxOf { it.length } }

    private fun ProductHint.matchesBareName(text: String): Boolean {
        if (text in aliases) return true
        return aliases.any { alias ->
            val remaining = text.removePrefix(alias).trim()
            remaining.isNotEmpty() && remaining.split(' ').all { it in genericSuffixes }
        }
    }

    private fun String.containsWholePhrase(phrase: String): Boolean =
        this == phrase || startsWith("$phrase ") || endsWith(" $phrase") || contains(" $phrase ")

    private fun String.normalizedProductText(): String {
        val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private class ProductHint(
        val canonicalName: String,
        val brand: String,
        val description: String,
        vararg val rawAliases: String,
    ) {
        val aliases = rawAliases.map { alias -> alias.normalizedProductText() }
    }
}
