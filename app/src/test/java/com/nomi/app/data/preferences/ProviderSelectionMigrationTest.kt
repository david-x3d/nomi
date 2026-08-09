package com.nomi.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProviderSelectionMigrationTest {
    @Test
    fun `new defaults use an available OpenRouter model`() {
        val preferences = AppPreferences()

        assertEquals("openrouter", preferences.foodResearchProvider.providerId)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.foodResearchProvider.model)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.foodInterpretationProvider.model)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.portionChangeProvider.model)
        assertEquals("openrouter", preferences.smartFallbackProvider.providerId)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.smartFallbackProvider.model)
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
    fun `previous OpenRouter default migrates to GPT 5 6 Sol`() {
        val previous = ProviderSelection(
            providerId = "openrouter",
            model = PREVIOUS_OPENROUTER_MODEL,
        )

        assertEquals(DEFAULT_OPENROUTER_MODEL, previous.withSupportedModel().model)
    }

    @Test
    fun `user selected model is left unchanged`() {
        val selected = ProviderSelection(providerId = "openrouter", model = "openai/gpt-5.2")

        assertSame(selected, selected.withSupportedModel())
    }
}
