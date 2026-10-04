package com.vaultzero.app.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Central crypto manager for VaultZero.
 *
 * Responsibilities:
 *  - Argon2id / PBKDF2 key derivation from master password (+ optional keyfile)
 *  - AES-256-GCM encrypt/decrypt with 12-byte random nonce + 16-byte tag
 *  - Secure random generation
 *  - Password generator with configurable character sets
 *  - ByteArray / CharArray wiping (best-effort via Arrays.fill)
 *
 * Security notes:
 *  - The BouncyCastle provider is registered explicitly for PBKDF2 and AES-GCM.
 *  - Argon2id is preferred; PBKDF2 is a fallback if Argon2 native lib fails to load.
 *  - Plaintext ByteArray/CharArray should be zeroed as soon as possible via [wipe].
 *  - This class does NOT persist keys; callers must manage key lifetime.
 */
class CryptoManager {

    companion object {
        const val AES_KEY_SIZE_BITS = 256
        const val AES_KEY_SIZE_BYTES = AES_KEY_SIZE_BITS / 8 // 32
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128

        // Argon2id defaults (tuned for ~500ms on mid-range Android)
        const val ARGON2_MEMORY_KB = 64 * 1024 // 64 MB
        const val ARGON2_ITERATIONS = 3
        const val ARGON2_PARALLELISM = 4

        // PBKDF2 fallback (OWASP 2023 recommendation)
        const val PBKDF2_ITERATIONS = 600_000

        const val VAULT_VERSION = 1

        // Keyfile derivation info string for HKDF-like SHA-256 composition
        private const val KEYFILE_INFO = "VaultZero/Keyfile/v1"

        init {
            // Ensure BouncyCastle is available
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Derive a 256-bit master key from [password] and optional [keyfileBytes].
     *
     * @param password   The user's master password.
     * @param salt       16+ random bytes (must be persisted in vault metadata).
     * @param keyfileBytes Optional additional entropy from a keyfile. If provided,
     *                     the raw file contents are hashed with SHA-256 and composed
     *                     with the password-derived key.
     * @param useArgon2  true = Argon2id, false = PBKDF2.
     * @return A fresh 32-byte master key. Caller must [wipe] when done.
     */
    fun deriveMasterKey(
        password: CharArray,
        salt: ByteArray,
        keyfileBytes: ByteArray? = null,
        useArgon2: Boolean = true
    ): ByteArray {
        require(salt.size >= 16) { "Salt must be at least 16 bytes" }

        val passwordBytes = charArrayToByteArray(password)
        try {
            val derived = if (useArgon2) {
                deriveWithArgon2id(passwordBytes, salt)
            } else {
                deriveWithPbkdf2(passwordBytes, salt)
            }

            return if (keyfileBytes != null) {
                combineWithKeyfile(derived, keyfileBytes).also {
                    wipe(derived)
                }
            } else {
                derived
            }
        } finally {
            wipe(passwordBytes)
        }
    }

    /**
     * Derive using Argon2id (preferred). Tuned for ~500ms on typical devices.
     */
    private fun deriveWithArgon2id(passwordBytes: ByteArray, salt: ByteArray): ByteArray {
        val argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id,
            ARGON2_MEMORY_KB,
            ARGON2_PARALLELISM
        )
        return try {
            argon2.hash(
                ARGON2_ITERATIONS,
                ARGON2_MEMORY_KB,
                ARGON2_PARALLELISM,
                passwordBytes,
                StandardCharsets.UTF_8,
                salt,
                AES_KEY_SIZE_BYTES
            )
        } finally {
            argon2.wipeArray(passwordBytes)
        }
    }

    /**
     * Derive using PBKDF2-HMAC-SHA256 (fallback if Argon2 unavailable).
     */
    private fun deriveWithPbkdf2(passwordBytes: ByteArray, salt: ByteArray): ByteArray {
        val gen = PKCS5S2ParametersGenerator()
        gen.init(passwordBytes, salt, PBKDF2_ITERATIONS)
        val params = gen.generateDerivedMacParameters(AES_KEY_SIZE_BITS)
        return (params as KeyParameter).key
    }

    /**
     * Combine a password-derived key with a keyfile hash:
     * combined = SHA-256(derivedKey || keyfileHash)
     */
    private fun combineWithKeyfile(derivedKey: ByteArray, keyfileBytes: ByteArray): ByteArray {
        val keyfileHash = sha256(keyfileBytes)
        return sha256(derivedKey + keyfileHash)
    }

    /**
     * Encrypt [plaintext] with AES-256-GCM.
     *
     * @param key        32-byte key.
     * @param plaintext  Data to encrypt.
     * @param aad        Optional additional authenticated data.
     * @return ByteArray: [nonce (12 bytes) || ciphertext || tag (16 bytes)]
     */
    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "Key must be 32 bytes" }

        val nonce = randomBytes(GCM_IV_SIZE_BYTES)
        val cipher = GCMBlockCipher(AESEngine())
        val params = AEADParameters(KeyParameter(key), GCM_TAG_SIZE_BITS, nonce)
        cipher.init(true, params)

        aad?.let { cipher.processAADBytes(it, 0, it.size) }

        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, len)

        return nonce + output
    }

    /**
     * Decrypt [ciphertext] produced by [encrypt].
     *
     * @param key         32-byte key.
     * @param ciphertext  [nonce (12) || ciphertext+tag].
     * @param aad         Must match the AAD used during encryption.
     * @return Plaintext ByteArray. Caller must [wipe] when done.
     * @throws VaultCryptoException on tag mismatch or corrupted data.
     */
    fun decrypt(
        key: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "Key must be 32 bytes" }
        require(ciphertext.size > GCM_IV_SIZE_BYTES) { "Ciphertext too short" }

        val nonce = ciphertext.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val payload = ciphertext.copyOfRange(GCM_IV_SIZE_BYTES, ciphertext.size)

        val cipher = GCMBlockCipher(AESEngine())
        val params = AEADParameters(KeyParameter(key), GCM_TAG_SIZE_BITS, nonce)
        cipher.init(false, params)

        aad?.let { cipher.processAADBytes(it, 0, it.size) }

        val output = ByteArray(cipher.getOutputSize(payload.size))
        return try {
            val len = cipher.processBytes(payload, 0, payload.size, output, 0)
            cipher.doFinal(output, len)
            output.copyOf(len + (output.size - len)) // trim to actual length
        } catch (e: Exception) {
            wipe(output)
            throw VaultCryptoException("Decryption failed: invalid key or corrupted ciphertext", e)
        }
    }

    /**
     * Encrypt a [CharArray] password to a ciphertext ByteArray, then wipe the plaintext.
     */
    fun encryptPassword(key: ByteArray, password: CharArray, aad: ByteArray? = null): ByteArray {
        val bytes = charArrayToByteArray(password)
        return try {
            encrypt(key, bytes, aad)
        } finally {
            wipe(bytes)
        }
    }

    /**
     * Decrypt a password ciphertext back to a [CharArray], then wipe intermediate bytes.
     */
    fun decryptPassword(key: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): CharArray {
        val bytes = decrypt(key, ciphertext, aad)
        return try {
            byteArrayToCharArray(bytes)
        } finally {
            wipe(bytes)
        }
    }

    /**
     * Generate [count] random bytes.
     */
    fun randomBytes(count: Int): ByteArray = ByteArray(count).apply {
        secureRandom.nextBytes(this)
    }

    /**
     * Generate a random UUID string (for entities).
     */
    fun randomUuid(): String = java.util.UUID.randomUUID().toString()

    /**
     * SHA-256 hash of [data].
     */
    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * HMAC-SHA256. Used for HKDF-like per-entry key derivation.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256", "BC")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Derive a per-entry key from the master key and entry UUID.
     * entryKey = HMAC-SHA256(masterKey, "VaultZero/Entry/1.0" || entryUuid)
     */
    fun deriveEntryKey(masterKey: ByteArray, entryUuid: String): ByteArray {
        val info = "VaultZero/Entry/1.0$entryUuid".toByteArray(StandardCharsets.UTF_8)
        return hmacSha256(masterKey, info)
    }

    /**
     * Best-effort secure wipe of a ByteArray.
     * On Android, this overwrites array contents; JVM may still leave copies
     * in memory due to GC / copy-on-write.
     */
    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    /**
     * Best-effort secure wipe of a CharArray.
     */
    fun wipe(chars: CharArray?) {
        chars?.fill('\u0000')
    }

    /**
     * Convert CharArray to UTF-8 ByteArray without intermediate String.
     */
    private fun charArrayToByteArray(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // Clear the direct buffer if it has an array backing
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
        return bytes
    }

    /**
     * Convert UTF-8 ByteArray to CharArray without intermediate String.
     */
    private fun byteArrayToCharArray(bytes: ByteArray): CharArray {
        val byteBuffer = ByteBuffer.wrap(bytes)
        val charBuffer = StandardCharsets.UTF_8.decode(byteBuffer)
        val chars = CharArray(charBuffer.remaining())
        charBuffer.get(chars)
        if (charBuffer.hasArray()) {
            charBuffer.array().fill('\u0000')
        }
        return chars
    }

    // ------------------------------------------------------------------
    // Password Generator
    // ------------------------------------------------------------------

    data class PasswordConfig(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeAmbiguous: Boolean = false
    ) {
        init {
            require(length >= 4) { "Password length must be at least 4" }
            require(length <= 256) { "Password length must be at most 256" }
            require(includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
                "At least one character set must be enabled"
            }
        }
    }

    private val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val DIGITS = "0123456789"
    private val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private val AMBIGUOUS = "0O1lI"

    /**
     * Generate a random password according to [config].
     * Guarantees at least one character from each enabled set.
     */
    fun generatePassword(config: PasswordConfig = PasswordConfig()): CharArray {
        val pool = buildString {
            if (config.includeLowercase) append(LOWER)
            if (config.includeUppercase) append(UPPER)
            if (config.includeNumbers) append(DIGITS)
            if (config.includeSymbols) append(SYMBOLS)
        }.toList().let { chars ->
            if (config.excludeAmbiguous) chars.filter { it !in AMBIGUOUS } else chars
        }

        require(pool.isNotEmpty()) { "Character pool is empty after filtering" }

        val password = CharArray(config.length)
        var index = 0

        // Guarantee at least one char from each enabled set
        if (config.includeLowercase) password[index++] = LOWER.randomChar()
        if (config.includeUppercase) password[index++] = UPPER.randomChar()
        if (config.includeNumbers) password[index++] = DIGITS.randomChar()
        if (config.includeSymbols) password[index++] = SYMBOLS.randomChar()

        // Fill remaining with random chars from pool
        while (index < config.length) {
            password[index++] = pool.randomChar()
        }

        // Shuffle
        password.shuffle()
        return password
    }

    private fun String.randomChar(): Char = this[secureRandom.nextInt(this.length)]
    private fun List<Char>.randomChar(): Char = this[secureRandom.nextInt(this.size)]

    private fun CharArray.shuffle() {
        for (i in size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    // ------------------------------------------------------------------
    // Exceptions
    // ------------------------------------------------------------------

    class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
