package com.nomi.app.ai.usage

import com.nomi.app.data.local.entity.AiUsageEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUsageSummaryTest {

    @Test
    fun `summarize returns recent events sorted newest first and capped at 30`() {
        val events = (1..40).map { index ->
            AiUsageEventEntity(
                id = index.toLong(),
                pipeline = if (index % 2 == 0) "FOOD_RESEARCH" else "VISION",
                resolutionKind = if (index % 5 == 0) {
                    AiUsageResolutionKind.CACHE_HIT.name
                } else {
                    AiUsageResolutionKind.AI_CALL.name
                },
                providerId = if (index % 5 == 0) "cache" else "gemini",
                model = if (index % 5 == 0) "pre-update-reused" else "gemini-2.5-flash",
                promptTokens = if (index % 5 == 0) 0 else 1000,
                completionTokens = if (index % 5 == 0) 0 else 200,
                estimatedCostUsd = if (index % 5 == 0) 0.0 else 0.001,
                costSource = if (index % 5 == 0) AiCostSource.FREE.name else AiCostSource.ESTIMATED.name,
                createdAtEpochMillis = 1_700_000_000_000L + index * 10_000L,
            )
        }

        val summary = AiCostEstimator.summarize(events)

        assertEquals(40, summary.totalEvents)
        assertEquals(30, summary.recentEvents.size)
        assertEquals(40L, summary.recentEvents.first().id)
        assertEquals(11L, summary.recentEvents.last().id)
        assertEquals(AiUsageResolutionKind.CACHE_HIT, summary.recentEvents.first().resolutionKind)
        assertEquals(0.0, summary.recentEvents.first().costUsd, 1e-9)
        assertTrue(
            summary.recentEvents.zipWithNext().all { (a, b) ->
                a.createdAtEpochMillis >= b.createdAtEpochMillis
            },
        )
    }
}
