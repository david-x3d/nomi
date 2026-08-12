package com.nomi.app.ui.app

import com.nomi.app.domain.Micronutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NomiFoodDetailMicronutrientsTest {
    @Test
    fun `detail grids include only enabled micronutrients`() {
        val visible = detailMicronutrients(
            listOf(
                Micronutrient.FIBER,
                Micronutrient.SUGAR,
                Micronutrient.FIBER,
            ),
        )

        assertEquals(listOf(Micronutrient.FIBER, Micronutrient.SUGAR), visible)
        assertFalse(Micronutrient.SODIUM in visible)
    }

    @Test
    fun `detail grids contain no micronutrients when tracking is disabled`() {
        assertEquals(emptyList<Micronutrient>(), detailMicronutrients(emptyList()))
    }
}
