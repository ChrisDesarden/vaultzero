package com.vaultzero.app.presentation.util

import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator()

    @Test
    fun `generate returns password of requested length`() {
        val result = generator.generate(PasswordGeneratorDefaults(length = 20))
        assertEquals(20, result.length)
    }

    @Test
    fun `generate includes uppercase when enabled`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 16,
                includeUppercase = true,
                includeLowercase = false,
                includeNumbers = false,
                includeSymbols = false
            )
        )
        assertTrue(result.all { it.isUpperCase() })
    }

    @Test
    fun `generate includes lowercase when enabled`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 16,
                includeUppercase = false,
                includeLowercase = true,
                includeNumbers = false,
                includeSymbols = false
            )
        )
        assertTrue(result.all { it.isLowerCase() })
    }

    @Test
    fun `generate includes numbers when enabled`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 16,
                includeUppercase = false,
                includeLowercase = false,
                includeNumbers = true,
                includeSymbols = false
            )
        )
        assertTrue(result.all { it.isDigit() })
    }

    @Test
    fun `generate includes symbols when enabled`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 16,
                includeUppercase = false,
                includeLowercase = false,
                includeNumbers = false,
                includeSymbols = true
            )
        )
        assertTrue(result.all { !it.isLetterOrDigit() })
    }

    @Test
    fun `generate excludes ambiguous when requested`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 32,
                excludeAmbiguous = true
            )
        )
        assertFalse(result.any { it in "0O1lI" })
    }

    @Test
    fun `generate returns empty string when no character sets selected`() {
        val result = generator.generate(
            PasswordGeneratorDefaults(
                length = 16,
                includeUppercase = false,
                includeLowercase = false,
                includeNumbers = false,
                includeSymbols = false
            )
        )
        assertEquals("", result)
    }
}
