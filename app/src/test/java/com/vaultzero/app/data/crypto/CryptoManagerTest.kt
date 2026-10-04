package com.vaultzero.app.data.crypto

import com.vaultzero.app.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CryptoManagerTest {

    private lateinit var crypto: CryptoManager

    @Before
    fun setUp() {
        crypto = CryptoManager()
    }

    @Test
    fun `deriveMasterKey returns 32 bytes`() {
        val salt = crypto.randomBytes(16)
        val key = crypto.deriveMasterKey("password".toCharArray(), salt)
        assertEquals(32, key.size)
        crypto.wipe(key)
    }

    @Test
    fun `deriveMasterKey is deterministic with same inputs`() {
        val salt = crypto.randomBytes(16)
        val key1 = crypto.deriveMasterKey("password".toCharArray(), salt)
        val key2 = crypto.deriveMasterKey("password".toCharArray(), salt)
        assertArrayEquals(key1, key2)
        crypto.wipe(key1)
        crypto.wipe(key2)
    }

    @Test
    fun `deriveMasterKey produces different keys for different passwords`() {
        val salt = crypto.randomBytes(16)
        val key1 = crypto.deriveMasterKey("password1".toCharArray(), salt)
        val key2 = crypto.deriveMasterKey("password2".toCharArray(), salt)
        assertTrue(!key1.contentEquals(key2))
        crypto.wipe(key1)
        crypto.wipe(key2)
    }

    @Test
    fun `encrypt and decrypt round trip`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Hello, VaultZero!".toByteArray()
        val ciphertext = crypto.encrypt(key, plaintext)
        val decrypted = crypto.decrypt(key, ciphertext)
        assertArrayEquals(plaintext, decrypted)
        crypto.wipe(key)
        crypto.wipe(plaintext)
        crypto.wipe(decrypted)
    }

    @Test
    fun `encrypt produces different ciphertexts for same plaintext`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Hello".toByteArray()
        val ciphertext1 = crypto.encrypt(key, plaintext)
        val ciphertext2 = crypto.encrypt(key, plaintext)
        assertTrue(!ciphertext1.contentEquals(ciphertext2))
        crypto.wipe(key)
    }

    @Test(expected = CryptoManager.VaultCryptoException::class)
    fun `decrypt with wrong key throws`() {
        val key = crypto.randomBytes(32)
        val wrongKey = crypto.randomBytes(32)
        val plaintext = "Secret".toByteArray()
        val ciphertext = crypto.encrypt(key, plaintext)
        crypto.decrypt(wrongKey, ciphertext)
    }

    @Test
    fun `encrypt decrypt with AAD`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Secret".toByteArray()
        val aad = byteArrayOf(0x01, 0x02, 0x03)
        val ciphertext = crypto.encrypt(key, plaintext, aad)
        val decrypted = crypto.decrypt(key, ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = CryptoManager.VaultCryptoException::class)
    fun `decrypt with wrong AAD throws`() {
        val key = crypto.randomBytes(32)
        val plaintext = "Secret".toByteArray()
        val aad = byteArrayOf(0x01, 0x02, 0x03)
        val ciphertext = crypto.encrypt(key, plaintext, aad)
        crypto.decrypt(key, ciphertext, byteArrayOf(0x04))
    }

    @Test
    fun `randomBytes produces unique values`() {
        val b1 = crypto.randomBytes(32)
        val b2 = crypto.randomBytes(32)
        assertTrue(!b1.contentEquals(b2))
    }

    @Test
    fun `generatePassword produces correct length`() {
        val config = CryptoManager.PasswordConfig(length = 20)
        val pw = crypto.generatePassword(config)
        assertEquals(20, pw.size)
    }

    @Test
    fun `generatePassword includes required character sets`() {
        val config = CryptoManager.PasswordConfig(
            length = 16,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val pw = crypto.generatePassword(config).concatToString()
        assertTrue(pw.any { it.isUpperCase() })
        assertTrue(pw.any { it.isLowerCase() })
        assertTrue(pw.any { it.isDigit() })
        assertTrue(pw.any { it in "!@#\$%^&*()-_=+[]{}|;:,.<>?" })
    }

    @Test
    fun `generatePassword excludes ambiguous when configured`() {
        val config = CryptoManager.PasswordConfig(
            length = 32,
            excludeAmbiguous = true
        )
        val pw = crypto.generatePassword(config).concatToString()
        assertTrue("0" !in pw)
        assertTrue("O" !in pw)
        assertTrue("1" !in pw)
        assertTrue("l" !in pw)
        assertTrue("I" !in pw)
    }

    @Test
    fun `encodeBase64 and decodeBase64 round trip`() {
        val bytes = crypto.randomBytes(32)
        val encoded = crypto.encodeBase64(bytes)
        val decoded = crypto.decodeBase64(encoded)
        assertArrayEquals(bytes, decoded)
    }

    @Test
    fun `deriveEntryKey is deterministic`() {
        val masterKey = crypto.randomBytes(32)
        val entryKey1 = crypto.deriveEntryKey(masterKey, "uuid-123")
        val entryKey2 = crypto.deriveEntryKey(masterKey, "uuid-123")
        assertArrayEquals(entryKey1, entryKey2)
        assertEquals(32, entryKey1.size)
    }

    @Test
    fun `deriveEntryKey produces different keys for different uuids`() {
        val masterKey = crypto.randomBytes(32)
        val key1 = crypto.deriveEntryKey(masterKey, "uuid-1")
        val key2 = crypto.deriveEntryKey(masterKey, "uuid-2")
        assertTrue(!key1.contentEquals(key2))
    }

    @Test
    fun `sha256 produces 32 bytes`() {
        val hash = crypto.sha256("test".toByteArray())
        assertEquals(32, hash.size)
    }

    @Test
    fun `hmacSha256 produces 32 bytes`() {
        val key = crypto.randomBytes(32)
        val mac = crypto.hmacSha256(key, "data".toByteArray())
        assertEquals(32, mac.size)
    }

    @Test
    fun `encryptPassword and decryptPassword round trip`() {
        val key = crypto.randomBytes(32)
        val password = "S3cureP@ssw0rd!".toCharArray()
        val cipher = crypto.encryptPassword(key, password)
        val decrypted = crypto.decryptPassword(key, cipher)
        assertArrayEquals(password, decrypted)
        crypto.wipe(decrypted)
    }
}
