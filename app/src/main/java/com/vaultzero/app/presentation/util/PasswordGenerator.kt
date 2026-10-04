package com.vaultzero.app.presentation.util

import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordGenerator @Inject constructor() {

    private val random = SecureRandom()

    fun generate(defaults: PasswordGeneratorDefaults = PasswordGeneratorDefaults()): String {
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val numbers = "0123456789"
        val symbols = "!@#$%^\u0026*()-_=+[]{}|;:,.?<>"
        val ambiguous = "0O1lI"

        val pool = buildString {
            if (defaults.includeUppercase) append(uppercase)
            if (defaults.includeLowercase) append(lowercase)
            if (defaults.includeNumbers) append(numbers)
            if (defaults.includeSymbols) append(symbols)
        }

        val effectivePool = if (defaults.excludeAmbiguous) {
            pool.filterNot { it in ambiguous }
        } else {
            pool
        }

        if (effectivePool.isEmpty()) return ""

        return CharArray(defaults.length) { effectivePool[random.nextInt(effectivePool.length)] }
            .concatToString()
    }
}
