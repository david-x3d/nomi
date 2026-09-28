package com.nomi.app.data.share

import java.util.zip.CRC32

/**
 * The bytes that cross between two Nomi phones held back to back.
 *
 * Android has no way to send a file from one phone to another. Bluetooth's push profile would
 * need a pairing, and NFC has no file API at all. What NFC does offer is two public pieces: a
 * phone can be made to answer as a card (`HostApduService`), and a phone in the foreground can
 * talk to whatever card it touches (`enableReaderMode`). This is the conversation between those
 * two, written as plain APDUs rather than as NDEF, because NDEF's tag filesystem is a large
 * specification and a shared day is a few kilobytes of JSON that only has to survive a few
 * centimetres of air.
 *
 * Both ends are here and neither is Android, which is deliberate. The reader and the tag are the
 * two halves most likely to disagree about a byte, and a disagreement like that only shows up as
 * a phone that never receives anything. Keeping them as functions of arrays means the two halves
 * can be run against each other in a test and made to agree there instead of over a radio.
 */
object ShareApdu {

    /** "NOMI", which is how a reader recognises a Nomi phone rather than a bank card. */
    val AID: ByteArray = byteArrayOf(0x4E, 0x4F, 0x4D, 0x49)

    /** Proprietary class byte: this is a Nomi conversation and nothing else on the card. */
    val CLA: Byte = 0x80.toByte()

    /** Reads the payload's length and checksum. Nothing else. */
    val INS_METADATA: Byte = 0xA1.toByte()

    /** Reads one piece of the payload. The offset is four bytes, so size is not the limit. */
    val INS_CHUNK: Byte = 0xA0.toByte()

    const val STATUS_OK: Int = 0x9000
    const val STATUS_NO_PAYLOAD: Int = 0x6D00
    const val STATUS_UNKNOWN_COMMAND: Int = 0x6D00
    const val STATUS_BAD_REQUEST: Int = 0x6700

    /** The card is real, it is just not a Nomi. A different code from a malformed request. */
    const val STATUS_WRONG_AID: Int = 0x6A82
    const val STATUS_TOO_LARGE: Int = 0x6A80

    /**
     * How much of the file one exchange carries.
     *
     * The limit that matters is the *reply*, not the request: the phone doing the reading receives
     * the piece in a buffer of 253 bytes unless it asks for more, and the piece arrives with two
     * status bytes on the end of it. Anything above about 251 is a packet the reader may not be
     * able to take, and a reply that does not fit fails the tap rather than truncating quietly.
     * A shared day is a few kilobytes, so this costs a dozen round trips and no visible pause.
     */
    const val CHUNK_BYTES: Int = 248

    /**
     * A shared day larger than this is refused rather than attempted.
     *
     * The bound is what the reader will hold in memory before it has seen a single byte of
     * checksum, and it is generous: a very long day of individually logged foods is a few
     * kilobytes, and this is several times that.
     */
    const val MAX_PAYLOAD_BYTES: Int = 512 * 1024

    /** Asks the tag to become a Nomi phone. Returns just the two status bytes. */
    fun selectAid(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte(),
    ) + AID + byteArrayOf(0x00)

    /** Asks how much there is to read and how it should add up. */
    fun readMetadata(): ByteArray = byteArrayOf(CLA, INS_METADATA, 0x00, 0x00, 0x00)

    /** Asks for [offset] bytes of the payload onwards. */
    fun readChunk(offset: Int): ByteArray = require(offset >= 0) { "offset must not be negative: $offset" }.let {
        byteArrayOf(CLA, INS_CHUNK, 0x00, 0x00, 0x04) + intBytes(offset)
    }

    /** The two trailing bytes of a reply, which say how it went. */
    fun statusOf(response: ByteArray): Int {
        if (response.size < 2) return STATUS_BAD_REQUEST
        return ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response[response.size - 1].toInt() and 0xFF)
    }

    /** Everything a reply carries apart from its status. */
    fun bodyOf(response: ByteArray): ByteArray =
        if (response.size <= 2) ByteArray(0) else response.copyOfRange(0, response.size - 2)

    /** A reply that says only how it went, for a request that has no answer. */
    fun statusOnly(status: Int): ByteArray = shortBytes(status)

    fun intBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun readInt(bytes: ByteArray, at: Int): Int = ((bytes[at].toInt() and 0xFF) shl 24) or
        ((bytes[at + 1].toInt() and 0xFF) shl 16) or
        ((bytes[at + 2].toInt() and 0xFF) shl 8) or
        (bytes[at + 3].toInt() and 0xFF)

    private fun shortBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    /** A checksum of the file, so a transfer that arrived damaged is refused rather than logged. */
    fun checksumOf(payload: ByteArray): Int = CRC32().apply { update(payload) }.value.toInt()
}

/**
 * Answers a reader's requests for one shared day.
 *
 * This is the sending phone pretending to be a card. It holds the file and hands it out a piece
 * at a time, and it holds nothing else: no state, no session, no notion of a connection. That
 * matters because a tap can be abandoned halfway through, and a tag that remembered "we were at
 * offset 600" would send the rest of the file to whoever tapped next.
 */
class ShareTagResponder(private val payload: ByteArray) {

    /** What the reader is told before it starts: the length, then the checksum of those bytes. */
    fun metadata(): ByteArray =
        ShareApdu.intBytes(payload.size) +
            ShareApdu.intBytes(ShareApdu.checksumOf(payload)) +
            ShareApdu.statusOnly(ShareApdu.STATUS_OK)

    /**
     * Handles one request.
     *
     * An unrecognised request is answered with a status rather than ignored, because a reader that
     * is met with silence waits for a timeout instead of learning it is talking to the wrong thing.
     * That includes the select that opens the conversation: a phone that has nothing staged still
     * answers it, because whether there is a day to share is the next question, not this one.
     */
    fun respond(apdu: ByteArray): ByteArray = when {
        apdu.size < 4 -> ShareApdu.statusOnly(ShareApdu.STATUS_BAD_REQUEST)
        isSelect(apdu) -> select(apdu)
        apdu[0] != ShareApdu.CLA -> ShareApdu.statusOnly(ShareApdu.STATUS_UNKNOWN_COMMAND)
        apdu[1] == ShareApdu.INS_METADATA -> metadata()
        apdu[1] == ShareApdu.INS_CHUNK -> chunk(apdu)
        else -> ShareApdu.statusOnly(ShareApdu.STATUS_UNKNOWN_COMMAND)
    }

    /**
     * True for the select-by-name that opens a card session.
     *
     * A select carries no CLA of its own - it is fixed at 0x00 by the standard, with 0xA4 as the
     * instruction - so it has to be recognised before the class byte is checked. One that is too
     * short to hold an AID is not a select at all, and falls through to the bad request answer.
     */
    private fun isSelect(apdu: ByteArray): Boolean =
        apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() && apdu.size >= 5 + ShareApdu.AID.size

    /** Accepts Nomi and turns away everything else, which is what a card reader expects. */
    private fun select(apdu: ByteArray): ByteArray {
        val aidLength = ((apdu[3].toInt() and 0xFF) shl 8) or (apdu[4].toInt() and 0xFF)
        val start = 5
        val end = (start + aidLength).coerceAtMost(apdu.size - 1)
        val isNomi = end > start && apdu.copyOfRange(start, end).contentEquals(ShareApdu.AID)
        return ShareApdu.statusOnly(
            if (isNomi) ShareApdu.STATUS_OK else ShareApdu.STATUS_WRONG_AID,
        )
    }

    private fun chunk(apdu: ByteArray): ByteArray {
        // Four bytes of offset, as asked for by readChunk.
        if (apdu.size < 9) return ShareApdu.statusOnly(ShareApdu.STATUS_BAD_REQUEST)
        val offset = ShareApdu.readInt(apdu, 5)
        if (offset < 0 || offset > payload.size) {
            return ShareApdu.statusOnly(ShareApdu.STATUS_BAD_REQUEST)
        }
        val end = minOf(offset + ShareApdu.CHUNK_BYTES, payload.size)
        return payload.copyOfRange(offset, end) + ShareApdu.statusOnly(ShareApdu.STATUS_OK)
    }
}

/** Why a tap did not turn into a shared day. */
enum class ShareReceiveFailure {
    /** The phones were not close enough, or the other phone had nothing to share. */
    NoTagFound,

    /** The other phone answered but the conversation failed part way through. */
    TransferFailed,

    /** The bytes arrived but do not add up to what the sending phone promised. */
    Corrupted,

    /** The other phone is not a Nomi, or its Nomi is too old to agree on the format. */
    Incompatible,

    /** A file arrived that is not a shared day. */
    NotAShare,
}

/**
 * Pulls one shared day off a tag, a piece at a time.
 *
 * The radio is handed in as a single function, so this whole loop is a testable loop rather than
 * something that only runs on a phone. [transceive] is expected to hand each request to the
 * other phone and return its reply.
 */
suspend fun pullSharedDay(
    transceive: suspend (ByteArray) -> ByteArray,
    onProgress: (receivedBytes: Int, totalBytes: Int) -> Unit = { _, _ -> },
): Result<ByteArray> = runCatching {
    val selected = transceive(ShareApdu.selectAid())
    if (ShareApdu.statusOf(selected) != ShareApdu.STATUS_OK) {
        throw ShareReceiveException(ShareReceiveFailure.Incompatible)
    }

    val metadata = transceive(ShareApdu.readMetadata())
    if (ShareApdu.statusOf(metadata) != ShareApdu.STATUS_OK) {
        throw ShareReceiveException(ShareReceiveFailure.NoTagFound)
    }
    val header = ShareApdu.bodyOf(metadata)
    if (header.size < 8) throw ShareReceiveException(ShareReceiveFailure.Incompatible)
    val total = ShareApdu.readInt(header, 0)
    val expectedChecksum = ShareApdu.readInt(header, 4)
    if (total < 0 || total > ShareApdu.MAX_PAYLOAD_BYTES) {
        throw ShareReceiveException(ShareReceiveFailure.TransferFailed)
    }

    val received = ByteArray(total)
    var offset = 0
    while (offset < total) {
        val reply = transceive(ShareApdu.readChunk(offset))
        if (ShareApdu.statusOf(reply) != ShareApdu.STATUS_OK) {
            throw ShareReceiveException(ShareReceiveFailure.TransferFailed)
        }
        val piece = ShareApdu.bodyOf(reply)
        if (piece.isEmpty()) throw ShareReceiveException(ShareReceiveFailure.Corrupted)
        if (offset + piece.size > total) throw ShareReceiveException(ShareReceiveFailure.Corrupted)
        piece.copyInto(received, offset)
        offset += piece.size
        onProgress(offset, total)
    }

    // Checked before the bytes are parsed, because a file that arrived damaged should fail as a
    // damaged transfer rather than as a confusing complaint about the day inside it.
    if (ShareApdu.checksumOf(received) != expectedChecksum) {
        throw ShareReceiveException(ShareReceiveFailure.Corrupted)
    }
    received
}

class ShareReceiveException(val failure: ShareReceiveFailure) :
    Exception("Shared day could not be received: $failure")
