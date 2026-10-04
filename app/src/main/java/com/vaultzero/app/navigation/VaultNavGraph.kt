package com.vaultzero.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vaultzero.app.presentation.group.GroupManagementScreen
import com.vaultzero.app.presentation.group.GroupManagementViewModel
import com.vaultzero.app.presentation.home.HomeScreen
import com.vaultzero.app.presentation.home.HomeViewModel
import com.vaultzero.app.presentation.settings.SettingsScreen
import com.vaultzero.app.presentation.settings.SettingsViewModel
import com.vaultzero.app.presentation.unlock.UnlockScreen
import com.vaultzero.app.presentation.unlock.UnlockViewModel
import com.vaultzero.app.presentation.vault.EditEntryScreen
import com.vaultzero.app.presentation.vault.EditEntryViewModel
import com.vaultzero.app.presentation.vault.EntryDetailScreen
import com.vaultzero.app.presentation.vault.EntryDetailViewModel
import kotlinx.serialization.Serializable

@Serializable
object Unlock

@Serializable
object Home

@Serializable
data class EntryDetail(val entryId: String)

@Serializable
data class EditEntry(val entryId: String?, val groupId: String?)

@Serializable
object Groups

@Serializable
object Settings

@Composable
fun VaultNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Unlock,
        modifier = modifier
    ) {
        composable<Unlock> {
            val viewModel: UnlockViewModel = hiltViewModel()
            UnlockScreen(
                viewModel = viewModel,
                onUnlocked = {
                    navController.navigate(Home) {
                        popUpTo(Unlock) { inclusive = true }
                    }
                }
            )
        }
        composable<Home> {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onEntryClick = { entryId ->
                    navController.navigate(EntryDetail(entryId))
                },
                onAddEntry = { groupId ->
                    navController.navigate(EditEntry(null, groupId))
                },
                onManageGroups = {
                    navController.navigate(Groups)
                },
                onOpenSettings = {
                    navController.navigate(Settings)
                }
            )
        }
        composable<EntryDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<EntryDetail>()
            val viewModel: EntryDetailViewModel = hiltViewModel()
            EntryDetailScreen(
                entryId = route.entryId,
                viewModel = viewModel,
                onEdit = { entryId ->
                    navController.navigate(EditEntry(entryId, null))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<EditEntry> { backStackEntry ->
            val route = backStackEntry.toRoute<EditEntry>()
            val viewModel: EditEntryViewModel = hiltViewModel()
            EditEntryScreen(
                entryId = route.entryId,
                groupId = route.groupId,
                viewModel = viewModel,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Groups> {
            val viewModel: GroupManagementViewModel = hiltViewModel()
            GroupManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable<Settings> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
