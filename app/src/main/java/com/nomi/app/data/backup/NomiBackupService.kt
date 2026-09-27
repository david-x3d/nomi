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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PushbackInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
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
@OptIn(ExperimentalSerializationApi::class)
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
        // Streamed and gzipped. Building one giant String first meant a large diary needed the
        // whole document as UTF-16 plus a byte array plus the object graph at once, which is an
        // OutOfMemoryError on a modest phone - and an Error, so nothing above could catch it.
        // The size ceiling is now enforced as the bytes go past, so an oversized diary fails
        // with a sentence instead of dying.
        val guard = ByteBudget(BackupValidator.MAX_BACKUP_FILE_BYTES)
        val counting = CountingOutputStream(guard)
        var written = 0
        GZIPOutputStream(counting, DEFAULT_BUFFER_SIZE).use { gzip ->
            try {
                // kotlinx.serialization's Json has no streaming encoder, so the document is still
                // built as one String - but it is written out through a Writer, which encodes to
                // UTF-8 in fixed-size chunks. The previous code additionally held a full
                // encodeToByteArray() copy, so this removes the doubling that turned a long diary
                // into an OutOfMemoryError on a modest phone.
                val text = json.encodeToString(envelope)
                OutputStreamWriter(gzip, Charsets.UTF_8).use { writer ->
                    writer.write(text)
                    writer.flush()
                }
                gzip.finish()
            } catch (error: SerializationException) {
                throw BackupFormatException("Nomi data could not be encoded safely", error)
            }
        }
        written = counting.count
        output.write(counting.buffer(), 0, written)
        output.flush()
        BackupExportResult(summary = summary, bytesWritten = written)
    }

    suspend fun exportToByteArray(): ByteArray = withContext(Dispatchers.IO) {
        ByteArrayOutputStream().use { buffer ->
            exportTo(buffer)
            buffer.toByteArray()
        }
    }

    /** Parses and validates an import for a confirmation preview without changing local state. */
    suspend fun inspect(input: InputStream): BackupInspection = withContext(Dispatchers.IO) {
        decodeAndValidate(input.readCapped())
    }

    /**
     * Replaces durable data only after a complete preflight. Room changes are transactional. Safe
     * preferences are applied first and rolled back if either preferences or the Room transaction
     * fails. Device-local API keys and the developer debug preference are never read or changed.
     */
    suspend fun importFrom(input: InputStream): BackupImportResult = withContext(Dispatchers.IO) {
        importValidated(decodeAndValidate(input.readCapped()))
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
        preferencesStore.setLanguageTag(
            value.languageTag ?: if (value.germanTranslationEnabled) "de" else "en",
        )
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
        // Absent in a backup written by an older build, in which case the default is what the
        // user already had; present in a current one, and previously dropped on restore.
        if (value.smartFallbackProvider.model.isNotBlank()) {
            preferencesStore.setProvider(
                ProviderPipeline.SMART_FALLBACK,
                value.smartFallbackProvider.toPreference(),
            )
        }
        preferencesStore.setReminders(value.reminders)
        preferencesStore.setAdjustTargetFromActivity(value.adjustTargetFromActivity)
        preferencesStore.setMicronutrients(value.micronutrients.toPreferences())
        preferencesStore.setCalorieEstimateBias(value.calorieEstimateBias)
        preferencesStore.setGoalsCardStyle(value.goalsCardStyle)
        preferencesStore.setOnboardingDraft(null)
        preferencesStore.markOnboardingCompleted(value.onboardingCompleted, clearDraft = true)
    }

    private suspend fun applyAllPreferences(value: AppPreferences) {
        preferencesStore.setAppearance(value.theme, value.dynamicColorEnabled)
        preferencesStore.setLanguageTag(value.languageTag)
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
        preferencesStore.setAiRequestTimeoutDisabled(value.aiRequestTimeoutDisabled)
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

/**
 * Reads a backup into memory, transparently inflating it when it is gzipped.
 *
 * A backup written by this version is gzip, because a JSON diary compresses roughly ten to one
 * and the storage ceiling is what stops a long diary from being exportable at all. A backup
 * written by any earlier version is plain JSON and is read exactly as before, so nothing that
 * already exists stops working: the choice is made by sniffing the two gzip magic bytes rather
 * than by anything recorded in the file.
 */
private fun InputStream.readCapped(): ByteArray {
    // PushbackInputStream is the tool for this: the two magic bytes are read, judged, and handed
    // back, so the same stream can then be inflated or read as plain text.
    val pushback = if (this is PushbackInputStream) this else PushbackInputStream(this, 2)
    val head = ByteArray(2)
    val read = pushback.read(head, 0, 2)
    val isGzip = read == 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
    if (read > 0) pushback.unread(head, 0, read)
    val source = if (isGzip) GZIPInputStream(pushback) else pushback
    val output = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE * 4)
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val n = source.read(chunk)
        if (n < 0) break
        if (n == 0) continue
        total += n
        if (total > BackupValidator.MAX_BACKUP_BYTES) {
            throw BackupFormatException(
                "Backup is larger than the ${BackupValidator.MAX_BACKUP_BYTES} byte limit",
            )
        }
        output.write(chunk, 0, n)
    }
    if (isGzip) source.close()
    return output.toByteArray()
}

/**
 * Refuses to buffer more than a fixed number of bytes.
 *
 * Fed to the encoder rather than checked afterwards, so an oversized diary is refused while it is
 * being written instead of after the whole thing has been held in memory.
 */
internal class ByteBudget(private val limit: Int) {
    private var used = 0

    fun take(count: Int) {
        used += count
        if (used > limit) {
            throw BackupFormatException("Backup is larger than the $limit byte limit")
        }
    }
}

/**
 * Collects the compressed bytes so a failed export never leaves a half-written file behind: the
 * caller only writes to the real destination once encoding has finished cleanly.
 */
private class CountingOutputStream(private val budget: ByteBudget) : OutputStream() {
    private var sink = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE * 4)

    val count: Int get() = sink.size()

    fun buffer(): ByteArray = sink.toByteArray()

    override fun write(b: Int) {
        budget.take(1)
        sink.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        budget.take(len)
        sink.write(b, off, len)
    }

    override fun flush() = Unit

    override fun close() = Unit
}
