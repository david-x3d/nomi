package com.nomi.app.integration.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingCaloriesPhraseTest {
    @Test
    fun `under target uses left today`() {
        assertEquals(RemainingCaloriesPhrase.LEFT_TODAY, RemainingCaloriesPhrase.templateKey(400.0, 2_000.0))
        assertEquals(1_600, RemainingCaloriesPhrase.deltaKcal(400.0, 2_000.0))
    }

    @Test
    fun `over target uses over wording`() {
        assertEquals(RemainingCaloriesPhrase.OVER_TARGET, RemainingCaloriesPhrase.templateKey(2_190.0, 2_000.0))
        assertEquals(190, RemainingCaloriesPhrase.deltaKcal(2_190.0, 2_000.0))
    }
}
