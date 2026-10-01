package com.nomi.app.ui.settings

import com.nomi.app.ai.model.AiProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which key form the AI page shows is decided by the four tasks every entry passes through. A
 * wrong answer here either hides the keys a setup needs or asks for one it does not use.
 */
class AiKeySetupTest {

    @Test
    fun `the recommended pair asks for a Gemini key and an Exa key`() {
        val state = stateOf(
            research = setting(AiProviderKind.EXA_GEMINI, primary = true, search = false),
            readers = AiProviderKind.GEMINI,
        )

        assertEquals(
            AiKeySetup.GeminiWithExa(hasGeminiKey = true, hasExaKey = false),
            state.aiKeySetup,
        )
    }

    @Test
    fun `one provider for every task asks for one key`() {
        val state = stateOf(
            research = setting(AiProviderKind.OPEN_ROUTER),
            readers = AiProviderKind.OPEN_ROUTER,
        )

        assertTrue(state.aiKeySetup is AiKeySetup.Single)
    }

    @Test
    fun `fallback on another provider does not change the answer`() {
        val state = stateOf(
            research = setting(AiProviderKind.OPEN_ROUTER),
            readers = AiProviderKind.OPEN_ROUTER,
            fallback = AiProviderKind.PERPLEXITY,
        )

        assertTrue(state.aiKeySetup is AiKeySetup.Single)
    }

    @Test
    fun `any other split is set up task by task`() {
        val mixed = stateOf(
            research = setting(AiProviderKind.EXA_GEMINI),
            readers = AiProviderKind.OPEN_ROUTER,
        )

        assertEquals(AiKeySetup.PerTask, mixed.aiKeySetup)
        // Before the settings have loaded there is nothing to ask for either.
        assertEquals(AiKeySetup.PerTask, SettingsUiState().aiKeySetup)
    }

    private fun setting(
        kind: AiProviderKind,
        primary: Boolean = false,
        search: Boolean = false,
    ) = AiProviderSetting(
        purpose = "Food research",
        provider = kind,
        model = "model",
        endpoint = "https://example.com",
        hasApiKey = primary && search,
        hasPrimaryApiKey = primary,
        hasSearchApiKey = search,
    )

    private fun stateOf(
        research: AiProviderSetting,
        readers: AiProviderKind,
        fallback: AiProviderKind = AiProviderKind.OPEN_ROUTER,
    ) = SettingsUiState(
        aiProviders = listOf(research) +
            List(3) { setting(readers) } +
            setting(fallback),
    )
}
