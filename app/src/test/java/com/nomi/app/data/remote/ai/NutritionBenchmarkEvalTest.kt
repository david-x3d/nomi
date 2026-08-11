package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.validation.AiResponseValidator
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_MODEL
import com.nomi.app.data.preferences.DEFAULT_OPENROUTER_RESEARCH_MODEL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Opt-in raw benchmark executor. eval/run_eval.py validates and scores its output.
 * A normal unit-test run returns immediately and never spends provider credits.
 */
class NutritionBenchmarkEvalTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun executeProductionProviders() = runBlocking {
        if (System.getenv("NOMI_RUN_EVAL_LIVE") != "1") return@runBlocking
        val root = repositoryRoot()
        val allCases = json.parseToJsonElement(
            Files.readString(root.resolve("eval/eval_cases.json")),
        ).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
        val requestedIds = System.getenv("NOMI_EVAL_CASE_IDS").orEmpty()
            .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val cases = if (requestedIds.isEmpty()) allCases else allCases.filter {
            it.getValue("id").jsonPrimitive.content in requestedIds
        }
        require(requestedIds.isEmpty() || cases.size == requestedIds.size) {
            "NOMI_EVAL_CASE_IDS contains unknown or duplicate case IDs"
        }
        val providerMode = System.getenv("NOMI_EVAL_PROVIDER").orEmpty().ifBlank { "both" }
        require(providerMode in setOf("both", "sonar", "exa_gemini")) {
            "NOMI_EVAL_PROVIDER must be both, sonar, or exa_gemini"
        }
        val openRouterKey = if (providerMode in setOf("both", "sonar")) {
            requiredEnv("OPENROUTER_API_KEY")
        } else null
        val geminiKey = if (providerMode in setOf("both", "exa_gemini")) {
            requiredEnv("GEMINI_API_KEY")
        } else null
        val exaKey = if (providerMode in setOf("both", "exa_gemini")) {
            requiredEnv("EXA_API_KEY")
        } else null
        val suffix = System.getenv("NOMI_EVAL_OUTPUT_SUFFIX").orEmpty()
        require(suffix.matches(Regex("[A-Za-z0-9_-]*"))) { "Invalid eval output suffix" }
        val results = root.resolve("eval/results")
        Files.createDirectories(results)

        if (providerMode in setOf("both", "sonar")) {
            val sonar = executeRun("sonar", cases) { case ->
                executeSonar(case.input(), case.country(), checkNotNull(openRouterKey))
            }
            Files.writeString(results.resolve("sonar${suffix}_raw.json"), json.encodeToString(sonar))
        }

        if (providerMode in setOf("both", "exa_gemini")) {
            val exaGemini = executeRun("exa_gemini", cases) { case ->
                executeExaGemini(
                    case.input(),
                    case.country(),
                    checkNotNull(geminiKey),
                    checkNotNull(exaKey),
                )
            }
            Files.writeString(
                results.resolve("exa_gemini${suffix}_raw.json"),
                json.encodeToString(exaGemini),
            )
        }
    }

    private suspend fun executeRun(
        provider: String,
        cases: List<kotlinx.serialization.json.JsonObject>,
        execute: suspend (kotlinx.serialization.json.JsonObject) -> RawCase,
    ): RawRun {
        val started = Instant.now().toString()
        val output = cases.mapIndexed { index, case ->
            val result = execute(case).copy(id = case.getValue("id").jsonPrimitive.content)
            println("[$provider] ${index + 1}/${cases.size} ${result.id}: ${result.status}")
            result
        }
        return RawRun(
            provider = provider,
            cacheNamespace = "$provider-${UUID.randomUUID()}",
            startedAt = started,
            completedAt = Instant.now().toString(),
            cases = output,
        )
    }

    private suspend fun executeSonar(input: String, country: String, key: String): RawCase {
        val started = System.nanoTime()
        return capture(started) {
            OpenAiCompatibleClient().use { client ->
                val credential = AiRuntimeCredential.from(key)
                val provider = openRouterProvider(client, credential, country)
                val intent = LocalFoodIntentParser.parseOrNull(input) ?: provider.parseFood(input)
                provider.researchNutrition(intent)
            }
        }
    }

    private suspend fun executeExaGemini(
        input: String,
        country: String,
        geminiKey: String,
        exaKey: String,
    ): RawCase {
        val started = System.nanoTime()
        return capture(started) {
            ExaGeminiHttpClient().use { client ->
                val credential = AiRuntimeCredential.from(geminiKey)
                val config = AiProviderConfig(
                    kind = AiProviderKind.EXA_GEMINI,
                    endpoint = GEMINI_API_ENDPOINT,
                    model = System.getenv("GEMINI_MODEL") ?: DEFAULT_GEMINI_NUTRITION_MODEL,
                    timeoutMillis = 60_000,
                )
                val intent = LocalFoodIntentParser.parseOrNull(input) ?: run {
                    val raw = client.generateStructuredJson(
                        config = config,
                        credential = credential,
                        systemPrompt = "You are Nomi's structured multilingual food parser.",
                        userPrompt = AiPrompts.parseFood(input),
                    )
                    val parsed = client.json.decodeFromString<com.nomi.app.ai.model.ParsedFoodIntent>(
                        extractJsonDocument(raw),
                    )
                    AiResponseValidator.validate(
                        UserQuantityResolver.reconcileParsedIntent(input, parsed, country),
                    )
                }
                ExaGeminiNutritionProvider(
                    exaSearch = client,
                    geminiExtractor = client,
                    exaCredential = { AiRuntimeCredential.from(exaKey) },
                    geminiConfig = config,
                    geminiCredential = { credential },
                    localeCountryProvider = { country },
                ).researchNutrition(intent)
            }
        }
    }

    private fun openRouterProvider(
        client: OpenAiCompatibleClient,
        credential: AiRuntimeCredential,
        country: String,
    ): OpenAiCompatibleProviders {
        val parserConfig = AiProviderConfig(
            kind = AiProviderKind.OPEN_ROUTER,
            endpoint = OPENROUTER_ENDPOINT,
            model = System.getenv("OPENROUTER_PARSER_MODEL") ?: DEFAULT_OPENROUTER_MODEL,
            timeoutMillis = 60_000,
        )
        val sonarConfig = parserConfig.copy(
            model = System.getenv("SONAR_MODEL") ?: DEFAULT_OPENROUTER_RESEARCH_MODEL,
        )
        return OpenAiCompatibleProviders(
            client = client,
            parsingConfig = parserConfig,
            parsingCredential = { credential },
            nutritionConfig = sonarConfig,
            nutritionCredential = { credential },
            portionConfig = parserConfig,
            portionCredential = { credential },
            visionConfig = parserConfig,
            visionCredential = { credential },
            localeCountryProvider = { country },
        )
    }

    private suspend fun capture(started: Long, block: suspend () -> FoodAnalysis): RawCase =
        try {
            val analysis = block()
            RawCase(
                status = "success",
                latencyMillis = (System.nanoTime() - started) / 1_000_000,
                analysis = analysis,
            )
        } catch (error: Throwable) {
            val rejected = error is AiValidationException || error is IllegalArgumentException
            RawCase(
                status = if (rejected) "rejected" else "provider_failure",
                latencyMillis = (System.nanoTime() - started) / 1_000_000,
                errorType = error.javaClass.simpleName,
                errorMessage = error.message?.take(300),
            )
        }

    private fun repositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(5) {
            if (Files.exists(current.resolve("eval/eval_cases.json"))) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate eval/eval_cases.json")
    }

    private fun kotlinx.serialization.json.JsonObject.input() =
        getValue("input").jsonPrimitive.content
    private fun kotlinx.serialization.json.JsonObject.country() =
        getValue("locale").jsonPrimitive.content.substringAfterLast('-', "")
    private fun requiredEnv(name: String) =
        System.getenv(name)?.takeIf(String::isNotBlank) ?: error("Set $name")

    @Serializable
    private data class RawRun(
        val schemaVersion: String = "1.0.0",
        val latencyMeasurement: String = "end_to_end_v1",
        val provider: String,
        val cacheNamespace: String,
        val startedAt: String,
        val completedAt: String,
        val approximateApiCostUsd: Double? = null,
        val costNote: String =
            "Production provider responses do not expose token/search cost to this executor.",
        val cases: List<RawCase>,
    )

    @Serializable
    private data class RawCase(
        val id: String = "",
        val status: String,
        val latencyMillis: Long,
        val analysis: FoodAnalysis? = null,
        val errorType: String? = null,
        val errorMessage: String? = null,
    )

    private companion object {
        const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1"
    }
}
