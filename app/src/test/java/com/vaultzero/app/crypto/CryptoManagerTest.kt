package com.vaultzero.app.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CryptoManager].
 *
 * These are pure JVM tests with no Android dependencies.
 * They verify:
 *  - Key derivation consistency (same password + salt = same key)
 *  - Key derivation uniqueness (different salts produce different keys)
 *  - AES-GCM round-trip encryption/decryption
 *  - Password generator properties (length, character sets)
 *  - Secure wipe effectiveness (best-effort)
 */
class CryptoManagerTest {

    private lateinit var crypto: CryptoManager

    @Before
    fun setUp() {
        crypto = CryptoManager()
    }

    @Test
    fun `deriveMasterKey same input produces same key`() {
        val password = "correct horse battery staple".toCharArray()
        val salt = crypto.randomBytes(16)

        val key1 = crypto.deriveMasterKey(password, salt, useArgon2 = false) // PBKDF2 for speed
        val key2 = crypto.deriveMasterKey(password, salt, useArgon2 = false)

        assertArrayEquals(key1, key2)
        crypto.wipe(key1)
        crypto.wipe(key2)
        crypto.wipe(password)
    }

    @Test
    fun `deriveMasterKey different salt produces different key`() {
        val password = "correct horse battery staple".toCharArray()
        val salt1 = crypto.randomBytes(16)
        val salt2 = crypto.randomBytes(16)

        val key1 = crypto.deriveMasterKey(password, salt1, useArgon2 = false)
        val key2 = crypto.deriveMasterKey(password, salt2, useArgon2 = false)

        assertFalse(key1.contentEquals(key2))
        crypto.wipe(key1)
        crypto.wipe(key2)
        crypto.wipe(password)
    }

    @Test
    fun `deriveMasterKey with keyfile produces different key than without`() {
        val password = "master-pass".toCharArray()
        val salt = crypto.randomBytes(16)
        val keyfile = "keyfile-contents-123".toByteArray()

        val keyNoFile = crypto.deriveMasterKey(password, salt, keyfileBytes = null, useArgon2 = false)
        val keyWithFile = crypto.deriveMasterKey(password, salt, keyfileBytes = keyfile, useArgon2 = false)

        assertFalse(keyNoFile.contentEquals(keyWithFile))
        crypto.wipe(keyNoFile)
        crypto.wipe(keyWithFile)
        crypto.wipe(password)
        crypto.wipe(keyfile)
    }

    @Test
    fun `AES GCM encrypt decrypt round trip`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Secret message for VaultZero".toByteArray()
        val aad = byteArrayOf(0x01)

        val ciphertext = crypto.encrypt(key, plaintext, aad)
        val decrypted = crypto.decrypt(key, ciphertext, aad)

        assertArrayEquals(plaintext, decrypted)
        crypto.wipe(key)
        crypto.wipe(plaintext)
        crypto.wipe(decrypted)
    }

    @Test(expected = CryptoManager.VaultCryptoException::class)
    fun `AES GCM decrypt with wrong key throws`() {
        val key = crypto.randomBytes(32)
        val wrongKey = crypto.randomBytes(32)
        val plaintext = "data".toByteArray()

        val ciphertext = crypto.encrypt(key, plaintext)
        try {
            crypto.decrypt(wrongKey, ciphertext)
        } finally {
            crypto.wipe(key)
            crypto.wipe(wrongKey)
        }
    }

    @Test(expected = CryptoManager.VaultCryptoException::class)
    fun `AES GCM decrypt with tampered ciphertext throws`() {
        val key = crypto.randomBytes(32)
        val plaintext = "data".toByteArray()

        val ciphertext = crypto.encrypt(key, plaintext)
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1] + 1).toByte()

        try {
            crypto.decrypt(key, ciphertext)
        } finally {
            crypto.wipe(key)
        }
    }

    @Test
    fun `encrypt password and decrypt password round trip`() {
        val key = crypto.randomBytes(32)
        val password = "MyP@ssw0rd!".toCharArray()

        val ciphertext = crypto.encryptPassword(key, password)
        val decrypted = crypto.decryptPassword(key, ciphertext)

        assertEquals(password.concatToString(), decrypted.concatToString())
        crypto.wipe(key)
        crypto.wipe(password)
        crypto.wipe(decrypted)
    }

    @Test
    fun `password generator produces correct length`() {
        val config = CryptoManager.PasswordConfig(length = 24)
        val password = crypto.generatePassword(config)

        assertEquals(24, password.size)
        crypto.wipe(password)
    }

    @Test
    fun `password generator includes all requested character sets`() {
        val config = CryptoManager.PasswordConfig(
            length = 32,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val password = crypto.generatePassword(config)
        val text = password.concatToString()

        assertTrue("Should contain uppercase", text.any { it.isUpperCase() })
        assertTrue("Should contain lowercase", text.any { it.isLowerCase() })
        assertTrue("Should contain digit", text.any { it.isDigit() })
        assertTrue("Should contain symbol", text.any { it in "!@#$%^&*()-_=+[]{}|;:,.<>?" })

        crypto.wipe(password)
    }

    @Test
    fun `password generator excludes ambiguous when requested`() {
        val config = CryptoManager.PasswordConfig(
            length = 64,
            excludeAmbiguous = true
        )
        val password = crypto.generatePassword(config)
        val text = password.concatToString()

        assertFalse("Should not contain 0", text.contains('0'))
        assertFalse("Should not contain O", text.contains('O'))
        assertFalse("Should not contain 1", text.contains('1'))
        assertFalse("Should not contain l", text.contains('l'))
        assertFalse("Should not contain I", text.contains('I'))

        crypto.wipe(password)
    }

    @Test
    fun `derive entry key is deterministic for same inputs`() {
        val masterKey = crypto.randomBytes(32)
        val uuid = "test-uuid-123"

        val key1 = crypto.deriveEntryKey(masterKey, uuid)
        val key2 = crypto.deriveEntryKey(masterKey, uuid)

        assertArrayEquals(key1, key2)
        crypto.wipe(masterKey)
        crypto.wipe(key1)
        crypto.wipe(key2)
    }

    @Test
    fun `derive entry key differs for different uuids`() {
        val masterKey = crypto.randomBytes(32)

        val key1 = crypto.deriveEntryKey(masterKey, "uuid-a")
        val key2 = crypto.deriveEntryKey(masterKey, "uuid-b")

        assertFalse(key1.contentEquals(key2))
        crypto.wipe(masterKey)
        crypto.wipe(key1)
        crypto.wipe(key2)
    }

    @Test
    fun `wipe zeroes byte array`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        crypto.wipe(bytes)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `randomBytes produces different values`() {
        val r1 = crypto.randomBytes(16)
        val r2 = crypto.randomBytes(16)
        assertFalse(r1.contentEquals(r2))
    }

    @Test
    fun `sha256 produces consistent hash`() {
        val data = "hello".toByteArray()
        val hash1 = crypto.sha256(data)
        val hash2 = crypto.sha256(data)
        assertArrayEquals(hash1, hash2)
        assertEquals(32, hash1.size)
    }
}
