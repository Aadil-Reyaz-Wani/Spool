package com.aadil.spool.core.network

import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class SessionCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun encryptsWithFreshNonceAndAuthenticatesCiphertext() {
        val plain = "refresh-token-and-email".toByteArray()
        val first = SessionCipher.encrypt(key, plain)
        val second = SessionCipher.encrypt(key, plain)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("refresh-token"))
        assertContentEquals(plain, SessionCipher.decrypt(key, first))
        assertContentEquals(plain, SessionCipher.decrypt(key, second))
        val tampered = first.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        assertFails { SessionCipher.decrypt(key, tampered) }
        assertFails { SessionCipher.decrypt(SecretKeySpec(ByteArray(32) { 99 }, "AES"), first) }
        assertFails { SessionCipher.decrypt(key, byteArrayOf(1, 2)) }
        assertFails { SessionCipher.decrypt(key, first.copyOf().apply { this[0] = 2 }) }
    }
}
