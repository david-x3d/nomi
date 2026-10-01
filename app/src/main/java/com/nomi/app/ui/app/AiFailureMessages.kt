package com.nomi.app.ui.app

import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.NutritionFailureReason
import com.nomi.app.ai.validation.NutritionResearchException
import com.nomi.app.data.remote.ai.ProviderTemporarilyUnavailableException
import com.nomi.app.data.security.SecretUnavailableException
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/*
 * Turns a provider or validation failure into the sentence the user reads. The sentences are the
 * English catalogue keys; the screen translates them where they are shown.
 */

internal fun Throwable.safeProviderSettingsMessage(): String = when {
    causeChain().any { it is SecretUnavailableException } ->
        "Nomi couldn't access secure API-key storage. Re-enter the key and try again."
    message?.contains("Configure", ignoreCase = true) == true -> message.orEmpty()
    message?.contains("endpoint", ignoreCase = true) == true ->
        "Enter a valid HTTPS API endpoint."
    message?.contains("model", ignoreCase = true) == true ->
        "Enter a model name."
    else -> "Nomi couldn't update this provider. Try again."
}

internal fun Throwable.safeAiMessage(): String {
    if (this is AiValidationException && message?.contains("not compatible", ignoreCase = true) == true) {
        return "Nomi couldn't match that source serving to your amount. Try g, ml, EL, or TL."
    }
    if (this is AiValidationException) {
        return message ?: "The serving amount could not be validated."
    }
    if (message?.contains("API key", ignoreCase = true) == true ||
        message?.contains("Configure", ignoreCase = true) == true
    ) {
        return message.orEmpty()
    }
    return safeProviderFailureMessage()
        ?: "Nomi couldn't finish that analysis. Try again or enter the food manually."
}

internal fun Throwable.safeProviderConnectionMessage(): String =
    safeProviderFailureMessage()
        ?: message?.takeIf {
            it.contains("API key", ignoreCase = true) ||
                it.contains("Configure", ignoreCase = true)
        }
        ?: "Connection failed. Check the API key, HTTPS endpoint, model, and network connection."

internal fun Throwable.safeProviderFailureMessage(): String? {
    val causes = causeChain()
    if (causes.any { it is SecretUnavailableException }) {
        return "Nomi couldn't read the stored API key. Remove it in Settings and enter it again."
    }
    causes.filterIsInstance<ProviderTemporarilyUnavailableException>().firstOrNull()?.let { error ->
        // A retried 429 is a quota problem, not an outage. Waiting is the answer to one and
        // reporting an outage is the answer to the other, so they do not share a sentence.
        if (error.statusCode == HTTP_TOO_MANY_REQUESTS) {
            return "${error.providerName} rejected the request for exceeding its rate limit, " +
                "even after automatic retries. Wait a moment and try again."
        }
        return "${error.providerName} is temporarily unavailable (HTTP ${error.statusCode}) " +
            "after automatic retries. Try again shortly."
    }
    val responseError = causes.filterIsInstance<ResponseException>().firstOrNull()
    if (responseError != null) {
        val status = responseError.response.status.value
        // Google answers a wrong key with 400 rather than 401 and says so only in the body,
        // which Ktor quotes in the exception. Left to the 400 wording below, a mistyped Gemini
        // key was reported as a model that cannot search.
        if (status == 400 &&
            responseError.message.orEmpty().contains("valid API key", ignoreCase = true)
        ) {
            return "The provider rejected that API key. Check it in Settings."
        }
        return when (status) {
            401 -> "The provider rejected that API key. Check it in Settings."
            402 -> "The provider account is out of credit. Top it up or switch the provider in Settings."
            403 -> "The provider denied access. Check the API key and model access in Settings."
            404 -> "The provider endpoint or model variant was not found. If the model ends " +
                "in :free, OpenRouter may not currently offer a free endpoint for it. Check " +
                "the exact model ID in Settings."
            408 -> "The provider took too long. Try again."
            429 -> "The provider rate limit was reached. Wait a moment and try again."
            in 400..499 ->
                "The provider rejected the request (HTTP $status). The selected model may not " +
                    "support live web search. For OpenAI pick a search model such as " +
                    "gpt-4o-search-preview, or use Perplexity/OpenRouter for Food research."
            else -> "The provider is temporarily unavailable (HTTP $status). Try again."
        }
    }
    if (causes.any {
            it is HttpRequestTimeoutException || it is ConnectTimeoutException ||
                it is SocketTimeoutException
        }
    ) {
        return "The provider took too long. Try again."
    }
    // A base URL missing its version segment still answers 200, but with the provider's own
    // web page. That arrives as a content type Ktor cannot read as a completion, and blaming
    // the model would send someone looking in the wrong place.
    if (causes.any { it is NoTransformationFoundException } ||
        causeMessageContains("No transformation found")
    ) {
        return "That endpoint answered with a web page instead of an API response. Check the " +
            "base URL in Settings — an OpenAI-compatible endpoint usually ends in /v1."
    }
    if (causes.any { it is SerializationException } ||
        causeMessageContains("JSON", "serialize", "deserialize", "structured content")
    ) {
        return "The provider returned a response Nomi couldn't read. Check the selected model in Settings."
    }
    if (causes.any {
            it is UnknownHostException || it is ConnectException || it is IOException
        }
    ) {
        return "Nomi couldn't reach the provider. Check the internet connection and endpoint."
    }
    return when {
        causeMessageContains("401") -> "The provider rejected that API key. Check it in Settings."
        causeMessageContains("403") ->
            "The provider denied access. Check the API key and model access in Settings."
        causeMessageContains("404") ->
            "The provider endpoint or model was not found. Check Settings."
        causeMessageContains("429", "rate limit") ->
            "The provider rate limit was reached. Wait a moment and try again."
        causeMessageContains("timeout", "timed out") -> "The provider took too long. Try again."
        else -> null
    }
}

/**
 * The user-facing sentence for a nutrition failure that belongs to one named item.
 *
 * Each cause needs a different answer from the user, so each gets its own sentence with the food
 * in it. Transport causes return null: they already have wording that names the provider and the
 * status code, and the food name adds nothing to a rate limit.
 */
internal fun researchFailureTemplate(reason: NutritionFailureReason): String? = when (reason) {
    NutritionFailureReason.MISSING_PORTION_WEIGHT ->
        "Nomi found nutrition for \"{0}\" but could not resolve that serving. Try again or add product details."
    NutritionFailureReason.SOURCE_IDENTITY_MISMATCH ->
        "Nomi only found sources for a different product than \"{0}\". Check the name, or add " +
            "the brand."
    NutritionFailureReason.NO_SUITABLE_SOURCE ->
        "Nomi found no nutrition source for \"{0}\". Try again or describe it more precisely."
    NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES ->
        "Nomi couldn't confirm the nutrition numbers for \"{0}\". Try again or edit the entry."
    NutritionFailureReason.INVALID_NUTRITION_BASIS ->
        "The nutrition Nomi found for \"{0}\" is given for a serving it cannot convert to your " +
            "amount. Try again or add product details."
    NutritionFailureReason.PARSING_FAILURE ->
        "Nomi couldn't read the nutrition answer for \"{0}\". Try again."
    NutritionFailureReason.PROVIDER_TIMEOUT,
    NutritionFailureReason.PROVIDER_RATE_LIMITED,
    NutritionFailureReason.MODEL_UNAVAILABLE,
    NutritionFailureReason.PROVIDER_UNREACHABLE,
    -> null
}

/**
 * The user-facing sentence for a failure that belongs to the whole request rather than to one
 * food: retrieval came back with nothing usable, or the answer did not match the contract.
 */
private fun requestLevelFailureMessage(reason: NutritionFailureReason): String? = when (reason) {
    NutritionFailureReason.NO_SUITABLE_SOURCE ->
        "Nomi found no usable nutrition sources for that entry. Try again in a moment."
    NutritionFailureReason.PARSING_FAILURE ->
        "Nomi couldn't read the nutrition answer for that entry. Try again."
    NutritionFailureReason.SOURCE_IDENTITY_MISMATCH,
    NutritionFailureReason.UNSUPPORTED_NUTRITION_VALUES,
    NutritionFailureReason.INVALID_NUTRITION_BASIS,
    NutritionFailureReason.MISSING_PORTION_WEIGHT,
    ->
        // These always belong to a named item; reaching here means the name was lost, so the
        // old shared sentence is the honest answer rather than a guess about which food it was.
        "Nomi couldn't verify nutrition for every product. Try again or edit the entry."
    // Transport causes keep their own provider wording.
    NutritionFailureReason.PROVIDER_TIMEOUT,
    NutritionFailureReason.PROVIDER_RATE_LIMITED,
    NutritionFailureReason.MODEL_UNAVAILABLE,
    NutritionFailureReason.PROVIDER_UNREACHABLE,
    -> null
}

/**
 * Turns a research failure into something the user can act on.
 *
 * Raw source-ID language is not UI, but neither is one sentence for every cause. A typed
 * [NutritionResearchException] names the item that failed and why, so the message can say
 * which food it was and what would fix it. Everything else keeps its existing wording,
 * including the provider-level timeout, rate-limit and model errors that
 * [safeAiMessage] already tells apart.
 */
internal fun researchFailureMessage(error: Throwable, language: NomiLanguage): String {
    val research = error.causeChain().filterIsInstance<NutritionResearchException>().firstOrNull()
    if (research != null) {
        val food = research.itemName?.trim()?.takeIf(String::isNotBlank)
        val template = if (food == null) {
            // Retrieval and contract failures belong to the whole request, not to one food.
            requestLevelFailureMessage(research.reason)
        } else {
            researchFailureTemplate(research.reason)
        }
        return when {
            template == null ->
                // Transport failures already carry their own distinct wording, which names
                // the provider and the status code; a food name adds nothing to those.
                NomiTranslations.translate(error.safeAiMessage(), language)
            food == null -> NomiTranslations.translate(template, language)
            else -> NomiTranslations.format(template, language, food)
        }
    }
    val technicalEvidenceFailure = error is AiValidationException && listOf(
        "Exa source",
        "nutrition evidence",
        "support Gemini",
        "not compatible",
    ).any { marker -> error.message?.contains(marker, ignoreCase = true) == true }
    if (!technicalEvidenceFailure) return NomiTranslations.translate(error.safeAiMessage(), language)
    return NomiTranslations.translate(
        "Nomi couldn't verify nutrition for every product. Try again or edit the entry.",
        language,
    )
}

/** The typed research detail behind a failure, for the debug log rather than the screen. */
internal fun Throwable.researchFailureDetail(): String? =
    causeChain().filterIsInstance<NutritionResearchException>().firstOrNull()?.message

private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * The typed cause behind a nutrition failure, whatever layer raised it.
 *
 * Validation already carries its own [NutritionResearchException]; transport failures arrive as
 * Ktor and IO exceptions and are classified here, so a debug event records "PROVIDER_TIMEOUT"
 * rather than a class name that says nothing about what went wrong.
 */
internal fun Throwable.nutritionFailureReason(): NutritionFailureReason? {
    val causes = causeChain()
    causes.filterIsInstance<NutritionResearchException>().firstOrNull()?.let { return it.reason }
    causes.filterIsInstance<ProviderTemporarilyUnavailableException>().firstOrNull()?.let { error ->
        return if (error.statusCode == HTTP_TOO_MANY_REQUESTS) {
            NutritionFailureReason.PROVIDER_RATE_LIMITED
        } else {
            NutritionFailureReason.PROVIDER_UNREACHABLE
        }
    }
    causes.filterIsInstance<ResponseException>().firstOrNull()?.let { error ->
        return when (error.response.status.value) {
            HTTP_TOO_MANY_REQUESTS -> NutritionFailureReason.PROVIDER_RATE_LIMITED
            HttpStatusCode.RequestTimeout.value -> NutritionFailureReason.PROVIDER_TIMEOUT
            HttpStatusCode.NotFound.value -> NutritionFailureReason.MODEL_UNAVAILABLE
            else -> NutritionFailureReason.PROVIDER_UNREACHABLE
        }
    }
    if (causes.any {
            it is HttpRequestTimeoutException || it is ConnectTimeoutException ||
                it is SocketTimeoutException
        } || causeMessageContains("timeout", "timed out")
    ) {
        return NutritionFailureReason.PROVIDER_TIMEOUT
    }
    if (causes.any { it is SerializationException } ||
        causes.any { it is NoTransformationFoundException }
    ) {
        return NutritionFailureReason.PARSING_FAILURE
    }
    if (causes.any { it is UnknownHostException || it is ConnectException || it is IOException }) {
        return NutritionFailureReason.PROVIDER_UNREACHABLE
    }
    return null
}

private fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this) { it.cause }.take(8).toList()

private fun Throwable.causeMessageContains(vararg values: String): Boolean =
    causeChain().any { error ->
        values.any { value -> error.message?.contains(value, ignoreCase = true) == true }
    }
