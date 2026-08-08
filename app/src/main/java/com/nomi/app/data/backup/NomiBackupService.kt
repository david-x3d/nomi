package com.nomi.app.data.backup

import com.nomi.app.data.local.NomiDatabase
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.AppPreferencesStore
import com.nomi.app.data.preferences.ProviderPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.Clock

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupImportException(message: String, cause: Throwable) : IllegalStateException(message, cause)

data class BackupInspection(
    val envelope: BackupEnvelopeV1,
    val summary: BackupSummary,
    val encodedBytes: Int,
)

data class BackupExportResult(
    val summary: BackupSummary,
    val bytesWritten: Int,
)

data class BackupImportResult(
    val summary: BackupSummary,
    val bytesRead: Int,
)

/**
 * Versioned JSON export/import boundary. Streams are never closed by this class because ownership
 * remains with the Storage Access Framework caller.
 */
class NomiBackupService(
    private val database: NomiDatabase,
    private val preferencesStore: AppPreferencesStore,
    private val appVersionName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = strictBackupJson(),
) {
    suspend fun exportTo(output: OutputStream): BackupExportResult = withContext(Dispatchers.IO) {
        val preferences = preferencesStore.preferences.first().toBackup()
        val envelope = BackupEnvelopeV1(
            exportedAtEpochMillis = clock.millis(),
            appVersionName = appVersionName,
            payload = database.readBackupPayload(preferences),
        )
        val summary = BackupValidator.validate(envelope)
        val bytes = try {
            json.encodeToString(envelope).encodeToByteArray()
        } catch (error: SerializationException) {
            throw BackupFormatException("Nomi data could not be encoded safely", error)
        }
        if (bytes.size > BackupValidator.MAX_BACKUP_BYTES) {
            throw BackupFormatException(
                "Backup is ${bytes.size} bytes; the limit is ${BackupValidator.MAX_BACKUP_BYTES}",
            )
        }
        output.write(bytes)
        output.flush()
        BackupExportResult(summary = summary, bytesWritten = bytes.size)
    }

    suspend fun exportToByteArray(): ByteArray = withContext(Dispatchers.IO) {
        ByteArrayOutputStream().use { buffer ->
            exportTo(buffer)
            buffer.toByteArray()
        }
    }

    /** Parses and validates an import for a confirmation preview without changing local state. */
    suspend fun inspect(input: InputStream): BackupInspection = withContext(Dispatchers.IO) {
        val bytes = input.readCapped(BackupValidator.MAX_BACKUP_BYTES)
        decodeAndValidate(bytes)
    }

    /**
     * Replaces durable data only after a complete preflight. Room changes are transactional. Safe
     * preferences are applied first and rolled back if either preferences or the Room transaction
     * fails. Device-local API keys and the developer debug preference are never read or changed.
     */
    suspend fun importFrom(input: InputStream): BackupImportResult = withContext(Dispatchers.IO) {
        val bytes = input.readCapped(BackupValidator.MAX_BACKUP_BYTES)
        importValidated(decodeAndValidate(bytes))
    }

    suspend fun importValidated(inspection: BackupInspection): BackupImportResult =
        withContext(Dispatchers.IO) {
            if (inspection.encodedBytes !in 1..BackupValidator.MAX_BACKUP_BYTES) {
                throw BackupFormatException("Backup size is outside the supported range")
            }
            // Revalidate the caller-supplied object; do not trust an inspection retained in memory.
            val summary = BackupValidator.validate(inspection.envelope)
            val previousPreferences = preferencesStore.preferences.first()
            try {
                applyBackupPreferences(inspection.envelope.payload.preferences)
                database.replaceWith(inspection.envelope.payload)
            } catch (error: Exception) {
                val rollbackFailure = withContext(NonCancellable) {
                    runCatching { applyAllPreferences(previousPreferences) }.exceptionOrNull()
                }
                rollbackFailure?.let(error::addSuppressed)
                if (error is CancellationException) throw error
                throw BackupImportException(
                    "Nomi could not restore this backup; database changes were rolled back",
                    error,
                )
            }
            BackupImportResult(summary = summary, bytesRead = inspection.encodedBytes)
        }

    private fun decodeAndValidate(bytes: ByteArray): BackupInspection {
        if (bytes.isEmpty()) throw BackupFormatException("Backup file is empty")
        val encoded = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw BackupFormatException("Backup is not valid UTF-8 text", error)
        }
        val root = try {
            json.parseToJsonElement(encoded).jsonObject
        } catch (error: Exception) {
            throw BackupFormatException("Backup is not valid JSON", error)
        }
        val format = runCatching { root["format"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val version = runCatching { root["schemaVersion"]?.jsonPrimitive?.intOrNull }.getOrNull()
        if (format != BackupEnvelopeV1.FORMAT) {
            throw BackupFormatException("File is not a Nomi backup")
        }
        if (version != BackupEnvelopeV1.SCHEMA_VERSION) {
            throw BackupFormatException("Backup version ${version ?: "missing"} is not supported")
        }
        val envelope = try {
            json.decodeFromString<BackupEnvelopeV1>(encoded)
        } catch (error: Exception) {
            throw BackupFormatException("Backup structure is incomplete or invalid", error)
        }
        return BackupInspection(
            envelope = envelope,
            summary = BackupValidator.validate(envelope),
            encodedBytes = bytes.size,
        )
    }

    private suspend fun applyBackupPreferences(value: BackupPreferencesV1) {
        preferencesStore.setAppearance(value.theme, value.dynamicColorEnabled)
        preferencesStore.setGermanTranslationEnabled(value.germanTranslationEnabled)
        preferencesStore.setUnits(value.weightUnit, value.heightUnit)
        preferencesStore.setProvider(
            ProviderPipeline.FOOD_RESEARCH,
            value.foodResearchProvider.toPreference(),
        )
        preferencesStore.setProvider(
            ProviderPipeline.FOOD_INTERPRETATION,
            value.foodInterpretationProvider.toPreference(),
        )
        preferencesStore.setProvider(
            ProviderPipeline.PORTION_CHANGE,
            value.portionChangeProvider.toPreference(),
        )
        preferencesStore.setProvider(ProviderPipeline.VISION, value.visionProvider.toPreference())
        preferencesStore.setReminders(value.reminders)
        preferencesStore.setAdjustTargetFromActivity(value.adjustTargetFromActivity)
        preferencesStore.setOnboardingDraft(null)
        preferencesStore.markOnboardingCompleted(value.onboardingCompleted, clearDraft = true)
    }

    private suspend fun applyAllPreferences(value: AppPreferences) {
        preferencesStore.setAppearance(value.theme, value.dynamicColorEnabled)
        preferencesStore.setGermanTranslationEnabled(value.germanTranslationEnabled)
        preferencesStore.setUnits(value.weightUnit, value.heightUnit)
        preferencesStore.setProvider(ProviderPipeline.FOOD_RESEARCH, value.foodResearchProvider)
        preferencesStore.setProvider(
            ProviderPipeline.FOOD_INTERPRETATION,
            value.foodInterpretationProvider,
        )
        preferencesStore.setProvider(ProviderPipeline.PORTION_CHANGE, value.portionChangeProvider)
        preferencesStore.setProvider(ProviderPipeline.VISION, value.visionProvider)
        preferencesStore.setProvider(ProviderPipeline.SMART_FALLBACK, value.smartFallbackProvider)
        preferencesStore.setReminders(value.reminders)
        preferencesStore.setAdjustTargetFromActivity(value.adjustTargetFromActivity)
        preferencesStore.setAiDebugEnabled(value.aiDebugEnabled)
        preferencesStore.setOnboardingDraft(value.onboardingDraft)
        preferencesStore.markOnboardingCompleted(value.onboardingCompleted, clearDraft = false)
    }

    companion object {
        fun strictBackupJson(): Json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
            allowSpecialFloatingPointValues = false
            prettyPrint = true
        }
    }
}

private fun InputStream.readCapped(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) {
            throw BackupFormatException("Backup is larger than the $maxBytes byte limit")
        }
        output.write(chunk, 0, read)
    }
    return output.toByteArray()
}
