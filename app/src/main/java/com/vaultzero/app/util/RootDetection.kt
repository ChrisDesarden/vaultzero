package com.vaultzero.app.util

import android.os.Build
import java.io.File

/**
 * Lightweight root/jailbreak detection. Only checks common indicators; not
 * exhaustive. Used only to show a warning — VaultZero never blocks or
 * deletes data based on these checks.
 */
object RootDetection {

    fun isDeviceRooted(): Boolean {
        return testKeysBuild() || superuserBinaryExists() || magiskArtifactsExist() || canExecuteSu()
    }

    private fun testKeysBuild(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun superuserBinaryExists(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun magiskArtifactsExist(): Boolean {
        return listOf("/sbin/.magisk", "/dev/.magisk.unblock", "/system/bin/magisk")
            .any { File(it).exists() }
    }

    private fun canExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
