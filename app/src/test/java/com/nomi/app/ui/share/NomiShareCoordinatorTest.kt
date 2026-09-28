package com.nomi.app.ui.share

import com.nomi.app.data.share.NomiShareImporter
import com.nomi.app.data.share.NomiSharePayload
import com.nomi.app.data.share.ShareEnvelopeV1
import com.nomi.app.data.share.ShareReceiveException
import com.nomi.app.data.share.ShareReceiveFailure
import com.nomi.app.data.share.ShareableFood
import com.nomi.app.integration.nfc.ShareReader
import com.nomi.app.integration.nfc.ShareTagHandle
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * What each phone does while a tap is going on.
 *
 * The radio itself cannot be tested here - there is no NFC on an emulator - but the bytes on it
 * are covered by ShareApduTest, and what remains is everything around it: which phone takes which
 * role, what a phone with no radio says, and above all that a reader left running cannot go on
 * pulling days off whatever card wanders past.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NomiShareCoordinatorTest {

    @Test
    fun `closing the food menu keeps a newly started offer alive`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        coordinator.open()
        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), setOf(1L), true)
        coordinator.closeMenu()
        assertEquals(NomiShareStage.Sending, coordinator.stage)
        assertNotNull(coordinator.offered)
        assertTrue(reader.offering)
        assertTrue(com.nomi.app.integration.nfc.NomiShareTag.isOffering())
        coordinator.collapse()
        assertFalse(reader.offering)
        assertFalse(com.nomi.app.integration.nfc.NomiShareTag.isOffering())
    }

    @Test
    fun `closing the menu only cancels food selection`() = runTest {
        val coordinator = coordinator(FakeReader())
        coordinator.open()
        coordinator.closeMenu()
        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        coordinator.startReceiving()
        coordinator.closeMenu()
        assertEquals(NomiShareStage.Receiving, coordinator.stage)
    }

    @Test
    fun `failed contact can be retried without leaving reader mode`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        coordinator.startReceiving()
        reader.failWith(ShareReceiveFailure.NoTagFound)
        assertTrue(reader.listening)
        reader.deliver(payloadOf(breakfast()))
        assertEquals(NomiShareStage.Received, coordinator.stage)
        assertTrue(reader.listening)
        coordinator.closeMenu()
        assertEquals(NomiShareStage.Received, coordinator.stage)
        coordinator.collapse()
        assertFalse(reader.listening)
    }

    @Test
    fun `offer expires and releases its radio role`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), setOf(1L), true)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        assertFalse(reader.offering)
    }

    @Test
    fun `sending checks whether NFC is enabled`() = runTest {
        val reader = FakeReader(enabled = false)
        val coordinator = coordinator(reader)
        val events = collectEvents(coordinator)
        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), setOf(1L), true)
        assertFalse(reader.offering)
        assertEquals(listOf(NomiShareEvent.NfcTurnedOff), events())
    }

    @Test
    fun `late and duplicate tag callbacks cannot replace a cancelled session`() = runTest {
        val reader = FakeReader()
        val gate = CompletableDeferred<Unit>()
        reader.readGate = gate
        val coordinator = coordinator(reader)
        coordinator.startReceiving()
        reader.deliver(payloadOf(breakfast()))
        reader.deliver(payloadOf(breakfast()))
        assertEquals(1, reader.reads)
        coordinator.collapse()
        coordinator.startReceiving()
        gate.complete(Unit)
        assertNull(coordinator.received)
        assertEquals(NomiShareStage.Receiving, coordinator.stage)
        reader.deliver(payloadOf(breakfast()))
        assertEquals(NomiShareStage.Received, coordinator.stage)
    }


    @Test
    fun `opening the menu asks the phone for nothing`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)

        coordinator.open()

        assertEquals(NomiShareStage.PickingFoods, coordinator.stage)
        // Holding down a food row must not switch the NFC radio into either role.
        assertFalse(reader.listening)
    }

    @Test
    fun `ticking nothing offers nothing`() = runTest {
        val coordinator = coordinator(FakeReader())
        val events = collectEvents(coordinator)

        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), emptySet(), includeTotals = true)

        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        assertNull(coordinator.offered)
        assertEquals(listOf(NomiShareEvent.NothingSelected), events())
    }

    @Test
    fun `offering stages exactly the ticked foods of a grouped meal`() = runTest {
        val coordinator = coordinator(FakeReader())
        val breakfast = breakfast()

        // The menu hands over the row's own foods rather than the row, because a grouped meal is
        // one line on the page but several things the user can tick.
        coordinator.offer(
            day = DAY,
            foods = breakfast.shareableFoods(),
            selectedIds = setOf(2L),
            includeTotals = true,
        )

        // The whole point of the boxes: the rice goes and the fish does not.
        assertEquals(NomiShareStage.Sending, coordinator.stage)
        assertEquals(OfferedDay(foodCount = 1, kcal = 105.0), coordinator.offered)
    }

    @Test
    fun `a day is replaced rather than added to when a second one is offered`() = runTest {
        val coordinator = coordinator(FakeReader())

        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), setOf(1L), includeTotals = true)
        coordinator.offer(DAY, listOf(food(2, "Banane", 105.0)), setOf(2L), includeTotals = true)

        // Otherwise a reader that asked for the second would be handed the first.
        assertEquals(OfferedDay(foodCount = 1, kcal = 105.0), coordinator.offered)
    }

    @Test
    fun `a phone with no radio says so instead of listening for nothing`() = runTest {
        val coordinator = coordinator(reader = null)
        val events = collectEvents(coordinator)

        coordinator.startReceiving()

        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        assertEquals(listOf(NomiShareEvent.NoNfcHardware), events())
    }

    @Test
    fun `a phone with the radio off says so instead of listening for nothing`() = runTest {
        val reader = FakeReader(enabled = false)
        val coordinator = coordinator(reader)
        val events = collectEvents(coordinator)

        coordinator.startReceiving()

        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        assertFalse(reader.listening)
        assertEquals(listOf(NomiShareEvent.NfcTurnedOff), events())
    }

    @Test
    fun `a phone that can listen, listens`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)

        coordinator.startReceiving()

        assertEquals(NomiShareStage.Receiving, coordinator.stage)
        assertTrue(reader.listening)
    }

    @Test
    fun `closing the screen takes the phone out of the listening role`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        coordinator.startReceiving()

        coordinator.collapse()

        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        // A reader left listening would go on pulling a day off the next card that came near.
        assertFalse(reader.listening)
    }

    @Test
    fun `closing the screen drops the day that was on offer`() = runTest {
        val coordinator = coordinator(FakeReader())
        coordinator.offer(DAY, listOf(food(1, "Toast", 196.0)), setOf(1L), includeTotals = true)

        coordinator.collapse()

        // A day left staged would be offered to whoever touched this phone next.
        assertNull(coordinator.offered)
    }

    @Test
    fun `an abandoned tap is not half a day in the diary`() = runTest {
        val reader = FakeReader()
        var added = 0
        val coordinator = coordinator(reader) { added++ }
        val events = collectEvents(coordinator)

        coordinator.startReceiving()
        reader.deliver(payloadOf(breakfast()))
        advanceUntilIdle()
        assertNotNull(coordinator.received)

        coordinator.discardReceived()
        advanceUntilIdle()

        // Nothing is written until the user says so. A file that has crossed a radio is not yet a
        // fact about this phone's user.
        assertEquals(0, added)
        assertNull(coordinator.received)
        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
        assertTrue(events().isEmpty())
    }

    @Test
    fun `a day that arrived is written only when the user keeps it`() = runTest {
        val reader = FakeReader()
        var added: ShareEnvelopeV1? = null
        val coordinator = coordinator(reader) { added = it }
        val events = collectEvents(coordinator)

        coordinator.startReceiving()
        reader.deliver(payloadOf(breakfast()))
        advanceUntilIdle()

        assertEquals(NomiShareStage.Received, coordinator.stage)
        assertEquals(2, coordinator.received?.day?.foods?.size)
        assertTrue("nothing is written until the user keeps it", events().isEmpty())

        coordinator.addReceivedToDiary()
        advanceUntilIdle()

        assertEquals(2, added?.day?.foods?.size)
        assertEquals(NomiShareStage.Collapsed, coordinator.stage)
    }

    @Test
    fun `a stopped transfer is reported and nothing is offered as arrived`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        val events = collectEvents(coordinator)

        coordinator.startReceiving()
        reader.failWith(ShareReceiveFailure.Corrupted)
        advanceUntilIdle()

        assertNull(coordinator.received)
        assertEquals(NomiShareStage.Receiving, coordinator.stage)
        assertTrue(reader.listening)
        assertEquals(listOf(NomiShareEvent.Failed(ShareReceiveFailure.Corrupted)), events())
    }

    @Test
    fun `bytes that are not a shared day are refused rather than logged`() = runTest {
        val reader = FakeReader()
        val coordinator = coordinator(reader)
        val events = collectEvents(coordinator)

        coordinator.startReceiving()
        // A bank card, or a file from another app, that happens to answer on the same radio.
        reader.deliver("""{"format":"something-else","hello":"world"}""".toByteArray())
        advanceUntilIdle()

        assertNull(coordinator.received)
        assertEquals(listOf(NomiShareEvent.Failed(ShareReceiveFailure.NotAShare)), events())
    }

    @Test
    fun `a day that arrives is logged on the day it was eaten`() {
        val envelope = envelope(
            date = "2026-09-21",
            foods = listOf(shareableFood(1, "Toast mit Butter", 196.0), shareableFood(2, "Banane", 105.0)),
        )

        val logs = NomiShareImporter.logsFor(
            envelope = envelope,
            date = LocalDate.parse("2026-09-21"),
            zone = ZoneOffset.UTC,
            now = 1_000L,
        )

        // Somebody's Monday stays their Monday. Dating it today would quietly rewrite their day
        // into this phone's today, which is the one thing the user is not asking for.
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.localDate == "2026-09-21" })
        assertTrue(logs.all { it.inputMethod == "shared" })
        assertTrue(logs.all { it.sourceSnapshot.kind == "shared" })
        // Each food is its own row, and in the order they were shared, so the receiving phone
        // can show and delete them one at a time.
        assertTrue(logs[0].loggedAtEpochMillis < logs[1].loggedAtEpochMillis)
    }

    @Test
    fun `an arriving food keeps its numbers and invents no weights`() {
        val envelope = envelope(
            date = "2026-09-21",
            foods = listOf(shareableFood(1, "Toast mit Butter", 196.0)),
            includeTotals = false,
        )

        val log = NomiShareImporter.logsFor(
            envelope = envelope,
            date = LocalDate.parse("2026-09-21"),
            zone = ZoneOffset.UTC,
            now = 1_000L,
        ).single()

        assertEquals("Toast mit Butter", log.displayNameSnapshot)
        assertEquals(196.0, log.nutritionSnapshot.caloriesKcal, 0.0)
        // Grams are not part of a shared day, and inventing them from a unit would put a number
        // in the diary that nobody weighed.
        assertNull(log.grams)
    }

    @Test
    fun `an unknown meal is filed rather than dropped`() {
        val envelope = NomiSharePayload.envelope(
            day = "2026-09-21",
            foods = listOf(shareableFood(1, "Suppe", 120.0).copy(meal = "supper")),
            selectedIds = setOf(1L),
            includeTotals = false,
            sharedAtEpochMillis = 1L,
            appVersionName = "2.5.2",
        )!!

        val log = NomiShareImporter.logsFor(
            envelope = envelope,
            date = LocalDate.parse("2026-09-21"),
            zone = ZoneOffset.UTC,
            now = 1_000L,
        ).single()

        // A day whose dinner was filed as a snack is a small inaccuracy. A day that is missing
        // the food entirely is a lie about somebody else's eating.
        assertEquals("SNACKS", log.mealCategory)
    }

    private fun TestScope.coordinator(
        reader: ShareReader?,
        onAdd: suspend (ShareEnvelopeV1) -> Unit = {},
    ) = NomiShareCoordinator(
        reader = reader,
        scope = sharingScope(),
        onAddToDiary = onAdd,
        appVersionName = "2.5.2",
        clock = CLOCK,
    )

    /** Unconfined, so a tap's work happens before the test looks at the result. */
    private fun TestScope.sharingScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.collectEvents(
        coordinator: NomiShareCoordinator,
    ): () -> List<NomiShareEvent> {
        val seen = mutableListOf<NomiShareEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.events.collect { seen += it }
        }
        return { seen.toList() }
    }

    private fun envelope(
        date: String,
        foods: List<ShareableFood>,
        includeTotals: Boolean = true,
    ) = NomiSharePayload.envelope(
        day = date,
        foods = foods,
        selectedIds = foods.map { it.id }.toSet(),
        includeTotals = includeTotals,
        sharedAtEpochMillis = 1L,
        appVersionName = "2.5.2",
    )!!

    private fun payloadOf(entry: TodayFoodEntry): ByteArray = NomiSharePayload.encode(
        envelope(
            date = DAY.toString(),
            foods = entry.shareableFoods().map { it.toShareableFood() },
        ),
    )

    private fun food(id: Long, name: String, kcal: Double) = TodayFoodEntry(
        id = id,
        name = name,
        amountText = "1 Stück",
        calories = kcal,
        proteinGrams = 3.0,
        carbohydrateGrams = 30.0,
        fatGrams = 1.0,
        mealCategory = MealCategory.BREAKFAST,
        time = LocalTime.of(8, 15),
        amount = 1.0,
        unit = "Stück",
    )

    /** One row on the page that is really two foods, which is what the boxes are for. */
    private fun breakfast() = food(1, "Toast mit Butter", 196.0).copy(
        name = "Toast mit Butter with Banane",
        calories = 301.0,
        groupItems = listOf(
            food(1, "Toast mit Butter", 196.0),
            food(2, "Banane", 105.0),
        ),
    )

    private fun shareableFood(id: Long, name: String, kcal: Double) = ShareableFood(
        id = id,
        name = name,
        amount = 1.0,
        unit = "Stück",
        meal = "breakfast",
        kcal = kcal,
        proteinGrams = 3.0,
        carbohydrateGrams = 30.0,
        fatGrams = 1.0,
    )

    /**
     * A reader with no radio behind it.
     *
     * There is no NFC on an emulator, so the real reader can never be the thing under test. This
     * one records the role the coordinator puts the phone in and lets a test hand it a card.
     */
    private class FakeReader(private val enabled: Boolean = true) : ShareReader {
        var listening = false
        var offering = false
        var reads = 0
        var readGate: CompletableDeferred<Unit>? = null

        override fun prepareOffering() { offering = true }
        private var onTag: ((ShareTagHandle) -> Unit)? = null

        override fun isAvailable(): Boolean = true

        override fun isEnabled(): Boolean = enabled

        override fun listen(onTag: (ShareTagHandle) -> Unit) {
            listening = true
            this.onTag = onTag
        }

        override fun stop() {
            offering = false
            listening = false
            onTag = null
        }

        override suspend fun read(
            tag: ShareTagHandle,
            onProgress: (Int, Int) -> Unit,
        ): Result<ByteArray> {
            reads++
            readGate?.await()
            return (tag as FakeCard).result.map { payload ->
                onProgress(payload.size, payload.size)
                payload
            }
        }

        /** A card was touched and it turned out to be a Nomi offering this many bytes. */
        fun deliver(payload: ByteArray) {
            onTag?.invoke(FakeCard(Result.success(payload)))
        }

        fun failWith(failure: ShareReceiveFailure) {
            onTag?.invoke(FakeCard(Result.failure(ShareReceiveException(failure))))
        }
    }

    /** Stands in for a real contactless card, carrying what reading it would have produced. */
    private class FakeCard(val result: Result<ByteArray>) : ShareTagHandle

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 9, 28)
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-28T10:00:00Z"), ZoneOffset.UTC)
    }
}
