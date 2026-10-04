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

/**
 * Secure clipboard helper that auto-clears copied sensitive data after a timeout.
 */
@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var clearJob: Job? = null

    /**
     * Copy text to clipboard and schedule auto-clear after [timeoutSeconds].
     * Pass 0 to disable auto-clear.
     */
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

    /** Copy non-sensitive text (no auto-clear). */
    fun copy(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    /** Clear the clipboard immediately. */
    fun clearPrimaryClip() {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(emptyClip)
        } catch (_: Exception) { /* ignore */ }
    }
}
