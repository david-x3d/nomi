package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiRuntimeCredential
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedExaSearchTest {
    private val credential = AiRuntimeCredential.from("test-key")

    @Test
    fun `parallel identical searches pay for one Exa request`() = runBlocking {
        val calls = AtomicInteger()
        val shared = SharedExaSearch { query, _, _, _, _ ->
            calls.incrementAndGet()
            delay(50)
            ExaSearchResponse(requestId = query)
        }

        val answers = List(4) {
            async { shared.search("burger", credential, 1_000, 5, includePageText = false) }
        }.awaitAll()

        assertEquals(1, calls.get())
        assertEquals(List(4) { "burger" }, answers.map { it.requestId })
    }

    @Test
    fun `different searches are each made`() = runBlocking {
        val calls = AtomicInteger()
        val shared = SharedExaSearch { query, _, _, _, _ ->
            calls.incrementAndGet()
            ExaSearchResponse(requestId = query)
        }

        shared.search("burger", credential, 1_000, 5, includePageText = false)
        shared.search("burger", credential, 1_000, 5, includePageText = true)
        shared.search("fries", credential, 1_000, 5, includePageText = false)

        assertEquals(3, calls.get())
    }

    @Test
    fun `a failed search is tried again`() = runBlocking {
        val calls = AtomicInteger()
        val shared = SharedExaSearch { query, _, _, _, _ ->
            if (calls.incrementAndGet() == 1) error("Exa unavailable")
            ExaSearchResponse(requestId = query)
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { shared.search("burger", credential, 1_000, 5, includePageText = false) }
        }
        assertEquals("burger", shared.search("burger", credential, 1_000, 5, false).requestId)
        assertEquals(2, calls.get())
    }
}
