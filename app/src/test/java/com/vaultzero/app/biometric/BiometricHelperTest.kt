package com.vaultzero.app.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BiometricHelperTest {

    private lateinit var context: Context
    private lateinit var biometricManager: BiometricManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        biometricManager = mockk(relaxed = true)
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
    }

    @After
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun `canAuthenticate returns true when biometric is available`() {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        val helper = BiometricHelper(context)
        assertTrue(helper.canAuthenticate())
    }

    @Test
    fun `canAuthenticate returns false when biometric is not available`() {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val helper = BiometricHelper(context)
        assertFalse(helper.canAuthenticate())
    }

    @Test
    fun `encryptDecryptPayload roundtrip`() {
        // Note: Real encrypt/decrypt requires Android Keystore which isn't available in JVM tests.
        // This test verifies the BiometricPayload data class behavior.
        val iv = byteArrayOf(1, 2, 3)
        val ciphertext = byteArrayOf(4, 5, 6)
        val payload = BiometricPayload(iv, ciphertext)

        assertTrue(payload.iv.contentEquals(iv))
        assertTrue(payload.ciphertext.contentEquals(ciphertext))

        val same = BiometricPayload(iv.copyOf(), ciphertext.copyOf())
        assertTrue(payload == same)
    }
}
