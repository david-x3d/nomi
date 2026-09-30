package com.nomi.app.data.share

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What actually lands on the other phone.
 *
 * The user decides what leaves by ticking boxes, so the rules about which foods are in the file,
 * what the totals add up to and how the numbers are written are the feature. They are pinned here
 * because every one of them is a privacy decision rather than a formatting one.
 */
class NomiSharePayloadTest {

    private val toast = food(id = 1, name = "Toast", kcal = 180.0, protein = 3.5)
    private val banana = food(id = 2, name = "Banane", kcal = 105.0, protein = 1.3)

    @Test
    fun `only the ticked foods are shared`() {
        val envelope = envelope(selectedIds = setOf(1L))

        val names = envelope!!.day.foods.map(ShareFoodV1::name)
        assertEquals(listOf("Toast"), names)
    }

    @Test
    fun `ticking nothing shares nothing rather than an empty day`() {
        // A file that says a day had no food in it is worse than no file: it reads as a fact
        // about the day instead of as a transfer that was never set up.
        assertNull(envelope(selectedIds = emptySet()))
    }

    @Test
    fun `a tick for a food that is no longer there is dropped`() {
        // The row can be deleted while the menu is open, and sending it anyway would share
        // something the user has already removed.
        val envelope = envelope(selectedIds = setOf(1L, 99L))

        assertEquals(listOf("Toast"), envelope!!.day.foods.map(ShareFoodV1::name))
    }

    @Test
    fun `the order follows the foods rather than the ticking`() {
        val envelope = envelope(selectedIds = setOf(2L, 1L))

        assertEquals(listOf("Toast", "Banane"), envelope!!.day.foods.map(ShareFoodV1::name))
    }

    @Test
    fun `the totals describe the ticked foods and not the whole day`() {
        val envelope = envelope(selectedIds = setOf(2L), includeTotals = true)

        val totals = envelope!!.day.totals
        assertNotNull(totals)
        assertEquals(1, totals!!.foodCount)
        assertEquals(105.0, totals.kcal, 0.0)
        assertEquals(1.3, totals.proteinGrams, 0.0)
    }

    @Test
    fun `unticking the totals leaves the foods untouched`() {
        val envelope = envelope(selectedIds = setOf(1L, 2L), includeTotals = false)

        assertEquals(2, envelope!!.day.foods.size)
        assertNull(envelope.day.totals)
    }

    @Test
    fun `totals add up the ticked foods together`() {
        val envelope = envelope(selectedIds = setOf(1L, 2L), includeTotals = true)

        val totals = envelope!!.day.totals!!
        assertEquals(2, totals.foodCount)
        assertEquals(285.0, totals.kcal, 0.0)
        assertEquals(4.8, totals.proteinGrams, 0.1)
    }

    @Test
    fun `numbers are rounded to what the screen said`() {
        // Portions are scaled doubles, so an unshared value arrives as 84.30000000000001 and the
        // receiving phone shows a number the user never saw on this one.
        val scaled = food(id = 1, name = "Toast", kcal = 84.30000000000001, protein = 3.14159)
        val envelope = NomiSharePayload.envelope(
            day = DAY,
            foods = listOf(scaled),
            selectedIds = setOf(1L),
            includeTotals = true,
            sharedAtEpochMillis = 1L,
            appVersionName = "2.5.2",
        )!!

        assertEquals(84.0, envelope.day.foods.single().kcal, 0.0)
        assertEquals(3.1, envelope.day.foods.single().proteinGrams, 0.0)
    }

    @Test
    fun `a value that is not a number does not reach the file`() {
        // JSON has no way to write a double that is not a number, and a file that cannot be
        // parsed is a file that was never shared.
        val broken = food(id = 1, name = "Toast", kcal = Double.NaN, protein = Double.POSITIVE_INFINITY)
        val envelope = NomiSharePayload.envelope(
            day = DAY,
            foods = listOf(broken),
            selectedIds = setOf(1L),
            includeTotals = true,
            sharedAtEpochMillis = 1L,
            appVersionName = "2.5.2",
        )!!

        assertEquals(0.0, envelope.day.foods.single().kcal, 0.0)
        assertEquals(0.0, envelope.day.totals!!.kcal, 0.0)
    }

    @Test
    fun `a blank brand is left out rather than written as an empty string`() {
        val envelope = NomiSharePayload.envelope(
            day = DAY,
            foods = listOf(food(id = 1, name = "Toast", brand = "   ")),
            selectedIds = setOf(1L),
            includeTotals = false,
            sharedAtEpochMillis = 1L,
            appVersionName = "2.5.2",
        )!!

        assertNull(envelope.day.foods.single().brand)
    }

    @Test
    fun `the envelope says what it is and when it was made`() {
        val envelope = envelope(selectedIds = setOf(1L))!!

        assertEquals("nomi-share", envelope.format)
        assertEquals(1, envelope.schemaVersion)
        assertEquals(1L, envelope.sharedAtEpochMillis)
        assertEquals("2.5.2", envelope.appVersionName)
        assertEquals(DAY, envelope.day.date)
    }

    @Test
    fun `the file is JSON a receiver can read back`() {
        val bytes = NomiSharePayload.encode(envelope(selectedIds = setOf(1L, 2L), includeTotals = true)!!)
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject

        assertEquals("nomi-share", root["format"]?.jsonPrimitive?.content)
        val foods = root["day"]!!.jsonObject["foods"]!!.jsonArray
        assertEquals(2, foods.size)
        assertTrue(foods[0].jsonObject.containsKey("kcal"))
        assertTrue(root["day"]!!.jsonObject["totals"]!!.jsonObject.containsKey("foodCount"))
    }

    @Test
    fun `the file is named after the day it holds`() {
        // The name is the only thing the receiving phone has to go on before the file is opened.
        assertEquals("nomi-share-2026-09-28.json", NomiSharePayload.fileName(DAY))
        assertEquals("application/json", NomiSharePayload.MIME_TYPE)
    }

    @Test
    fun `a share is not a backup and does not claim to be one`() {
        // Nomi's backup is restorable and this is not. Keeping the tags apart is what stops a
        // receiving phone from trying to import a shared day as a whole diary.
        val root = Json.parseToJsonElement(
            NomiSharePayload.encode(envelope(selectedIds = setOf(1L))!!).decodeToString(),
        ).jsonObject
        val text = root.keys.joinToString(" ")

        assertFalse(text.contains("backup"))
        assertFalse(root.containsKey("preferences"))
        assertFalse(root.containsKey("weightLogs"))
    }

    @Test
    fun `a fractional amount survives instead of rounding to zero`() {
        // Half a pizza used to leave as 0 pieces, which the receiver refuses as an entry, so the
        // whole shared day failed to import.
        val amounts = listOf(0.5, 1.5, 0.25, 1.234).mapIndexed { index, amount ->
            food(id = index + 10L, name = "Food $index").copy(amount = amount, unit = "piece")
        }
        val shared = NomiSharePayload.envelope(
            day = DAY,
            foods = amounts,
            selectedIds = amounts.map(ShareableFood::id).toSet(),
            includeTotals = false,
            sharedAtEpochMillis = 1L,
            appVersionName = "2.8.0",
        )!!

        assertEquals(listOf(0.5, 1.5, 0.25, 1.23), shared.day.foods.map(ShareFoodV1::amount))
    }

    @Test
    fun `a tiny amount is never sent as zero`() {
        val tiny = food(id = 5, name = "Salt").copy(amount = 0.001)
        val shared = NomiSharePayload.envelope(DAY, listOf(tiny), setOf(5L), false, 1L, "2.8.0")!!

        assertTrue(shared.day.foods.single().amount > 0.0)
    }

    private fun envelope(
        selectedIds: Set<Long>,
        includeTotals: Boolean = false,
    ): ShareEnvelopeV1? = NomiSharePayload.envelope(
        day = DAY,
        foods = listOf(toast, banana),
        selectedIds = selectedIds,
        includeTotals = includeTotals,
        sharedAtEpochMillis = 1L,
        appVersionName = "2.5.2",
    )

    private fun food(
        id: Long,
        name: String,
        brand: String? = null,
        kcal: Double = 100.0,
        protein: Double = 1.0,
    ) = ShareableFood(
        id = id,
        name = name,
        brand = brand,
        amount = 50.0,
        unit = "g",
        meal = "breakfast",
        kcal = kcal,
        proteinGrams = protein,
        carbohydrateGrams = 10.0,
        fatGrams = 2.0,
    )

    private companion object {
        const val DAY = "2026-09-28"
    }
}
