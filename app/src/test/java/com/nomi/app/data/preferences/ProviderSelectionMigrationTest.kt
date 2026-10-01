package com.nomi.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionMigrationTest {
    @Test
    fun `a fresh install researches with Exa + Gemini and reads with Gemini`() {
        val preferences = AppPreferences()

        assertEquals("exa-gemini", preferences.foodResearchProvider.providerId)
        assertEquals(DEFAULT_DIRECT_GEMINI_NUTRITION_MODEL, preferences.foodResearchProvider.model)
        listOf(
            preferences.foodInterpretationProvider,
            preferences.portionChangeProvider,
            preferences.visionProvider,
        ).forEach { reader ->
            assertEquals("gemini", reader.providerId)
            assertEquals(DEFAULT_DIRECT_GEMINI_MODEL, reader.model)
        }
        // Fallback has to search the web, which Gemini alone cannot, so it stays on OpenRouter.
        assertEquals("openrouter", preferences.smartFallbackProvider.providerId)
        assertEquals(DEFAULT_OPENROUTER_MODEL, preferences.smartFallbackProvider.model)
    }

    @Test
    fun `an install set up before the change keeps OpenRouter where it saved nothing`() {
        assertTrue(usesLegacyProviderDefaults(LEGACY_PROVIDER_DEFAULTS))
        ProviderPipeline.entries.forEach { pipeline ->
            assertEquals("openrouter", pipeline.legacyProvider().providerId)
            assertEquals(pipeline.defaultOpenRouterModel(), pipeline.legacyProvider().model)
        }
    }

    @Test
    fun `an install that is new, or finished onboarding since, gets the recommended setup`() {
        // Nothing stamped yet: a fresh install on its way through onboarding.
        assertFalse(usesLegacyProviderDefaults(null))
        assertFalse(usesLegacyProviderDefaults(RECOMMENDED_PROVIDER_DEFAULTS))
    }

    @Test
    fun `gemini with no model saved gets the light one`() {
        val blank = ProviderSelection(providerId = "gemini")

        assertEquals(DEFAULT_DIRECT_GEMINI_MODEL, blank.withSupportedModel(ProviderPipeline.VISION).model)
        val chosen = ProviderSelection(providerId = "gemini", model = "gemini-2.5-flash")
        assertSame(chosen, chosen.withSupportedModel(ProviderPipeline.VISION))
    }

    @Test
    fun `research keeps the searching model while other pipelines get the fast one`() {
        val retired = ProviderSelection(providerId = "openrouter", model = RETIRED_OPENROUTER_MODEL)

        assertEquals(
            DEFAULT_OPENROUTER_RESEARCH_MODEL,
            retired.withSupportedModel(ProviderPipeline.FOOD_RESEARCH).model,
        )
        assertEquals(
            DEFAULT_OPENROUTER_MODEL,
            retired.withSupportedModel(ProviderPipeline.VISION).model,
        )
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
    fun `earlier OpenRouter defaults and their shorthand all migrate`() {
        listOf(
            PREVIOUS_OPENROUTER_MODEL,
            PREVIOUS_OPENROUTER_DEFAULT_MODEL,
            "gpt5.6sol",
            "gpt-5.6-sol",
            "openai/gpt5.6sol",
        ).forEach { retiredModel ->
            val selection = ProviderSelection(providerId = "openrouter", model = retiredModel)

            assertEquals(DEFAULT_OPENROUTER_MODEL, selection.withSupportedModel().model)
        }
    }

    @Test
    fun `an unconfigured pipeline is filled in so one key covers everything`() {
        val unconfigured = ProviderSelection()

        val filled = unconfigured.withSupportedModel(ProviderPipeline.VISION)

        assertEquals("openrouter", filled.providerId)
        assertEquals(DEFAULT_OPENROUTER_MODEL, filled.model)
    }

    @Test
    fun `user selected model is left unchanged`() {
        val selected = ProviderSelection(providerId = "openrouter", model = "openai/gpt-5.2")

        assertSame(selected, selected.withSupportedModel())
    }

    @Test
    fun `another provider is never rewritten to OpenRouter`() {
        val perplexity = ProviderSelection(providerId = "perplexity", model = "sonar")

        assertSame(perplexity, perplexity.withSupportedModel(ProviderPipeline.FOOD_RESEARCH))
    }

    @Test
    fun `OpenRouter Gemini slug migrates to the direct Google model identifier`() {
        val previous = ProviderSelection(
            providerId = "exa-gemini",
            model = PREVIOUS_OPENROUTER_GEMINI_NUTRITION_MODEL,
            endpoint = "https://openrouter.ai/api/v1",
        )

        val migrated = previous.withSupportedModel(ProviderPipeline.FOOD_RESEARCH)

        assertEquals(DEFAULT_DIRECT_GEMINI_NUTRITION_MODEL, migrated.model)
        assertEquals(previous.providerId, migrated.providerId)
        assertEquals(previous.endpoint, migrated.endpoint)
    }

}
