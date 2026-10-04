package com.nomi.app.ui.app

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.ai.validation.FoodDisplayName
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.providerSelection
import com.nomi.app.data.preferences.ProviderSelection
import com.nomi.app.data.remote.ai.ExaGeminiNutritionProvider
import com.nomi.app.data.remote.ai.OpenAiCompatibleProviders
import com.nomi.app.di.AppContainer
import com.nomi.app.ui.settings.AiKeyField
import com.nomi.app.ui.settings.AiProviderEditorState
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one place a stored provider choice and its secret meet to become something a request can
 * run on.
 *
 * A key only ever exists inside the block it was read for: every entry point here hands a
 * ready-made provider to its caller and wipes the characters when the block returns, so nothing
 * above this class holds a credential.
 *
 * [onResearchSources] receives the pages a running research has opened, for the spinner.
 */
internal class AiProviderAccess(
    private val container: AppContainer,
    private val preferences: StateFlow<AppPreferences>,
    private val debug: AiDebugRecorder,
    private val scope: CoroutineScope,
    private val onResearchSources: (List<String>) -> Unit,
) {
    private val mutableKeyPresence =
        MutableStateFlow<Map<ProviderPipeline, ProviderKeyPresence>>(emptyMap())
    val keyPresence: StateFlow<Map<ProviderPipeline, ProviderKeyPresence>> =
        mutableKeyPresence.asStateFlow()

    /** Sourced research on the configured provider, with the configured fallback behind it. */
    suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis =
        runWithSmartFallback(
            primary = {
                withConfiguredResearchProvider { provider ->
                    provider.researchNutrition(intent)
                }
            },
            fallback = {
                withConfiguredSmartFallback { config, key ->
                    providerFor(config, key).researchNutrition(intent)
                }
            },
            onFallback = { error ->
                debug.recordResearchFallback(status = "FALLBACK_STARTED", error = error)
            },
            onFallbackSuccess = { analysis ->
                debug.recordResearchFallback(status = "FALLBACK_VALIDATED", analysis = analysis)
            },
        ).withCleanDisplayNames()

    /**
     * Every researched item passes through here on its way to the page, so the name that is
     * previewed is the same one that is saved and later reopened for rewriting. The prompts
     * ask the model for a clean short name; this only removes what a provider left behind.
     */
    private fun FoodAnalysis.withCleanDisplayNames(): FoodAnalysis =
        copy(items = items.map { it.copy(name = FoodDisplayName.clean(it.name)) })

    /** Runs [block] against the provider configured for [pipeline], with its stored key. */
    suspend fun <T> withProvider(
        pipeline: ProviderPipeline,
        block: suspend (OpenAiCompatibleProviders) -> T,
    ): T = withConfiguredProvider(pipeline) { config, key -> block(providerFor(config, key)) }
    private suspend fun loadedPreferences(): AppPreferences = container.repository.preferences.first()

    private suspend fun <T> withConfiguredProvider(
        pipeline: ProviderPipeline,
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        val prefs = loadedPreferences()
        val selection = prefs.providerSelection(pipeline)
        require(selection.providerId.isNotBlank()) { "Configure this AI provider in Settings first." }
        return container.secretStore.useSecret(secretId(selection)) { chars ->
            val credential = AiRuntimeCredential.from(chars.concatToString())
            block(selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled), credential)
        } ?: error("Add the ${selection.providerId.displayProviderName()} API key in Settings first.")
    }

    private suspend fun <T> withConfiguredResearchProvider(
        block: suspend (NutritionResearchProvider) -> T,
    ): T {
        val prefs = loadedPreferences()
        val selection = prefs.foodResearchProvider
        if (!selection.usesExaGemini) {
            return withConfiguredProvider(ProviderPipeline.FOOD_RESEARCH) { config, key ->
                block(providerFor(config, key))
            }
        }
        val config = selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled)
        return container.secretStore.useSecret(secretId(selection)) { geminiChars ->
            val geminiCredential = AiRuntimeCredential.from(geminiChars.concatToString())
            container.secretStore.useSecret(exaSecretId()) { exaChars ->
                val exaCredential = AiRuntimeCredential.from(exaChars.concatToString())
                block(exaGeminiProvider(config, geminiCredential, exaCredential))
            } ?: error("Add the Exa API key in Settings first.")
        } ?: error("Add the Google Gemini API key in Settings first.")
    }

    private suspend fun <T : Any> withConfiguredSmartFallback(
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        val prefs = loadedPreferences()
        return withSmartFallbackCredential(prefs, prefs.smartFallbackProvider, block)
    }

    private suspend fun <T : Any> withSmartFallbackCredential(
        prefs: AppPreferences,
        selection: ProviderSelection,
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        require(selection.providerId.isNotBlank()) {
            "Configure Fallback in Settings first."
        }
        val config = selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled)
        suspend fun use(secret: String): T? = container.secretStore.useSecret(secret) { chars ->
            block(config, AiRuntimeCredential.from(chars.concatToString()))
        }
        smartFallbackCredentialIds(selection, prefs.foodResearchProvider).forEach { secret ->
            use(secret)?.let { return it }
        }
        error(
            "Configure Fallback in Settings with an API key, or select the same provider " +
                "as Food research to reuse its key.",
        )
    }

    private fun exaGeminiProvider(
        config: AiProviderConfig,
        geminiCredential: AiRuntimeCredential,
        exaCredential: AiRuntimeCredential,
    ) = ExaGeminiNutritionProvider(
        exaSearch = container.exaGeminiClient,
        geminiExtractor = container.exaGeminiClient,
        exaCredential = { exaCredential },
        geminiConfig = config,
        geminiCredential = { geminiCredential },
        searchProgressSink = onResearchSources,
        debugSink = debug::recordExaGeminiTrace,
        calorieBiasProvider = { preferences.value.calorieEstimateBias },
        fullPageTextProvider = { preferences.value.exaFullPageText },
    )

    private fun providerFor(config: AiProviderConfig, credential: AiRuntimeCredential) =
        OpenAiCompatibleProviders(
            client = container.openAiClient,
            parsingConfig = config,
            parsingCredential = { credential },
            nutritionConfig = config,
            nutritionCredential = { credential },
            portionConfig = config,
            portionCredential = { credential },
            visionConfig = config,
            visionCredential = { credential },
            calorieBiasProvider = { preferences.value.calorieEstimateBias },
            nutritionDebugSink = debug::recordNutritionScalingTrace,
        )

    /** Re-reads which pipelines have their keys stored, for the settings list. */
    fun refreshKeyPresence() {
        scope.launch {
            val prefs = loadedPreferences()
            mutableKeyPresence.value = ProviderPipeline.entries.associateWith { pipeline ->
                val selection = prefs.providerSelection(pipeline)
                val primary = runCatching {
                    container.secretStore.contains(secretId(selection))
                }.getOrDefault(false)
                val search = if (selection.usesExaGemini) {
                    runCatching { container.secretStore.contains(exaSecretId()) }.getOrDefault(false)
                } else true
                ProviderKeyPresence(primary = primary, search = search)
            }
        }
    }

    /**
     * Checks the keys typed into the one-step key form and stores each as soon as its provider
     * has answered. It is what onboarding and the AI page call.
     *
     * Keys belong to the provider account rather than to a pipeline, so one key covers every
     * pipeline on that account. The reading pipeline is checked first: it is one small
     * completion, and it proves the main key before anything is spent on research. When research
     * runs on Exa + Gemini it is checked next, which is the only way to prove the Exa key, and
     * by then the Gemini key is known to be good, so a refused credential there is Exa's.
     *
     * A blank key means "the stored one", so a second attempt after fixing only the Exa key
     * does not ask for the Gemini key again.
     */
    suspend fun connectKeys(key: String, searchKey: String) {
        val prefs = loadedPreferences()
        val reading = ProviderPipeline.FOOD_INTERPRETATION
        val readingDraft = prefs.keyFormDraft(reading).copy(apiKeyInput = key)
        checking(AiKeyField.PRIMARY) { testConnection(reading, readingDraft) }
        save(reading, readingDraft)

        val research = ProviderPipeline.FOOD_RESEARCH
        if (!prefs.providerSelection(research).usesExaGemini) return
        val researchDraft = prefs.keyFormDraft(research)
            .copy(apiKeyInput = key, searchApiKeyInput = searchKey)
        try {
            testConnection(research, researchDraft)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw KeyCheckException(AiKeyField.SEARCH.takeIf { error.blamesCredential() }, error)
        }
        save(research, researchDraft)
    }

    private suspend fun checking(field: AiKeyField, check: suspend () -> Unit) {
        try {
            check()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw KeyCheckException(field, error)
        }
    }

    private fun AppPreferences.keyFormDraft(pipeline: ProviderPipeline): AiProviderEditorState {
        val selection = providerSelection(pipeline)
        return AiProviderEditorState(
            purpose = pipeline.displayName(),
            provider = selection.providerId.toProviderKind(),
            endpoint = runCatching { selection.toRuntimeConfig().endpoint }
                .getOrElse { selection.endpoint.orEmpty() },
            model = selection.model,
        )
    }

    /**
     * Whether keys are already stored for the provider a draft points at, so the editor can say
     * "stored" for a provider the user has just switched to. False for a draft that does not
     * resolve to an endpoint yet.
     */
    suspend fun storedKeyPresence(
        pipeline: ProviderPipeline,
        state: AiProviderEditorState,
    ): ProviderKeyPresence {
        val selection = state.toProviderSelection(pipeline)
        val primary = runCatching { container.secretStore.contains(secretId(selection)) }
            .getOrDefault(false)
        val search = selection.usesExaGemini &&
            runCatching { container.secretStore.contains(exaSecretId()) }.getOrDefault(false)
        return ProviderKeyPresence(primary = primary, search = search)
    }

    /** Stores the edited provider and any key typed with it. Throws when the draft is unusable. */
    suspend fun save(pipeline: ProviderPipeline, state: AiProviderEditorState) {
        val draft = state.toProviderSelection(pipeline)
        val config = draft.toRuntimeConfig()
        val selection = draft.copy(endpoint = config.endpoint)
        state.apiKeyInput.normalizedApiKeyCharsOrNull()?.let { chars ->
            try {
                container.secretStore.put(secretId(selection), chars)
            } finally {
                chars.fill('\u0000')
            }
        }
        if (config.kind == AiProviderKind.EXA_GEMINI) {
            state.searchApiKeyInput.normalizedApiKeyCharsOrNull()?.let { chars ->
                try {
                    container.secretStore.put(exaSecretId(), chars)
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
        container.repository.appPreferencesStore.setProvider(pipeline, selection)
    }

    /** Deletes the keys stored for the edited provider. False when there was nothing to delete. */
    suspend fun removeStoredKeys(pipeline: ProviderPipeline, state: AiProviderEditorState): Boolean {
        val selection = state.toProviderSelection(pipeline)
        val primaryRemoved = container.secretStore.delete(secretId(selection))
        val searchRemoved = if (selection.usesExaGemini) {
            container.secretStore.delete(exaSecretId())
        } else false
        return primaryRemoved || searchRemoved
    }

    /**
     * Makes one real request with the edited provider, using the key in the editor when one was
     * typed and the stored one otherwise. Returns normally when the provider answered.
     */
    suspend fun testConnection(pipeline: ProviderPipeline, state: AiProviderEditorState) {
        val draft = state.toProviderSelection(pipeline)
        val config = draft.toRuntimeConfig()
        val selection = draft.copy(endpoint = config.endpoint)
        suspend fun testWith(
            targetConfig: AiProviderConfig,
            credential: AiRuntimeCredential,
        ) {
            if (pipeline.requiresWebResearch()) {
                providerFor(targetConfig, credential).researchNutrition(
                    providerConnectionTestIntent(),
                )
            } else {
                val content = container.openAiClient.completeJson(
                    config = targetConfig,
                    credential = credential,
                    systemPrompt = "Return one JSON object and nothing else.",
                    userPrompt = "Reply with {\"ok\":true} to confirm this connection.",
                )
                require(content.isNotBlank()) { "The provider returned an empty response." }
            }
        }
        suspend fun <T> withDraftOrStoredCredential(
            input: String,
            storedId: String,
            missingMessage: String,
            block: suspend (AiRuntimeCredential) -> T,
        ): T {
            val entered = input.normalizedApiKeyCharsOrNull()
            if (entered != null) {
                return try {
                    block(AiRuntimeCredential.from(entered.concatToString()))
                } finally {
                    entered.fill('\u0000')
                }
            }
            return container.secretStore.useSecret(storedId) { chars ->
                block(AiRuntimeCredential.from(chars.concatToString()))
            } ?: error(missingMessage)
        }

        if (config.kind == AiProviderKind.EXA_GEMINI) {
            withDraftOrStoredCredential(
                input = state.apiKeyInput,
                storedId = secretId(selection),
                missingMessage = "Enter a Google Gemini API key before testing this provider.",
            ) { geminiCredential ->
                withDraftOrStoredCredential(
                    input = state.searchApiKeyInput,
                    storedId = exaSecretId(),
                    missingMessage = "Enter an Exa API key before testing this provider.",
                ) { exaCredential ->
                    exaGeminiProvider(config, geminiCredential, exaCredential)
                        .researchNutrition(providerConnectionTestIntent())
                }
            }
            return
        }

        val enteredKey = state.apiKeyInput.normalizedApiKeyCharsOrNull()
        if (enteredKey != null) {
            try {
                testWith(config, AiRuntimeCredential.from(enteredKey.concatToString()))
            } finally {
                enteredKey.fill('\u0000')
            }
        } else if (pipeline == ProviderPipeline.SMART_FALLBACK) {
            withSmartFallbackCredential(
                prefs = loadedPreferences(),
                selection = selection,
                block = ::testWith,
            )
        } else {
            container.secretStore.useSecret(secretId(selection)) { chars ->
                testWith(config, AiRuntimeCredential.from(chars.concatToString()))
            } ?: error("Enter an API key before testing this provider.")
        }
    }
}

/**
 * A key check that failed, and the field the failure belongs to when that is known. The cause is
 * what gets worded for the user.
 */
internal class KeyCheckException(val field: AiKeyField?, cause: Throwable) :
    Exception(cause.message, cause)

/** True for a key that is missing or that the provider refused, as opposed to a bad connection. */
private fun Throwable.blamesCredential(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        (cause as? ResponseException)?.response?.status?.value in setOf(401, 403) ||
            cause.message?.contains("API key", ignoreCase = true) == true
    }
