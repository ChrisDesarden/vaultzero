package com.vaultzero.app.data.crypto

import com.vaultzero.app.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoManagerTest {

    private val crypto = CryptoManager()

    @Test
    fun `deriveMasterKey with Argon2id returns 32 bytes`() {
        val salt = crypto.randomBytes(16)
        val key = crypto.deriveMasterKey("password".toCharArray(), salt, useArgon2 = true)
        assertEquals(32, key.size)
        crypto.wipe(key)
        crypto.wipe(salt)
    }

    @Test
    fun `deriveMasterKey with PBKDF2 returns 32 bytes`() {
        val salt = crypto.randomBytes(16)
        val key = crypto.deriveMasterKey("password".toCharArray(), salt, useArgon2 = false)
        assertEquals(32, key.size)
        crypto.wipe(key)
        crypto.wipe(salt)
    }

    @Test
    fun `same password and salt produce same key`() {
        val salt = crypto.randomBytes(16)
        val key1 = crypto.deriveMasterKey("testpass".toCharArray(), salt, useArgon2 = false)
        val key2 = crypto.deriveMasterKey("testpass".toCharArray(), salt, useArgon2 = false)
        assertTrue(key1.contentEquals(key2))
        crypto.wipe(key1)
        crypto.wipe(key2)
        crypto.wipe(salt)
    }

    @Test
    fun `different passwords produce different keys`() {
        val salt = crypto.randomBytes(16)
        val key1 = crypto.deriveMasterKey("pass1".toCharArray(), salt, useArgon2 = false)
        val key2 = crypto.deriveMasterKey("pass2".toCharArray(), salt, useArgon2 = false)
        assertTrue(!key1.contentEquals(key2))
        crypto.wipe(key1)
        crypto.wipe(key2)
        crypto.wipe(salt)
    }

    @Test
    fun `AES-GCM encrypt and decrypt roundtrip`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Hello, VaultZero!".toByteArray()
        val ciphertext = crypto.encrypt(key, plaintext)
        assertTrue(ciphertext.size > plaintext.size + 12) // nonce + tag overhead
        val decrypted = crypto.decrypt(key, ciphertext)
        assertArrayEquals(plaintext, decrypted)
        crypto.wipe(key)
        crypto.wipe(plaintext)
        crypto.wipe(ciphertext)
        crypto.wipe(decrypted)
    }

    @Test(expected = CryptoManager.VaultCryptoException::class)
    fun `decrypt with wrong key throws VaultCryptoException`() {
        val key = crypto.randomBytes(32)
        val wrongKey = crypto.randomBytes(32)
        val plaintext = "secret".toByteArray()
        val ciphertext = crypto.encrypt(key, plaintext)
        try {
            crypto.decrypt(wrongKey, ciphertext)
        } finally {
            crypto.wipe(key)
            crypto.wipe(wrongKey)
            crypto.wipe(plaintext)
            crypto.wipe(ciphertext)
        }
    }

    @Test
    fun `generatePassword produces correct length`() {
        val config = CryptoManager.PasswordConfig(length = 20)
        val password = crypto.generatePassword(config)
        assertEquals(20, password.size)
        crypto.wipe(password)
    }

    @Test
    fun `generatePassword with all sets includes at least one of each`() {
        val config = CryptoManager.PasswordConfig(
            length = 16, includeUppercase = true, includeLowercase = true,
            includeNumbers = true, includeSymbols = true
        )
        val password = crypto.generatePassword(config).concatToString()
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { it in "!@#\$%^\u0026*()-_=+[]{}|;:,.<>?" })
    }

    @Test
    fun `hmacSha256 produces deterministic output`() {
        val key = crypto.randomBytes(32)
        val data = "test".toByteArray()
        val h1 = crypto.hmacSha256(key, data)
        val h2 = crypto.hmacSha256(key, data)
        assertArrayEquals(h1, h2)
        assertEquals(32, h1.size) // SHA-256 output is 32 bytes
        crypto.wipe(key)
    }
}
