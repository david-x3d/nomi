package com.nomi.app.data.share

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One food as the shared day needs it, stripped of everything that only means something inside
 * Nomi's own database.
 *
 * Kept as its own type rather than a Room entity or a UI row so the builder below stays a pure
 * function: which foods end up in a shared file is decided by the user ticking boxes, and that
 * decision is worth testing without a database or a screen.
 */
data class ShareableFood(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val amount: Double,
    val unit: String,
    val meal: String,
    val kcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)

/** Builds the JSON Nomi pushes to the device the user picked. */
object NomiSharePayload {

    /** The type the receiving device is told about, so it opens the file as JSON. */
    const val MIME_TYPE: String = "application/json"

    private val json = Json {
        prettyPrint = true
        // The bytes cross a Bluetooth link and land in someone else's gallery or files app, so
        // a field that is missing must be a missing field rather than a silently dropped one.
        encodeDefaults = true
    }

    /** e.g. `nomi-share-2026-09-28.json`, which sorts and looks right in a file list. */
    fun fileName(day: String): String = "nomi-share-$day.json"

    /**
     * The shared file, or null when nothing is ticked.
     *
     * A file with no foods in it would be a file that says nothing, so an empty selection is
     * refused here rather than sent as an empty day. [selectedIds] is matched against the foods
     * passed in, which means a stale tick - a row deleted while the menu was open - quietly drops
     * out instead of sharing a food that is no longer there.
     */
    fun envelope(
        day: String,
        foods: List<ShareableFood>,
        selectedIds: Set<Long>,
        includeTotals: Boolean,
        sharedAtEpochMillis: Long,
        appVersionName: String,
    ): ShareEnvelopeV1? {
        val shared = foods.filter { it.id in selectedIds }
        if (shared.isEmpty()) return null
        return ShareEnvelopeV1(
            sharedAtEpochMillis = sharedAtEpochMillis,
            appVersionName = appVersionName,
            day = ShareDayV1(
                date = day,
                foods = shared.map { it.toV1() },
                totals = if (includeTotals) shared.totals() else null,
            ),
        )
    }

    fun encode(envelope: ShareEnvelopeV1): ByteArray =
        json.encodeToString(envelope).encodeToByteArray()

    /**
     * Reads a shared day back, or null when the bytes are not one.
     *
     * The tag check is not pedantry. The bytes arrive from a radio, so they could be anything at
     * all: a bank card that happens to answer, a file from another app, a corrupted transfer. A
     * file that claims to be a shared day is logged; anything else is refused rather than guessed
     * at, and a version this build does not understand is refused rather than half-read.
     */
    fun decode(bytes: ByteArray): ShareEnvelopeV1? = runCatching {
        val envelope = json.decodeFromString<ShareEnvelopeV1>(bytes.decodeToString())
        envelope.takeIf {
            it.format == ShareEnvelopeV1.FORMAT && it.schemaVersion == ShareEnvelopeV1.SCHEMA_VERSION
        }
    }.getOrNull()

    /**
     * Grams are rounded to one decimal and calories to a whole number.
     *
     * Nutrition arrives as scaled doubles, so a half of a slice of toast carries values like
     * 84.30000000000001. Rounding keeps the shared file equal to what the screen said, which
     * matters more here than the fractions of a gram a receiver could still interpolate.
     */
    private fun ShareableFood.toV1(): ShareFoodV1 = ShareFoodV1(
        name = name,
        brand = brand?.takeIf { it.isNotBlank() },
        amount = amount.whole(),
        unit = unit,
        meal = meal,
        kcal = kcal.whole(),
        proteinGrams = proteinGrams.gram(),
        carbohydrateGrams = carbohydrateGrams.gram(),
        fatGrams = fatGrams.gram(),
    )

    private fun List<ShareableFood>.totals(): ShareTotalsV1 = ShareTotalsV1(
        foodCount = size,
        kcal = sumOf(ShareableFood::kcal).whole(),
        proteinGrams = sumOf(ShareableFood::proteinGrams).gram(),
        carbohydrateGrams = sumOf(ShareableFood::carbohydrateGrams).gram(),
        fatGrams = sumOf(ShareableFood::fatGrams).gram(),
    )

    private fun Double.whole(): Double =
        if (isFinite()) kotlin.math.round(this).toDouble() else 0.0

    private fun Double.gram(): Double =
        if (isFinite()) kotlin.math.round(this * 10.0) / 10.0 else 0.0
}
