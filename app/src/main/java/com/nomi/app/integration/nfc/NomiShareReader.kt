package com.nomi.app.integration.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.nomi.app.data.share.pullSharedDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A card that has just been touched.
 *
 * Deliberately opaque. The coordinator only passes it back to the reader that handed it over, and
 * keeping Android's `Tag` behind this means the share flow can be driven in a test without a
 * phone - there is no NFC on an emulator, so a flow that can only be reached through `Tag` is a
 * flow that is never exercised before it ships.
 */
interface ShareTagHandle

/**
 * The phone that is listening.
 *
 * While Nomi is on screen and the user has asked to receive, this phone reads whatever card it is
 * held against. Reading is the only mode that can pull, so the phone that receives is the one that
 * has to be open and in the foreground - which is worth saying plainly on screen, because holding
 * two phones together and getting nothing is the failure people hit first.
 */
interface ShareReader {
    /** False on a phone with no radio at all, which is a different answer from one that is off. */
    fun isAvailable(): Boolean

    fun canOffer(): Boolean = isAvailable()

    fun prepareOffering() {}

    fun isEnabled(): Boolean

    /** Starts listening, handing every card that is touched to [onTag]. */
    fun listen(onTag: (ShareTagHandle) -> Unit)

    fun stop()

    suspend fun read(tag: ShareTagHandle, onProgress: (Int, Int) -> Unit): Result<ByteArray>
}

/** The real thing, backed by a phone's NFC hardware. */
class NomiShareReader(private val activity: Activity) : ShareReader {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    override fun isAvailable(): Boolean = adapter != null

    private var offering = false
    private var discoveryChanged = false

    override fun canOffer(): Boolean = activity.packageManager.hasSystemFeature(
        PackageManager.FEATURE_NFC_HOST_CARD_EMULATION,
    )

    override fun prepareOffering() {
        stop()
        val nfc = adapter ?: return
        offering = true
        CardEmulation.getInstance(nfc).setPreferredService(
            activity, ComponentName(activity, NomiShareTagService::class.java),
        )
        if (Build.VERSION.SDK_INT >= 35) {
            nfc.setDiscoveryTechnology(
                activity, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP,
            )
            discoveryChanged = true
        } else {
            // Older Android cannot disable polling independently of HCE. Consume incidental
            // tag discoveries here so another tag app cannot take over the sending screen.
            val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            nfc.enableForegroundDispatch(activity, PendingIntent.getActivity(activity, 0, intent, flags), null, null)
        }
    }

    override fun isEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Reader mode rather than the system's tag dispatch, because it only listens while the screen
     * is in front of the user. Normal dispatch would offer to open Nomi on a tap meant for someone
     * else, and would keep listening after the user walked away.
     */
    override fun listen(onTag: (ShareTagHandle) -> Unit) {
        stop()
        val nfc = adapter ?: return
        nfc.enableReaderMode(
            activity,
            { tag -> onTag(NfcTagHandle(tag)) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                // Talk directly to HCE without probing for an NDEF tag first.
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                // Nomi is being held against another phone, so the sound and vibration of a tag
                // being found would be noise on top of its own feedback.
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
        )
    }

    override fun stop() {
        val nfc = adapter ?: return
        runCatching { nfc.disableReaderMode(activity) }
        if (offering) {
            runCatching { CardEmulation.getInstance(nfc).unsetPreferredService(activity) }
            if (Build.VERSION.SDK_INT >= 35 && discoveryChanged) {
                runCatching { nfc.resetDiscoveryTechnology(activity) }
            } else {
                runCatching { nfc.disableForegroundDispatch(activity) }
            }
        }
        offering = false
        discoveryChanged = false
    }

    /**
     * Reads a whole shared day off a card that was just touched.
     *
     * The blocking calls are moved to the IO dispatcher and the connection is always closed, so a
     * phone that walks out of range mid-file leaves nothing behind holding the antenna.
     */
    override suspend fun read(
        tag: ShareTagHandle,
        onProgress: (Int, Int) -> Unit,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get((tag as NfcTagHandle).tag)
            ?: return@withContext Result.failure(ShareReadException())
        try {
            isoDep.connect()
            isoDep.timeout = 5000
            pullSharedDay(
                transceive = { apdu -> isoDep.transceive(apdu) },
                onProgress = onProgress,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The phones were pulled apart, or the other phone's screen went off. Both arrive
            // here as a failed exchange, and neither is worth a different message.
            Result.failure(ShareReadException())
        } catch (e: IllegalStateException) {
            Result.failure(ShareReadException())
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private class NfcTagHandle(val tag: Tag) : ShareTagHandle

    /** Carries no detail on purpose: the user is holding two phones and needs one sentence. */
    class ShareReadException : Exception("The shared day could not be read")
}
