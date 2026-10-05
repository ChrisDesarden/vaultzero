package com.vaultzero.app.util

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureClipboard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copy(text: String, clearAfterSeconds: Int = 30) {
        val clip = ClipData.newPlainText("VaultZero", text)
        clipboard.setPrimaryClip(clip)
        if (clearAfterSeconds > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                clear()
            }, clearAfterSeconds * 1000L)
        }
    }

    fun clear() {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
            // Ignore clear failures
        }
    }
}
