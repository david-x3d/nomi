package com.nomi.app.ai.usage

import com.nomi.app.data.local.entity.AiUsageEventEntity
import com.nomi.app.data.repository.NomiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Always-on local usage & cost recorder. Persists token counts, Exa search counts, cache hits,
 * and local parser shortcuts without storing prompts or credentials.
 */
class AiUsageRecorder(
    private val repository: NomiRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun record(sample: AiUsageSample) {
        val (costUsd, costSource) = AiCostEstimator.estimate(sample)
        val entity = AiUsageEventEntity(
            pipeline = sample.pipeline,
            resolutionKind = sample.resolutionKind.name,
            providerId = sample.providerId,
            model = sample.model,
            promptTokens = sample.promptTokens.coerceAtLeast(0),
            completionTokens = sample.completionTokens.coerceAtLeast(0),
            thoughtsTokens = sample.thoughtsTokens.coerceAtLeast(0),
            exaSearches = sample.exaSearches.coerceAtLeast(0),
            estimatedCostUsd = costUsd,
            costSource = costSource.name,
            createdAtEpochMillis = clock(),
        )
        scope.launch {
            runCatching { repository.recordAiUsageEvent(entity) }
        }
    }

    fun recordCacheHit(pipeline: String, source: String) {
        record(
            AiUsageSample(
                pipeline = pipeline,
                resolutionKind = AiUsageResolutionKind.CACHE_HIT,
                providerId = "cache",
                model = source,
            ),
        )
    }

    fun recordLocalParse(pipeline: String, parserName: String) {
        record(
            AiUsageSample(
                pipeline = pipeline,
                resolutionKind = AiUsageResolutionKind.LOCAL_PARSE,
                providerId = "local",
                model = parserName,
            ),
        )
    }
}
