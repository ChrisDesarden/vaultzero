package com.vaultzero.app.data.repository

import android.content.Context
import android.net.Uri
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.domain.model.VaultEntry
import org.bouncycastle.crypto.BufferedBlockCipher
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * Imports Password Safe v3 (.psafe3 / .dat) databases into VaultZero entries.
 *
 * The file is decrypted entirely on-device using the user's PasswordSafe master
 * password. Only the parsed entries are imported; the original file contents are
 * not retained.
 *
 * Based on the Password Safe v3 file format specification:
 * https://github.com/pwsafe/pwsafe/blob/master/docs/formatV3.txt
 */
class PasswordSafeImporter @Inject constructor(
    private val context: Context,
    private val crypto: CryptoManager
) {

    /**
     * Decrypts a PasswordSafe v3 file and returns the entries it contains.
     *
     * @return the list of entries on success, or null if the password is wrong
     *         or the file is malformed.
     */
    suspend fun import(uri: Uri, password: String): List<VaultEntry>? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        return decrypt(bytes, password.toCharArray())
    }

    private fun decrypt(file: ByteArray, passChars: CharArray): List<VaultEntry>? {
        try {
            val buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN)

            // 1. TAG
            val tag = ByteArray(4)
            buffer.get(tag)
            if (!tag.contentEquals(TAG)) return null

            // 2. SALT (32 bytes)
            val salt = ByteArray(32)
            buffer.get(salt)

            // 3. ITER (4 bytes LE)
            val iterations = buffer.int

            // 4. H(P') (32 bytes)
            val expectedKeyHash = ByteArray(32)
            buffer.get(expectedKeyHash)

            // 5. B1..B4 (64 bytes) — Twofish-ECB encrypted K and L
            val keyBlocks = ByteArray(64)
            buffer.get(keyBlocks)

            // 6. IV (16 bytes)
            val iv = ByteArray(16)
            buffer.get(iv)

            // Stretch the passphrase
            val stretchedKey = stretchKey(passChars, salt, iterations)
            val keyHash = sha256(stretchedKey)
            if (!keyHash.contentEquals(expectedKeyHash)) {
                crypto.wipe(stretchedKey)
                return null
            }

            // Decrypt K and L
            val kAndL = decryptEcb(stretchedKey, keyBlocks)
            crypto.wipe(stretchedKey)
            val k = kAndL.copyOfRange(0, 32)
            val l = kAndL.copyOfRange(32, 64)
            crypto.wipe(kAndL)

            // Decrypt the CBC payload: header + records + EOF
            val ciphertextStart = buffer.position()
            val eofIndex = findEof(file, ciphertextStart)
                ?: run {
                    crypto.wipe(k)
                    crypto.wipe(l)
                    return null
                }
            val ciphertextLength = eofIndex - ciphertextStart
            val ciphertext = ByteArray(ciphertextLength)
            buffer.position(ciphertextStart)
            buffer.get(ciphertext)

            val plaintext = decryptCbc(k, iv, ciphertext)
            crypto.wipe(k)
            crypto.wipe(ciphertext)

            // Read EOF marker bytes (unencrypted)
            val eofMarker = ByteArray(EOF.size)
            buffer.position(eofIndex)
            buffer.get(eofMarker)
            if (!eofMarker.contentEquals(EOF)) {
                crypto.wipe(l)
                crypto.wipe(plaintext)
                return null
            }

            // Read and verify HMAC
            if (buffer.remaining() < 32) {
                crypto.wipe(l)
                crypto.wipe(plaintext)
                return null
            }
            val storedHmac = ByteArray(32)
            buffer.get(storedHmac)
            val computedHmac = hmacSha256(l, plaintext)
            crypto.wipe(l)
            val hmacOk = storedHmac.contentEquals(computedHmac)
            crypto.wipe(storedHmac)
            crypto.wipe(computedHmac)
            if (!hmacOk) {
                crypto.wipe(plaintext)
                return null
            }

            // Parse plaintext into entries
            val entries = parseEntries(plaintext)
            crypto.wipe(plaintext)
            return entries
        } catch (e: Exception) {
            return null
        }
    }

    private fun findEof(file: ByteArray, start: Int): Int? {
        for (i in start..file.size - EOF.size) {
            var match = true
            for (j in EOF.indices) {
                if (file[i + j] != EOF[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return null
    }

    private fun stretchKey(passChars: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        return try {
            val passBytes = passChars.concatToString().toByteArray(StandardCharsets.UTF_8)
            val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
                javax.crypto.spec.PBEKeySpec(
                    passChars,
                    salt,
                    iterations.coerceAtLeast(1),
                    256
                )
            ).encoded
            crypto.wipe(passBytes)
            key
        } finally {
            // PBEKeySpec doesn't give us a direct wipe; rely on best-effort GC
        }
    }

    private fun sha256(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun decryptEcb(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = BufferedBlockCipher(TwofishEngine())
        cipher.init(false, KeyParameter(key))
        val output = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun decryptCbc(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = BufferedBlockCipher(CBCBlockCipher(TwofishEngine()))
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun parseEntries(plaintext: ByteArray): List<VaultEntry> {
        val entries = mutableListOf<VaultEntry>()
        val buf = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN)

        // Skip header: read fields until END (0xff)
        while (buf.remaining() >= 16) {
            val (length, type, _) = readFieldBlock(buf)
            if (type == 0xff.toByte()) break
            // skip header field value blocks
            val blocks = blocksForLength(length)
            skipBlocks(buf, blocks)
        }

        // Read records
        while (buf.remaining() >= 16) {
            val recordFields = mutableMapOf<Byte, ByteArray>()
            var endFound = false
            while (buf.remaining() >= 16) {
                val (length, type, data) = readFieldBlock(buf)
                if (type == 0xff.toByte()) {
                    endFound = true
                    break
                }
                recordFields[type] = data.copyOfRange(0, length)
            }
            if (!endFound) break

            val title = recordFields[0x03]?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            if (title.isBlank()) continue

            val group = recordFields[0x02]?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            val username = recordFields[0x04]?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            val password = recordFields[0x06]?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            val notes = recordFields[0x05]?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            val url = recordFields[0x0d]?.let { String(it, StandardCharsets.UTF_8) } ?: ""

            entries.add(
                VaultEntry(
                    id = crypto.randomUuid(),
                    title = title,
                    username = username,
                    password = password,
                    url = url,
                    notes = notes,
                    tags = emptyList(),
                    groupId = null,
                    favorite = false
                )
            )
        }

        return entries
    }

    private fun readFieldBlock(buf: ByteBuffer): Triple<Int, Byte, ByteArray> {
        val block = ByteArray(16)
        buf.get(block)
        val length = ByteBuffer.wrap(block, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val type = block[4]
        return Triple(length, type, block)
    }

    private fun blocksForLength(length: Int): Int {
        return (4 + 1 + length + 15) / 16
    }

    private fun skipBlocks(buf: ByteBuffer, blocks: Int) {
        repeat(blocks - 1) {
            if (buf.remaining() >= 16) {
                buf.position(buf.position() + 16)
            }
        }
    }

    companion object {
        private val TAG = "PWS3".toByteArray(StandardCharsets.US_ASCII)
        private val EOF = "PWS3-EOFPWS3-EOF".toByteArray(StandardCharsets.US_ASCII)
    }
}
