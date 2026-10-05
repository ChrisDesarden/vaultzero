package com.vaultzero.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.usecase.GetSettingsUseCase
import com.vaultzero.app.domain.usecase.LockVaultUseCase
import com.vaultzero.app.presentation.base.SecureActivity
import androidx.navigation.NavHostController
import com.vaultzero.app.navigation.VaultNavHost
import com.vaultzero.app.navigation.VaultRoutes
import com.vaultzero.app.ui.theme.VaultZeroTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : SecureActivity() {

    @Inject
    lateinit var getSettings: GetSettingsUseCase

    @Inject
    lateinit var lockVault: LockVaultUseCase

    private var navController: NavHostController? = null

    private val autoLockObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            lifecycleScope.launch {
                lockVault()
                navController?.let { nav ->
                    if (nav.currentDestination?.route != VaultRoutes.UNLOCK) {
                        nav.navigate(VaultRoutes.UNLOCK) {
                            popUpTo(nav.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(autoLockObserver)

        lifecycleScope.launch {
            val initialSettings = try {
                getSettings().first()
            } catch (_: Exception) {
                VaultSettings()
            }
            runOnUiThread {
                setContent {
                    val settings by getSettings().collectAsState(initial = initialSettings)
                    VaultZeroTheme(theme = settings.theme) {
                        val controller = rememberNavController()
                        navController = controller
                        VaultNavHost(navController = controller)
                    }
                }
            }
        }
    }
}
