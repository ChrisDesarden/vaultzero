package com.vaultzero.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var clearJob: Job? = null

    fun copySensitive(label: String, text: String, timeoutSeconds: Int = 30) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        if (timeoutSeconds > 0) {
            clearJob = scope.launch {
                delay(timeoutSeconds * 1000L)
                clearPrimaryClip()
            }
        }
    }

    fun copy(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun clearPrimaryClip() {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(emptyClip)
        } catch (_: Exception) { /* ignore */ }
    }
}
