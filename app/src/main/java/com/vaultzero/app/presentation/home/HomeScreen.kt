package com.vaultzero.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultzero.app.R
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onEntryClick: (String) -> Unit,
    onAddEntry: (String?) -> Unit,
    onManageGroups: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups)) },
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            onClick = {
                                menuExpanded = false
                                onManageGroups()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lock_vault)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.lock()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddEntry(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (state.isEmpty) {
                EmptyVault(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        item {
                            GroupHeader(group = group)
                        }
                        items(
                            items = state.entries.filter { it.groupId == group.id },
                            key = { it.id }
                        ) { entry ->
                            EntryRow(
                                entry = entry,
                                onClick = { onEntryClick(entry.id) }
                            )
                        }
                    }
                    val uncategorized = state.entries.filter { it.groupId == null || state.groups.none { g -> g.id == it.groupId } }
                    if (uncategorized.isNotEmpty()) {
                        item { GroupHeader(group = VaultGroup(id = "", name = stringResource(R.string.uncategorized))) }
                        items(uncategorized, key = { it.id }) { entry ->
                            EntryRow(entry = entry, onClick = { onEntryClick(entry.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: VaultGroup) {
    Text(
        text = group.name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EntryRow(entry: VaultEntry, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = {
            if (entry.username.isNotBlank()) {
                Text(entry.username, style = MaterialTheme.typography.bodyMedium)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyVault(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.height(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_vault),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
