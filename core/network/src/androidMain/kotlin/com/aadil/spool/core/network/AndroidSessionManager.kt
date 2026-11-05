package com.aadil.spool.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM key stays in Android Keystore; ciphertext is excluded from backup/transfer. */
internal class AndroidSessionManager(context: Context, origin: String) : SessionManager {
    private val project = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val alias = "spool.auth.$project"
    private val file = AtomicFile(File(context.noBackupFilesDir, "$alias.session"))
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = SessionCipher.encrypt(key(), json.encodeToString(session).toByteArray(Charsets.UTF_8))
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val plain = SessionCipher.decrypt(key(), file.readFully())
                json.decodeFromString<UserSession>(plain.toString(Charsets.UTF_8))
            } catch (_: Exception) {
                // Invalidated keys or corrupt storage require sign-in again, never a DB reset.
                file.delete()
                null
            }
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }
}

internal object SessionCipher {
    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(key: SecretKey, encrypted: ByteArray): ByteArray {
        require(encrypted.size >= 29 && encrypted[0] == 1.toByte())
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
            doFinal(encrypted.copyOfRange(13, encrypted.size))
        }
    }
}
