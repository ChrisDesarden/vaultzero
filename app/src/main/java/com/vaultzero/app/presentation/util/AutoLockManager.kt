package com.vaultzero.app.presentation.util

import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLockManager @Inject constructor(
    private val repository: VaultRepository
) {
    private var lockJob: Job? = null

    fun onActivityResumed() {
        cancelLock()
    }

    fun onActivityPaused(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        cancelLock()
        scope.launch {
            // In a real implementation, observe settings for timeout value
            lockJob = scope.launch {
                delay(300_000L) // 5 minutes default
                repository.lock()
            }
        }
    }

    private fun cancelLock() {
        lockJob?.cancel()
        lockJob = null
    }
}
