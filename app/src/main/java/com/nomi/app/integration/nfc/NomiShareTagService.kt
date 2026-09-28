package com.nomi.app.integration.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.nomi.app.data.share.ShareApdu
import com.nomi.app.data.share.ShareTagResponder

/**
 * The phone that is sharing, seen by the phone that is listening.
 *
 * Android lets an app answer as a contactless card, and that is the only NFC role available for
 * pushing data from one phone to another: there is no file transfer API, and the old Android Beam
 * is gone. So the sending phone becomes the card and the receiving phone becomes the reader.
 *
 * The service is registered for Nomi's own AID and requires the device to be unlocked, so a locked
 * phone cannot hand a stranger its day by accident.
 */
class NomiShareTagService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val request = commandApdu
            ?: return ShareApdu.statusOnly(ShareApdu.STATUS_BAD_REQUEST)
        val payload = staged
            ?: return ShareApdu.statusOnly(ShareApdu.STATUS_NO_PAYLOAD)
        return ShareTagResponder(payload).respond(request)
    }

    override fun onDeactivated(reason: Int) {
        // Android takes the card role away when the phones part company, when the session times
        // out, or when another app wants it. Whatever the reason, the shared day has been read or
        // abandoned, and either way it should not sit in memory waiting for the next tap.
        clear()
    }

    internal companion object {
        /**
         * The staged day.
         *
         * Held in the service's companion because the card role is a separate component from the
         * screen that staged the file, and the tap happens while the sender is looking at a
         * progress screen. Process-wide rather than in the service instance because the framework
         * creates that instance itself and hands the app no reference to it.
         */
        @Volatile
        private var staged: ByteArray? = null

        fun stage(payload: ByteArray) {
            staged = payload
        }

        fun clear() {
            staged = null
        }

        /** What the card has ready, for the sending screen to describe. Null when nothing is. */
        fun hasStagedDay(): Boolean = staged != null
    }
}

/** Puts a shared day on the card, and takes it off again. */
object NomiShareTag {

    /**
     * Offers [payload] to whichever Nomi phone is touched next.
     *
     * Replaces anything already staged rather than adding to it, so a second share cannot end up
     * handing the first one's file to a reader that asked for the second.
     */
    fun offer(payload: ByteArray) {
        NomiShareTagService.stage(payload)
    }

    /** Takes the day off the card, which is what the sending screen does when it is done. */
    fun withdraw() {
        NomiShareTagService.clear()
    }

    fun isOffering(): Boolean = NomiShareTagService.hasStagedDay()
}
