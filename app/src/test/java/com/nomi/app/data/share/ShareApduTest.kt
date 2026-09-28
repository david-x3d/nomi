package com.nomi.app.data.share

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two halves of a tap, run against each other.
 *
 * Nothing here touches a radio. The sending phone is a [ShareTagResponder] and the receiving
 * phone is [pullSharedDay], and the test wires them together through a plain function. That is
 * enough to catch the failure that would otherwise only appear on two phones held together: the
 * two ends disagreeing about a byte, and the file simply never arriving.
 */
class ShareApduTest {

    @Test
    fun `an idle Nomi still identifies itself without reporting an incompatible version`() = runTest {
        val card = ShareTagResponder(null)
        assertEquals(ShareApdu.STATUS_OK, ShareApdu.statusOf(card.respond(ShareApdu.selectAid())))
        assertEquals(ShareReceiveFailure.NoTagFound, pullSharedDay(card::respond).failureOrNull())
    }

    @Test
    fun `older sender with cleared offer is not reported as incompatible`() = runTest {
        assertEquals(ShareReceiveFailure.NoTagFound, pullSharedDay({
            ShareApdu.statusOnly(ShareApdu.STATUS_NO_PAYLOAD)
        }).failureOrNull())
    }


    @Test
    fun `registered AID is routable by Android and matches the reader`() {
        val file = java.io.File("src/main/res/xml/nfc_share_apdu_service.xml")
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file)
        val filter = document.getElementsByTagName("aid-filter").item(0)
        val aid = filter.attributes.getNamedItem("android:name").nodeValue
        assertTrue("Android requires 5 to 16 bytes", aid.length in 10..32 && aid.length % 2 == 0)
        assertEquals(ShareApdu.AID.joinToString("") { "%02X".format(it) }, aid)
        assertEquals(0xF0, ShareApdu.AID[0].toInt() and 0xF0)
    }

    @Test
    fun `select accepts optional Le and rejects truncated AIDs`() {
        val responder = ShareTagResponder(byteArrayOf(1))
        val select = ShareApdu.selectAid()
        assertEquals(ShareApdu.STATUS_OK, ShareApdu.statusOf(responder.respond(select.dropLast(1).toByteArray())))
        assertEquals(ShareApdu.STATUS_BAD_REQUEST, ShareApdu.statusOf(responder.respond(select.dropLast(2).toByteArray())))
        val badLength = select.copyOf().also { it[4] = 0x7F }
        assertEquals(ShareApdu.STATUS_BAD_REQUEST, ShareApdu.statusOf(responder.respond(badLength)))
        val badParameter = select.copyOf().also { it[3] = 1 }
        assertEquals(ShareApdu.STATUS_BAD_REQUEST, ShareApdu.statusOf(responder.respond(badParameter)))
    }


    @Test
    fun `a shared day survives a whole tap`() = runTest {
        val payload = NomiSharePayload.encode(
            NomiSharePayload.envelope(
                day = "2026-09-28",
                foods = listOf(
                    food(1, "Toast mit Butter", 196.0),
                    food(2, "Banane", 105.0),
                ),
                selectedIds = setOf(1L, 2L),
                includeTotals = true,
                sharedAtEpochMillis = 1L,
                appVersionName = "2.5.2",
            )!!,
        )

        val received = pullSharedDay(transceive = radio(payload)).getOrThrow()

        assertArrayEquals(payload, received)
    }

    @Test
    fun `a day of every size comes across whole`() = runTest {
        // A day's file is a few hundred bytes and a very long one is a few thousand, so the sizes
        // that matter are the small one that fits in a single exchange and the large one that has
        // to be split across many. Both must come back identical.
        listOf(1, 100, 479, 480, 481, 960, 4_096, 40_000).forEach { size ->
            val payload = ByteArray(size) { (it % 251).toByte() }

            val received = pullSharedDay(transceive = radio(payload)).getOrThrow()

            assertArrayEquals("size $size", payload, received)
        }
    }

    @Test
    fun `the reader is told how much to expect before it starts`() = runTest {
        val payload = ByteArray(1_000)
        val progress = mutableListOf<Pair<Int, Int>>()

        pullSharedDay(
            transceive = radio(payload),
            onProgress = { received, total -> progress += received to total },
        )

        // The reader has to know the size to know when to stop, and it has to be able to show
        // something while the phones are still touching.
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.second == 1_000 })
        assertEquals(1_000, progress.last().first)
    }

    @Test
    fun `a phone that is not Nomi is turned away rather than left waiting`() = runTest {
        // A reader that is met with silence waits for a timeout. A status word at least lets it
        // say "that is not a Nomi" instead of failing for reasons that make no sense.
        val result = pullSharedDay(transceive = { byteArrayOf(0x6A, 0x82.toByte()) })

        assertEquals(ShareReceiveFailure.Incompatible, result.failureOrNull())
    }

    @Test
    fun `a tap with nothing to share is reported as finding no tag`() = runTest {
        val responder = ShareTagResponder(ByteArray(0))
        val result = pullSharedDay(transceive = { apdu ->
            // A phone with nothing staged answers the select and then has nothing to offer.
            if (apdu[1] == ShareApdu.INS_METADATA) {
                ShareApdu.statusOnly(ShareApdu.STATUS_NO_PAYLOAD)
            } else {
                responder.respond(apdu)
            }
        })

        assertEquals(ShareReceiveFailure.NoTagFound, result.failureOrNull())
    }

    @Test
    fun `a transfer that stops halfway is reported rather than logged half a day`() = runTest {
        val payload = ByteArray(2_000)
        val responder = ShareTagResponder(payload)
        var pieces = 0
        val result = pullSharedDay(transceive = { apdu ->
            if (apdu[1] == ShareApdu.INS_CHUNK) {
                // The phones drift apart after a piece or two, which is what a lost tap looks
                // like from the receiving end.
                pieces++
                if (pieces > 2) ShareApdu.statusOnly(ShareApdu.STATUS_BAD_REQUEST)
                else responder.respond(apdu)
            } else {
                responder.respond(apdu)
            }
        })

        assertEquals(ShareReceiveFailure.TransferFailed, result.failureOrNull())
    }

    @Test
    fun `bytes that arrive damaged are refused`() = runTest {
        val payload = ByteArray(600) { (it % 97).toByte() }
        val responder = ShareTagResponder(payload)
        val result = pullSharedDay(transceive = { apdu ->
            val reply = responder.respond(apdu)
            // Flip one byte in the first piece, the way a noisy tap would.
            if (apdu[1] == ShareApdu.INS_CHUNK) {
                reply.copyOf().also { it[10] = (it[10] + 1).toByte() }
            } else {
                reply
            }
        })

        // A file that arrived damaged has to fail as a damaged transfer, not as a complaint
        // about the contents of a day that was never really sent.
        assertEquals(ShareReceiveFailure.Corrupted, result.failureOrNull())
    }

    @Test
    fun `an absurd length is refused before anything is held in memory`() = runTest {
        val result = pullSharedDay(transceive = { apdu ->
            if (apdu[1] == ShareApdu.INS_METADATA) {
                ShareApdu.intBytes(Int.MAX_VALUE) + ShareApdu.intBytes(0) +
                    ShareApdu.statusOnly(ShareApdu.STATUS_OK)
            } else {
                ShareApdu.statusOnly(ShareApdu.STATUS_OK)
            }
        })

        assertEquals(ShareReceiveFailure.TransferFailed, result.failureOrNull())
    }

    @Test
    fun `the responder only answers the conversation it agreed to`() {
        val responder = ShareTagResponder("hello".toByteArray())

        assertEquals(ShareApdu.STATUS_OK, ShareApdu.statusOf(responder.respond(ShareApdu.selectAid())))
        // A card is still a card when it is not a Nomi, and saying so is what lets a reader stop
        // instead of waiting for a reply that is never coming.
        val foreignSelect = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x04) +
            byteArrayOf(0xD2.toByte(), 0x00, 0x00, 0x01) + byteArrayOf(0x00)
        assertEquals(
            ShareApdu.STATUS_WRONG_AID,
            ShareApdu.statusOf(responder.respond(foreignSelect)),
        )
        assertEquals(
            ShareApdu.STATUS_UNKNOWN_COMMAND,
            ShareApdu.statusOf(responder.respond(byteArrayOf(0x80.toByte(), 0x77, 0x00, 0x00))),
        )
        assertEquals(
            ShareApdu.STATUS_UNKNOWN_COMMAND,
            ShareApdu.statusOf(responder.respond(byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04, 0x00))),
        )
        assertEquals(
            ShareApdu.STATUS_BAD_REQUEST,
            ShareApdu.statusOf(responder.respond(byteArrayOf(0x80.toByte()))),
        )
    }

    @Test
    fun `the responder does not remember a previous tap`() = runTest {
        // A tag that remembered "we were at offset 600" would send the rest of the file to
        // whoever tapped next, so every read has to stand on its own offset.
        val payload = ByteArray(1_500) { (it % 251).toByte() }
        val responder = ShareTagResponder(payload)
        val offset = 1_000

        val first = ShareApdu.bodyOf(responder.respond(ShareApdu.readChunk(offset)))
        val second = ShareApdu.bodyOf(responder.respond(ShareApdu.readChunk(offset)))

        // Asked the same thing twice, it gives the same piece: there is no session to be halfway
        // through, and a second reader gets the whole file rather than the tail of the first.
        assertArrayEquals(first, second)
        assertArrayEquals(
            payload.copyOfRange(offset, minOf(offset + ShareApdu.CHUNK_BYTES, payload.size)),
            first,
        )
    }

    @Test
    fun `the chunk size stays inside what a phone's radio can carry`() {
        // The reply is a piece plus its two status bytes, and it has to arrive in the reader's
        // 253 byte buffer. A day that does not fit in one exchange is split, and splitting is fine.
        assertTrue(ShareApdu.CHUNK_BYTES + 2 <= 253)
        assertTrue(ShareApdu.readMetadata().size <= 5)
        assertTrue(ShareApdu.selectAid().size <= 253)
    }

    @Test
    fun `an offset is four bytes so a long day is not capped at 64k`() {
        val apdu = ShareApdu.readChunk(100_000)

        assertEquals(100_000, ShareApdu.readInt(apdu, 5))
    }

    /** A loopback radio: the sending phone's responder, with the link's latency flattened out. */
    private fun radio(payload: ByteArray): suspend (ByteArray) -> ByteArray {
        val responder = ShareTagResponder(payload)
        return { apdu -> responder.respond(apdu) }
    }

    private fun food(id: Long, name: String, kcal: Double) = ShareableFood(
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

    private fun Result<ByteArray>.failureOrNull(): ShareReceiveFailure? =
        (exceptionOrNull() as? ShareReceiveException)?.failure
}
