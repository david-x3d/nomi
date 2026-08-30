package com.nomi.app.integration.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NomiExternalIntentsTest {
    @Test
    fun `deep link logs food from query`() {
        val command = NomiExternalIntents.parse(
            action = NomiExternalIntents.ACTION_VIEW,
            scheme = "nomi",
            host = "log",
            query = mapOf("food" to "eine Banane"),
            extras = emptyMap(),
        )
        assertEquals(NomiExternalCommand.LogFood("eine Banane"), command)
    }

    @Test
    fun `assistant observation extra logs food`() {
        val command = NomiExternalIntents.parse(
            action = NomiExternalIntents.ACTION_LOG_FOOD,
            scheme = null,
            host = null,
            query = emptyMap(),
            extras = mapOf("foodObservation.aboutFood.name" to "füge eine Banane hinzu"),
        )
        assertEquals(NomiExternalCommand.LogFood("eine Banane"), command)
    }

    @Test
    fun `shared text can ask remaining calories`() {
        val command = NomiExternalIntents.parse(
            action = NomiExternalIntents.ACTION_SEND,
            scheme = null,
            host = null,
            query = emptyMap(),
            extras = mapOf(NomiExternalIntents.EXTRA_TEXT to "how many kcal left"),
            mimeType = "text/plain",
        )
        assertEquals(NomiExternalCommand.SpeakCalories, command)
    }

    @Test
    fun `photo and menu hosts open capture`() {
        assertEquals(
            NomiExternalCommand.CapturePhoto,
            NomiExternalIntents.parse(
                action = NomiExternalIntents.ACTION_VIEW,
                scheme = "nomi",
                host = "photo",
                query = emptyMap(),
                extras = emptyMap(),
            ),
        )
        assertEquals(
            NomiExternalCommand.ScanMenu,
            NomiExternalIntents.parse(
                action = NomiExternalIntents.ACTION_SCAN_MENU,
                scheme = null,
                host = null,
                query = emptyMap(),
                extras = emptyMap(),
            ),
        )
    }

    @Test
    fun `launcher and empty log are ignored`() {
        assertNull(
            NomiExternalIntents.parse(
                action = "android.intent.action.MAIN",
                scheme = null,
                host = null,
                query = emptyMap(),
                extras = emptyMap(),
            ),
        )
        assertNull(
            NomiExternalIntents.parse(
                action = NomiExternalIntents.ACTION_LOG_FOOD,
                scheme = null,
                host = null,
                query = emptyMap(),
                extras = emptyMap(),
            ),
        )
    }

    @Test
    fun `google search action logs the query`() {
        val command = NomiExternalIntents.parse(
            action = NomiExternalIntents.ACTION_GOOGLE_SEARCH,
            scheme = null,
            host = null,
            query = emptyMap(),
            extras = mapOf("query" to "a banana"),
        )
        assertTrue(command is NomiExternalCommand.LogFood)
        assertEquals("a banana", (command as NomiExternalCommand.LogFood).text)
    }
}
