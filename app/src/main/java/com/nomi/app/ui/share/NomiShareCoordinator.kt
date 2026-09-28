package com.nomi.app.ui.share

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.nomi.app.BuildConfig
import com.nomi.app.data.share.NomiSharePayload
import com.nomi.app.data.share.ShareEnvelopeV1
import com.nomi.app.data.share.ShareReceiveException
import com.nomi.app.data.share.ShareReceiveFailure
import com.nomi.app.data.share.ShareableFood
import com.nomi.app.integration.nfc.NomiShareTag
import com.nomi.app.integration.nfc.ShareReader
import com.nomi.app.ui.today.TodayFoodEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Where a tap has got to, which is what decides what the screen says.
 *
 * Sharing over NFC is not a list of devices: there is nothing to discover, because a phone only
 * exists to the other one while the two are touching. So the flow is a single pair of states,
 * "waiting to be tapped" and "waiting to be tapped by", and the interface is about telling the
 * user which phone to do what to.
 */
enum class NomiShareStage {
    /** Only the Share and Receive entries are in the row menu. */
    Collapsed,

    /** The foods of the row that was held down, each one a box to tick. */
    PickingFoods,

    /** This phone is offering a day and waiting for the other phone to come and get it. */
    Sending,

    /** This phone is listening and waiting for the other phone to offer something. */
    Receiving,

    /** A day arrived and is being shown before it is written to the diary. */
    Received,
}

/** Something the user should be told once, as a message rather than as screen state. */
@Immutable
sealed interface NomiShareEvent {
    /** The other phone read the day that was offered. */
    data object Offered : NomiShareEvent

    /** A day arrived and was added to the diary. */
    data class Added(val foodCount: Int) : NomiShareEvent

    data object NoNfcHardware : NomiShareEvent

    data object NfcTurnedOff : NomiShareEvent

    data object CannotEmulate : NomiShareEvent

    data class Failed(val failure: ShareReceiveFailure) : NomiShareEvent

    data object NothingSelected : NomiShareEvent
}

/**
 * Drives sharing a day between two Nomi phones held together.
 *
 * One object for the whole interface, because the states are about one continuous action rather
 * than about a row: if the user starts receiving from one row's menu and the list scrolls, the
 * receiving screen has to stay where it is.
 *
 * The Android-specific halves are handed in rather than created here. The reader needs an
 * activity, and the diary is written by the ViewModel, and neither belongs in a class that is
 * otherwise a state machine.
 */
@Stable
class NomiShareCoordinator(
    private val reader: ShareReader?,
    private val scope: CoroutineScope,
    private val onAddToDiary: suspend (ShareEnvelopeV1) -> Unit,
    private val appVersionName: String = BuildConfig.VERSION_NAME,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    var stage by mutableStateOf(NomiShareStage.Collapsed)
        private set

    /** Bytes read so far and bytes expected, so the receiving screen can show a real progress. */
    var receivedBytes by mutableStateOf(0)
        private set

    var expectedBytes by mutableStateOf(0)
        private set

    /** The day that arrived, held until the user decides to keep it. */
    var received by mutableStateOf<ShareEnvelopeV1?>(null)
        private set

    /**
     * What this phone is currently offering, or null when it is not offering.
     *
     * Kept as a count and a total rather than the envelope because the sending screen only has to
     * say what is on its way, and holding the whole day on screen would be a second copy of
     * something already staged for the other phone to read.
     */
    var offered by mutableStateOf<OfferedDay?>(null)
        private set

    private var session = 0L
    private var reading = false
    private var offerTimeout: Job? = null

    private val _events = MutableSharedFlow<NomiShareEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NomiShareEvent> = _events.asSharedFlow()

    val isReceiving: Boolean get() = stage == NomiShareStage.Receiving
    val isSending: Boolean get() = stage == NomiShareStage.Sending

    private val receiveProgress: (Int, Int) -> Unit = { done, total ->
        receivedBytes = done
        expectedBytes = total
    }

    /** Called when the row menu's Share entry is tapped. */
    fun closeMenu() {
        if (stage == NomiShareStage.PickingFoods) collapse()
    }

    fun open() {
        stage = NomiShareStage.PickingFoods
    }

    /**
     * Stops whatever was happening and puts the menu back to its first entry.
     *
     * This is what a dismissed screen calls, and it has to take the phone out of both roles: a
     * reader that was left listening would keep pulling a day off the next card that came near,
     * and a staged day would be offered to whoever touched the phone next.
     */
    fun collapse() {
        session++
        reading = false
        offerTimeout?.cancel()
        offerTimeout = null
        stopListening()
        NomiShareTag.withdraw()
        received = null
        offered = null
        receivedBytes = 0
        expectedBytes = 0
        stage = NomiShareStage.Collapsed
    }

    /**
     * Stages the ticked foods of one row and offers them to the other phone.
     *
     * The bytes are built here rather than by the caller so that what is offered is the foods that
     * were ticked, at the moment they were ticked, with that day's numbers in them.
     */
    fun offer(
        day: LocalDate,
        foods: List<TodayFoodEntry>,
        selectedIds: Set<Long>,
        includeTotals: Boolean,
    ) {
        val envelope = NomiSharePayload.envelope(
            day = day.toString(),
            foods = foods.map(TodayFoodEntry::toShareableFood),
            selectedIds = selectedIds,
            includeTotals = includeTotals,
            sharedAtEpochMillis = clock.millis(),
            appVersionName = appVersionName,
        )
        if (envelope == null) {
            emit(NomiShareEvent.NothingSelected)
            return
        }
        if (!checkRadio()) return
        if (reader?.canOffer() != true) {
            emit(NomiShareEvent.CannotEmulate)
            return
        }
        collapse()
        try {
            reader.prepareOffering()
        } catch (_: RuntimeException) {
            collapse()
            emit(NomiShareEvent.Failed(ShareReceiveFailure.TransferFailed))
            return
        }
        NomiShareTag.offer(NomiSharePayload.encode(envelope))
        offered = OfferedDay(
            foodCount = envelope.day.foods.size,
            kcal = envelope.day.totals?.kcal ?: envelope.day.foods.sumOf { it.kcal },
        )
        stage = NomiShareStage.Sending
        offerTimeout = scope.launch {
            delay(120_000)
            collapse()
        }
    }

    /**
     * Starts listening for the other phone.
     *
     * The checks are here rather than at the entry point so that a phone with the radio switched
     * off gets told to switch it on, rather than being shown a screen that will never hear
     * anything. A phone with no radio at all is a different answer from a phone that is merely
     * off, and the user has to be able to tell which one they are holding.
     */
    private fun checkRadio(): Boolean {
        if (reader == null || !reader.isAvailable()) {
            emit(NomiShareEvent.NoNfcHardware)
            return false
        }
        if (!reader.isEnabled()) {
            emit(NomiShareEvent.NfcTurnedOff)
            return false
        }
        return true
    }

    fun startReceiving() {
        if (!checkRadio()) return
        collapse()
        stage = NomiShareStage.Receiving
        val activeSession = session
        try {
            reader!!.listen { tag ->
                scope.launch {
                    if (activeSession != session || !isReceiving || reading) return@launch
                    reading = true
                    try {
                        val result = reader.read(tag) { done, total ->
                            if (activeSession == session) receiveProgress(done, total)
                        }
                        if (activeSession != session) return@launch
                        val payload = result.getOrNull()
                        val envelope = payload?.let(NomiSharePayload::decode)
                        if (envelope != null) {
                            received = envelope
                            stage = NomiShareStage.Received
                            // Keep reader mode until the preview is dismissed. Disabling it with
                            // the phones still touching gives the same tag back to Android.
                        } else {
                            receivedBytes = 0
                            expectedBytes = 0
                            emit(NomiShareEvent.Failed(
                                (result.exceptionOrNull() as? ShareReceiveException)?.failure
                                    ?: if (payload != null) ShareReceiveFailure.NotAShare
                                    else ShareReceiveFailure.TransferFailed,
                            ))
                            // Remain in the explicit receive session so separating and tapping
                            // again works without returning to Android's generic tag discovery.
                        }
                    } finally {
                        if (activeSession == session) reading = false
                    }
                }
            }
        } catch (_: RuntimeException) {
            collapse()
            emit(NomiShareEvent.Failed(ShareReceiveFailure.TransferFailed))
        }
    }

    /**
     * Writes the day that arrived into this phone's diary.
     *
     * Nothing is written until this is called. A day that has crossed a radio is not yet a fact
     * about this phone's user, and showing it first is what makes adding it a decision rather
     * than something that happened to them.
     */
    fun addReceivedToDiary() {
        val envelope = received ?: return
        scope.launch {
            onAddToDiary(envelope)
            emit(NomiShareEvent.Added(envelope.day.foods.size))
            collapse()
        }
    }

    /** Drops the day that arrived, which is the same as never having read it. */
    fun discardReceived() {
        collapse()
    }

    private fun stopListening() {
        reader?.stop()
    }

    private fun emit(event: NomiShareEvent) {
        _events.tryEmit(event)
    }
}

/** How much is on its way, which is all the sending screen needs to say about it. */
@Immutable
data class OfferedDay(val foodCount: Int, val kcal: Double)

/**
 * A grouped meal is one row on the page but several foods, and those are the things a user can
 * sensibly tick: sharing the rice out of a plate but not the fish next to it.
 */
fun TodayFoodEntry.shareableFoods(): List<TodayFoodEntry> =
    if (groupItems.isEmpty()) listOf(this) else groupItems

internal fun TodayFoodEntry.toShareableFood(): ShareableFood = ShareableFood(
    id = id,
    name = name,
    brand = brand,
    amount = amount,
    unit = unit,
    meal = mealCategory.name.lowercase(),
    kcal = calories,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
)

/**
 * The coordinator, provided once around the main interface.
 *
 * A composition local rather than a parameter through the row, its swipe wrapper and the screen
 * above it: this is a single object about one continuous action rather than about a row, and
 * threading it through three signatures that are all already a page long would add noise to each.
 */
val LocalNomiShareCoordinator = staticCompositionLocalOf<NomiShareCoordinator> {
    error("No NomiShareCoordinator was provided")
}
