package com.vaultzero.app.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vaultzero.app.presentation.entry.EntryEditScreen
import com.vaultzero.app.presentation.entry.EntryEditViewModel
import com.vaultzero.app.presentation.groups.GroupScreen
import com.vaultzero.app.presentation.groups.GroupViewModel
import com.vaultzero.app.presentation.home.HomeScreen
import com.vaultzero.app.presentation.home.HomeViewModel
import com.vaultzero.app.presentation.settings.SettingsScreen
import com.vaultzero.app.presentation.settings.SettingsViewModel
import com.vaultzero.app.presentation.unlock.UnlockScreen
import com.vaultzero.app.presentation.unlock.UnlockViewModel

@Composable
fun VaultNavHost(
    navController: NavHostController,
    startDestination: String = VaultRoutes.UNLOCK
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(VaultRoutes.UNLOCK) {
            val viewModel: UnlockViewModel = hiltViewModel()
            UnlockScreen(
                viewModel = viewModel,
                onUnlocked = {
                    navController.navigate(VaultRoutes.HOME) {
                        popUpTo(VaultRoutes.UNLOCK) { inclusive = true }
                    }
                }
            )
        }

        composable(VaultRoutes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onEntryClick = { entryId ->
                    navController.navigate("${VaultRoutes.ENTRY_DETAIL}?entryId=$entryId")
                },
                onAddEntry = { navController.navigate(VaultRoutes.ENTRY_EDIT) },
                onGroups = { navController.navigate(VaultRoutes.GROUPS) },
                onSettings = { navController.navigate(VaultRoutes.SETTINGS) },
                onLock = {
                    navController.navigate(VaultRoutes.UNLOCK) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "${VaultRoutes.ENTRY_DETAIL}?entryId={entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: EntryEditViewModel = hiltViewModel()
            val entryId = backStackEntry.arguments?.getString("entryId")
            EntryEditScreen(
                viewModel = viewModel,
                entryId = entryId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(VaultRoutes.ENTRY_EDIT) {
            val viewModel: EntryEditViewModel = hiltViewModel()
            EntryEditScreen(
                viewModel = viewModel,
                entryId = null,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(VaultRoutes.GROUPS) {
            val viewModel: GroupViewModel = hiltViewModel()
            GroupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(VaultRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
