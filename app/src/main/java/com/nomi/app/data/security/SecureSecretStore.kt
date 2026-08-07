package com.nomi.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets are only made available inside [useSecret], whose temporary character buffer is wiped
 * immediately after the callback returns. Callers must never log or persist that buffer.
 */
interface SecureSecretStore {
    suspend fun put(secretId: String, secret: CharArray)
    suspend fun contains(secretId: String): Boolean
    suspend fun <T> useSecret(secretId: String, block: suspend (CharArray) -> T): T?
    suspend fun delete(secretId: String): Boolean
    suspend fun clear()
}

class SecretUnavailableException(
    message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

/**
 * AES-GCM storage backed by a non-exportable Android Keystore key. Ciphertext lives under
 * [Context.getNoBackupFilesDir], so neither ciphertext nor key material enters Android backup.
 */
class AndroidKeystoreSecureSecretStore(
    context: Context,
) : SecureSecretStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME)
    private val operationMutex = Mutex()
    private val keyLock = Any()

    override suspend fun put(secretId: String, secret: CharArray) {
        validateId(secretId)
        require(secret.isNotEmpty()) { "A secret cannot be empty" }
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                val encoded = encode(secret)
                val aad = secretId.toByteArray(StandardCharsets.UTF_8)
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                    cipher.updateAAD(aad)
                    val ciphertext = cipher.doFinal(encoded)
                    try {
                        writeAtomically(fileFor(secretId), cipher.iv, ciphertext)
                    } finally {
                        ciphertext.fill(0)
                    }
                } catch (error: GeneralSecurityException) {
                    throw SecretUnavailableException("Secure storage encryption failed", error)
                } finally {
                    encoded.fill(0)
                    aad.fill(0)
                }
            }
        }
    }

    override suspend fun contains(secretId: String): Boolean {
        validateId(secretId)
        return operationMutex.withLock {
            withContext(Dispatchers.IO) { fileFor(secretId).isFile }
        }
    }

    override suspend fun <T> useSecret(
        secretId: String,
        block: suspend (CharArray) -> T,
    ): T? {
        validateId(secretId)
        val cleartext = operationMutex.withLock {
            withContext(Dispatchers.IO) { decrypt(secretId) }
        } ?: return null
        return try {
            block(cleartext)
        } finally {
            cleartext.fill('\u0000')
        }
    }

    override suspend fun delete(secretId: String): Boolean {
        validateId(secretId)
        return operationMutex.withLock {
            withContext(Dispatchers.IO) { Files.deleteIfExists(fileFor(secretId).toPath()) }
        }
    }

    override suspend fun clear() {
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!directory.isDirectory) return@withContext
                Files.newDirectoryStream(directory.toPath(), "*$FILE_SUFFIX").use { paths ->
                    paths.forEach { path ->
                        if (Files.isRegularFile(path)) Files.deleteIfExists(path)
                    }
                }
            }
        }
    }

    private fun decrypt(secretId: String): CharArray? {
        val file = fileFor(secretId)
        if (!file.isFile) return null
        val payload = Files.readAllBytes(file.toPath())
        val aad = secretId.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        try {
            val buffer = ByteBuffer.wrap(payload)
            checkPayload(buffer)
            val ivLength = buffer.int
            if (ivLength !in MIN_IV_BYTES..MAX_IV_BYTES || buffer.remaining() <= ivLength) {
                throw SecretUnavailableException("Secure storage payload is invalid")
            }
            val iv = ByteArray(ivLength)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(aad)
                plaintext = cipher.doFinal(ciphertext)
                return decode(checkNotNull(plaintext))
            } catch (error: AEADBadTagException) {
                throw SecretUnavailableException("Secure storage authentication failed", error)
            } catch (error: GeneralSecurityException) {
                throw SecretUnavailableException("Secure storage decryption failed", error)
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
        } finally {
            plaintext?.fill(0)
            payload.fill(0)
            aad.fill(0)
        }
    }

    private fun writeAtomically(target: File, iv: ByteArray, ciphertext: ByteArray) {
        Files.createDirectories(directory.toPath())
        val payload = ByteBuffer.allocate(MAGIC.size + Int.SIZE_BYTES + iv.size + ciphertext.size)
            .put(MAGIC)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
        val temporary = File.createTempFile("nomi-secret-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            payload.fill(0)
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private fun existingKey(): SecretKey = synchronized(keyLock) {
        val keyStore = loadKeyStore()
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw SecretUnavailableException("Secure storage key is unavailable")
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }

    private fun fileFor(secretId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secretId.toByteArray(StandardCharsets.UTF_8))
        val fileName = buildString(digest.size * 2 + FILE_SUFFIX.length) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
            append(FILE_SUFFIX)
        }
        digest.fill(0)
        return File(directory, fileName)
    }

    private fun checkPayload(buffer: ByteBuffer) {
        if (buffer.remaining() < MAGIC.size + Int.SIZE_BYTES) {
            throw SecretUnavailableException("Secure storage payload is truncated")
        }
        MAGIC.forEach { expected ->
            if (buffer.get() != expected) {
                throw SecretUnavailableException("Secure storage payload is invalid")
            }
        }
    }

    private fun encode(chars: CharArray): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        return ByteArray(byteBuffer.remaining()).also(byteBuffer::get)
    }

    private fun decode(bytes: ByteArray): CharArray {
        val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        return CharArray(charBuffer.remaining()).also(charBuffer::get)
    }

    private fun validateId(secretId: String) {
        require(secretId.isNotBlank()) { "A secret id cannot be blank" }
        require(secretId.length <= MAX_SECRET_ID_LENGTH) { "A secret id is too long" }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.nomi.app.secure-secrets.aes-gcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DIRECTORY_NAME = "nomi_secure_secrets"
        const val FILE_SUFFIX = ".secret"
        const val KEY_SIZE_BITS = 256
        const val TAG_BITS = 128
        const val MIN_IV_BYTES = 12
        const val MAX_IV_BYTES = 32
        const val MAX_SECRET_ID_LENGTH = 256
        val MAGIC = byteArrayOf(0x4e, 0x4f, 0x4d, 0x01)
        const val HEX = "0123456789abcdef"
    }
}
