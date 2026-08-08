package com.nomi.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProviderSelectionMigrationTest {
    @Test
    fun `new defaults use an available OpenRouter model`() {
        val preferences = AppPreferences()

        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.foodInterpretationProvider.model)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.portionChangeProvider.model)
    }

    @Test
    fun `retired OpenRouter default migrates without changing credential identity inputs`() {
        val retired = ProviderSelection(
            providerId = "openrouter",
            model = RETIRED_OPENROUTER_MODEL,
            endpoint = "https://openrouter.ai/api/v1",
            advancedParametersJson = "{\"temperature\":0.1}",
        )

        val migrated = retired.withSupportedModel()

        assertEquals(DEFAULT_OPENROUTER_MODEL, migrated.model)
        assertEquals(retired.providerId, migrated.providerId)
        assertEquals(retired.endpoint, migrated.endpoint)
        assertEquals(retired.advancedParametersJson, migrated.advancedParametersJson)
    }

    @Test
    fun `user selected model is left unchanged`() {
        val selected = ProviderSelection(providerId = "openrouter", model = "openai/gpt-5.2")

        assertSame(selected, selected.withSupportedModel())
    }
}
