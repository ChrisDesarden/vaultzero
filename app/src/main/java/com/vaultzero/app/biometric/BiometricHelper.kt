package com.vaultzero.app.biometric

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric helper using Android Keystore-backed key.
 * The biometric key wraps (encrypts) the database master key.
 * Never stores the master password in plaintext.
 */
@Singleton
class BiometricHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vaultzero_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val biometricManager by lazy { BiometricManager.from(context) }

    /** Checks if biometric hardware is available and enrolled. */
    fun canAuthenticate(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Show biometric prompt for encryption (enrollment). */
    fun promptForEnrollment(
        activity: FragmentActivity,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = getEncryptCipher() ?: run { onError("Failed to initialize cipher"); return }
        showPrompt(activity, cipher, onSuccess, onError)
    }

    /** Show biometric prompt for decryption (unlock). */
    fun promptForDecryption(
        activity: FragmentActivity,
        iv: ByteArray,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = getDecryptCipher(iv) ?: run { onError("Failed to initialize cipher"); return }
        showPrompt(activity, cipher, onSuccess, onError)
    }

    /** Encrypt data with the biometric key. */
    fun encrypt(cipher: Cipher, plaintext: ByteArray): BiometricPayload {
        val ciphertext = cipher.doFinal(plaintext)
        return BiometricPayload(cipher.iv, ciphertext)
    }

    /** Decrypt data with the biometric key. */
    fun decrypt(cipher: Cipher, payload: BiometricPayload): ByteArray {
        return cipher.doFinal(payload.ciphertext)
    }

    /** Delete the Keystore key (e.g., user disables biometric). */
    fun clearKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun showPrompt(
        activity: FragmentActivity,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(onSuccess)
                        ?: onError("Crypto object unavailable")
                }

                override fun onAuthenticationFailed() {
                    onError("Authentication failed")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError("CANCELLED")
                    } else {
                        onError(errString.toString())
                    }
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(com.vaultzero.app.R.string.biometric_prompt_title))
            .setSubtitle(context.getString(com.vaultzero.app.R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(context.getString(com.vaultzero.app.R.string.biometric_prompt_use_password))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun getEncryptCipher(): Cipher? {
        return try {
            val key = getOrCreateKey()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getDecryptCipher(iv: ByteArray): Cipher? {
        return try {
            val key = getOrCreateKey()
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let { return (it as KeyStore.SecretKeyEntry).secretKey }

        val keyGen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        keyGen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
                .build()
        )
        return keyGen.generateKey()
    }
}

data class BiometricPayload(val iv: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BiometricPayload
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
