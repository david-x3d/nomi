package com.nomi.app.ai.usage

import com.nomi.app.data.local.entity.AiUsageEventEntity
import java.util.Locale

enum class AiUsageResolutionKind {
    AI_CALL,
    CACHE_HIT,
    LOCAL_PARSE,
}

enum class AiCostSource {
    REPORTED,
    ESTIMATED,
    FREE,
}

enum class AiUsageRange(val label: String, val days: Long?) {
    SEVEN_DAYS("7D", 7L),
    THIRTY_DAYS("30D", 30L),
    ALL("All", null),
}

data class AiUsageSample(
    val pipeline: String,
    val resolutionKind: AiUsageResolutionKind,
    val providerId: String,
    val model: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val thoughtsTokens: Int = 0,
    val exaSearches: Int = 0,
    val reportedCostUsd: Double? = null,
)

data class AiModelUsageRow(
    val providerId: String,
    val model: String,
    val calls: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val thoughtsTokens: Long,
    val exaSearches: Int,
    val costUsd: Double,
    val costShare: Float,
)

data class AiTaskUsageRow(
    val pipeline: String,
    val calls: Int,
    val totalTokens: Long,
    val costUsd: Double,
    val share: Float,
)

data class AiUsageSummary(
    val totalEvents: Int = 0,
    val aiCalls: Int = 0,
    val cacheHits: Int = 0,
    val localParses: Int = 0,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val thoughtsTokens: Long = 0L,
    val exaSearches: Int = 0,
    val exaCostUsd: Double = 0.0,
    val estimatedCostUsd: Double = 0.0,
    val hasReportedCosts: Boolean = false,
    val hasEstimatedCosts: Boolean = false,
    val byModel: List<AiModelUsageRow> = emptyList(),
    val byTask: List<AiTaskUsageRow> = emptyList(),
) {
    val totalTokens: Long get() = promptTokens + completionTokens + thoughtsTokens
    val freeResolutions: Int get() = cacheHits + localParses
    val freeResolutionRate: Float
        get() = if (totalEvents == 0) 0f else (freeResolutions.toFloat() / totalEvents.toFloat()).coerceIn(0f, 1f)

    /**
     * Out-of-pocket spend after applying Exa's free starter credit pool.
     * When [exaFreeTierEnabled] is on and all-time Exa usage has not exhausted [exaFreeCreditUsd],
     * Exa searches inside this range are covered at $0 out-of-pocket.
     */
    fun outOfPocketCostUsd(
        exaFreeTierEnabled: Boolean,
        exaFreeCreditUsd: Double,
        allTimeExaCostUsd: Double,
    ): Double {
        if (!exaFreeTierEnabled || exaFreeCreditUsd <= 0.0 || exaCostUsd <= 0.0) {
            return estimatedCostUsd
        }
        val excessAllTime = (allTimeExaCostUsd - exaFreeCreditUsd).coerceAtLeast(0.0)
        val billedExaInRange = minOf(exaCostUsd, excessAllTime)
        val coveredExaInRange = (exaCostUsd - billedExaInRange).coerceAtLeast(0.0)
        return (estimatedCostUsd - coveredExaInRange).coerceAtLeast(0.0)
    }

    fun exaCoveredInRangeUsd(
        exaFreeTierEnabled: Boolean,
        exaFreeCreditUsd: Double,
        allTimeExaCostUsd: Double,
    ): Double = (estimatedCostUsd - outOfPocketCostUsd(exaFreeTierEnabled, exaFreeCreditUsd, allTimeExaCostUsd))
        .coerceAtLeast(0.0)
}

/**
 * Estimates USD cost for BYO-API-key calls when the provider does not return an exact bill amount.
 * Rates are per 1,000,000 tokens (USD) based on published standard tiers.
 */
object AiCostEstimator {
    const val EXA_SEARCH_DEFAULT_USD = 0.005

    private data class ModelRate(
        val inputPerMillionUsd: Double,
        val outputPerMillionUsd: Double,
    )

    fun estimate(sample: AiUsageSample): Pair<Double, AiCostSource> {
        if (sample.resolutionKind != AiUsageResolutionKind.AI_CALL) {
            return 0.0 to AiCostSource.FREE
        }
        val reported = sample.reportedCostUsd?.takeIf { it.isFinite() && it >= 0.0 }
        if (reported != null) {
            return reported to AiCostSource.REPORTED
        }
        val exaCost = sample.exaSearches.coerceAtLeast(0) * EXA_SEARCH_DEFAULT_USD
        val rate = rateFor(sample.providerId, sample.model)
        val inputCost = (sample.promptTokens.coerceAtLeast(0) / 1_000_000.0) * rate.inputPerMillionUsd
        val outputTokens = (sample.completionTokens + sample.thoughtsTokens).coerceAtLeast(0)
        val outputCost = (outputTokens / 1_000_000.0) * rate.outputPerMillionUsd
        return (exaCost + inputCost + outputCost) to AiCostSource.ESTIMATED
    }

    private fun rateFor(providerId: String, model: String): ModelRate {
        val m = model.trim().lowercase(Locale.ROOT)
        val p = providerId.trim().lowercase(Locale.ROOT)
        return when {
            "flash-lite" in m -> ModelRate(0.10, 0.40)
            "gemini-2.5-flash" in m || "gemini-3" in m && "flash" in m -> ModelRate(0.30, 2.50)
            "gemini-2.0-flash" in m || "gemini-1.5-flash" in m -> ModelRate(0.10, 0.40)
            "gemini" in m && "pro" in m -> ModelRate(1.25, 10.00)
            "gemini" in m -> ModelRate(0.25, 1.50)
            "gpt-4o-mini" in m || "gpt-4.1-mini" in m || "gpt-5-mini" in m || "luna" in m -> ModelRate(0.15, 0.60)
            "gpt-4.1-nano" in m || "gpt-5-nano" in m -> ModelRate(0.10, 0.40)
            "gpt-4o" in m || "gpt-4.1" in m || "gpt-5" in m -> ModelRate(2.50, 10.00)
            "sonar-pro" in m -> ModelRate(3.00, 15.00)
            "sonar" in m || "perplexity" in p -> ModelRate(1.00, 1.00)
            "claude" in m && "haiku" in m -> ModelRate(0.80, 4.00)
            "claude" in m && "sonnet" in m -> ModelRate(3.00, 15.00)
            "qwen" in m || "glm" in m || "deepseek" in m || "llama" in m -> ModelRate(0.20, 0.80)
            "exa" == p -> ModelRate(0.0, 0.0)
            else -> ModelRate(0.30, 1.20)
        }
    }

    fun summarize(events: List<AiUsageEventEntity>): AiUsageSummary {
        if (events.isEmpty()) return AiUsageSummary()
        var aiCalls = 0
        var cacheHits = 0
        var localParses = 0
        var promptTokens = 0L
        var completionTokens = 0L
        var thoughtsTokens = 0L
        var exaSearches = 0
        var exaCostUsd = 0.0
        var totalCost = 0.0
        var hasReported = false
        var hasEstimated = false

        val aiEvents = mutableListOf<AiUsageEventEntity>()
        for (event in events) {
            when (event.resolutionKind) {
                AiUsageResolutionKind.CACHE_HIT.name -> cacheHits++
                AiUsageResolutionKind.LOCAL_PARSE.name -> localParses++
                else -> {
                    aiCalls++
                    aiEvents += event
                }
            }
            promptTokens += event.promptTokens.coerceAtLeast(0)
            completionTokens += event.completionTokens.coerceAtLeast(0)
            thoughtsTokens += event.thoughtsTokens.coerceAtLeast(0)
            exaSearches += event.exaSearches.coerceAtLeast(0)
            if (event.providerId.equals("exa", ignoreCase = true) || event.exaSearches > 0) {
                exaCostUsd += event.estimatedCostUsd.coerceAtLeast(0.0)
            }
            totalCost += event.estimatedCostUsd.coerceAtLeast(0.0)
            when (event.costSource) {
                AiCostSource.REPORTED.name -> hasReported = true
                AiCostSource.ESTIMATED.name -> hasEstimated = true
            }
        }

        val byModel = aiEvents
            .groupBy { it.providerId to it.model }
            .map { (key, group) ->
                val cost = group.sumOf { it.estimatedCostUsd.coerceAtLeast(0.0) }
                AiModelUsageRow(
                    providerId = key.first,
                    model = key.second,
                    calls = group.size,
                    promptTokens = group.sumOf { it.promptTokens.toLong().coerceAtLeast(0L) },
                    completionTokens = group.sumOf { it.completionTokens.toLong().coerceAtLeast(0L) },
                    thoughtsTokens = group.sumOf { it.thoughtsTokens.toLong().coerceAtLeast(0L) },
                    exaSearches = group.sumOf { it.exaSearches.coerceAtLeast(0) },
                    costUsd = cost,
                    costShare = if (totalCost > 0.0) (cost / totalCost).toFloat().coerceIn(0f, 1f) else {
                        if (aiCalls > 0) (group.size.toFloat() / aiCalls.toFloat()).coerceIn(0f, 1f) else 0f
                    },
                )
            }
            .sortedWith(compareByDescending<AiModelUsageRow> { it.costUsd }.thenByDescending { it.calls })

        val byTask = aiEvents
            .groupBy { it.pipeline }
            .map { (pipeline, group) ->
                val cost = group.sumOf { it.estimatedCostUsd.coerceAtLeast(0.0) }
                val tokens = group.sumOf {
                    (it.promptTokens + it.completionTokens + it.thoughtsTokens).toLong().coerceAtLeast(0L)
                }
                AiTaskUsageRow(
                    pipeline = pipeline,
                    calls = group.size,
                    totalTokens = tokens,
                    costUsd = cost,
                    share = if (aiCalls > 0) (group.size.toFloat() / aiCalls.toFloat()).coerceIn(0f, 1f) else 0f,
                )
            }
            .sortedWith(compareByDescending<AiTaskUsageRow> { it.costUsd }.thenByDescending { it.calls })

        return AiUsageSummary(
            totalEvents = events.size,
            aiCalls = aiCalls,
            cacheHits = cacheHits,
            localParses = localParses,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            thoughtsTokens = thoughtsTokens,
            exaSearches = exaSearches,
            exaCostUsd = exaCostUsd,
            estimatedCostUsd = totalCost,
            hasReportedCosts = hasReported,
            hasEstimatedCosts = hasEstimated,
            byModel = byModel,
            byTask = byTask,
        )
    }

    fun formatUsd(amount: Double): String = when {
        amount <= 0.0 -> "$0.00"
        amount < 0.01 -> String.format(Locale.US, "$%.4f", amount)
        amount < 1.0 -> String.format(Locale.US, "$%.3f", amount)
        else -> String.format(Locale.US, "$%.2f", amount)
    }

    fun formatCompactTokens(tokens: Long): String = when {
        tokens >= 1_000_000L -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}
