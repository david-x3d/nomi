package com.nomi.app.ui.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuantityDisplayFormatterTest {
    private val english = Locale.US
    private val german = Locale.GERMANY

    @Test
    fun `half a 200 gram bag is shown as semantic fraction and canonical grams`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(
                quantity = 0.5,
                unit = "bag",
                canonicalQuantity = 100.0,
                canonicalUnit = "g",
                packageQuantity = 200.0,
                packageUnit = "bag",
                fractionNumerator = 1,
                fractionDenominator = 2,
            ),
            english,
        )

        assertEquals("100 g", display.primary)
        assertEquals("½ bag", display.context)
        assertEquals("½ bag · 100 g", display.withContext)
        assertFalse(display.withContext.contains("0.5 bag"))
    }

    @Test
    fun `two thirds uses a fraction glyph and approximate rounded grams`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(
                quantity = 2.0 / 3.0,
                unit = "bag",
                canonicalQuantity = 200.0 * 2.0 / 3.0,
                canonicalUnit = "g",
                packageUnit = "bag",
                fractionNumerator = 2,
                fractionDenominator = 3,
                isApproximate = true,
            ),
            english,
        )

        assertEquals("≈133 g", display.primary)
        assertEquals("⅔ bag · ≈133 g", display.withContext)
        assertFalse(display.withContext.contains("0.7 bag"))
    }

    @Test
    fun `55 percent of an explicitly sized package keeps exact 176 grams`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(
                quantity = 0.55,
                unit = "package",
                canonicalQuantity = 176.0,
                canonicalUnit = "g",
                semantic = QuantityDisplaySemantic.PACKAGE_PERCENT,
                packageQuantity = 320.0,
                packageUnit = "g",
                percentage = 55.0,
            ),
            english,
        )

        assertEquals("176 g", display.primary)
        assertEquals("55% of package", display.context)
        assertFalse(display.withContext.contains("0.55 package"))
    }

    @Test
    fun `German can default displays canonical milliliters with localized context`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(
                quantity = 1.0,
                unit = "Dose",
                canonicalQuantity = 250.0,
                canonicalUnit = "ml",
                semantic = QuantityDisplaySemantic.LOCAL_CAN_DEFAULT,
                packageQuantity = 250.0,
                packageUnit = "ml",
                sourcePackageQuantity = 355.0,
                sourcePackageUnit = "ml",
            ),
            german,
        )

        assertEquals("250 ml", display.primary)
        assertEquals("1 Dose · 250 ml", display.withContext)
    }

    @Test
    fun `direct German decimal uses locale separator`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 250.5, unit = "g"),
            german,
        )

        assertEquals("250,5 g", display.primary)
        assertEquals(null, display.context)
    }

    @Test
    fun `source package conflict explains that explicit package was kept`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(
                quantity = 176.0,
                unit = "g",
                packageQuantity = 320.0,
                packageUnit = "g",
                sourcePackageQuantity = 380.0,
                sourcePackageUnit = "g",
                sourcePackageConflict = true,
            ),
            german,
        )

        assertEquals(
            "Die Quelle listet derzeit eine Packungsgröße von 380 g. Deine Eingabe von 320 g wurde beibehalten.",
            display.sourceConflictNote,
        )
    }

    @Test
    fun `fallback package decimal becomes percentage rather than decimal package`() {
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 0.55, unit = "package"),
            english,
        )

        assertEquals("55% of package", display.primary)
    }

    @Test
    fun `mass units remain humane instead of expanding kilograms`() {
        val milligrams = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 500.0, unit = "mg"),
            english,
        )
        val kilograms = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 1.0, unit = "kg"),
            german,
        )

        assertEquals("500 mg", milligrams.primary)
        assertEquals("1 kg", kilograms.primary)
    }

    @Test
    fun `spoon aliases use short locale appropriate labels`() {
        val englishSpoon = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 1.0, unit = "tablespoon"),
            english,
        )
        val germanSpoon = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(quantity = 2.0, unit = "Teel\u00f6ffel"),
            german,
        )

        assertEquals("1 tbsp", englishSpoon.primary)
        assertEquals("2 TL", germanSpoon.primary)
    }
}
