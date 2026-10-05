package com.vaultzero.app.presentation.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricCrypto @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BiometricCrypto"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val OLD_KEY_ALIAS = "vaultzero_biometric_master_key"
        private const val KEY_ALIAS = "vaultzero_biometric_master_key_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        try {
            if (keyStore.containsAlias(OLD_KEY_ALIAS)) {
                keyStore.deleteEntry(OLD_KEY_ALIAS)
                Log.d(TAG, "Deleted old biometric keystore key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old biometric key", e)
        }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey().also {
            Log.d(TAG, "Created biometric keystore key")
        }
    }

    fun getEncryptCipher(): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
    }

    fun getDecryptCipher(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
    }

    fun encrypt(plaintext: ByteArray, cipher: Cipher): ByteArray {
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(encrypted: ByteArray, cipher: Cipher): ByteArray {
        return cipher.doFinal(encrypted)
    }

    fun deleteKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
            keyStore.deleteEntry(OLD_KEY_ALIAS)
            Log.d(TAG, "Deleted biometric keystore key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete biometric key", e)
        }
    }
}
