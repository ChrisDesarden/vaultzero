package com.vaultzero.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()

    event?.let {
        when (it) {
            is SettingsViewModel.SettingsEvent.BiometricUnavailable -> {
                Text(stringResource(R.string.error_biometric_not_available))
                viewModel.consumeEvent()
            }
            else -> viewModel.consumeEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle(stringResource(R.string.appearance))
            ThemeSelector(
                selected = state.theme,
                onSelect = viewModel::setTheme
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.security))
            TimeoutSelector(
                selected = state.autoLockTimeout,
                onSelect = viewModel::setAutoLockTimeout
            )
            if (state.biometricAvailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.biometric_unlock),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.biometricEnabled,
                        onCheckedChange = viewModel::setBiometricEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.password_generator))
            PasswordDefaultsCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.data))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::exportVault,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = viewModel::importVault,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ThemeSelector(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM)
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { theme ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (theme) {
                                AppTheme.LIGHT -> stringResource(R.string.light)
                                AppTheme.DARK -> stringResource(R.string.dark)
                                AppTheme.SYSTEM -> stringResource(R.string.system_default)
                            }
                        )
                    },
                    onClick = {
                        onSelect(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeoutSelector(
    selected: AutoLockTimeout,
    onSelect: (AutoLockTimeout) -> Unit
) {
    val options = AutoLockTimeout.entries.toList()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.auto_lock)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { timeout ->
                DropdownMenuItem(
                    text = { Text(timeout.label) },
                    onClick = {
                        onSelect(timeout)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PasswordDefaultsCard(
    state: SettingsViewModel.SettingsUiState,
    viewModel: SettingsViewModel
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${stringResource(R.string.length)}: ${state.passwordDefaults.length}")
            Slider(
                value = state.passwordDefaults.length.toFloat(),
                onValueChange = { viewModel.setPasswordLength(it.toInt()) },
                valueRange = 4f..128f,
                steps = 123
            )
            PasswordOptionRow(
                label = stringResource(R.string.uppercase),
                checked = state.passwordDefaults.includeUppercase,
                onCheckedChange = { viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.UPPERCASE, it) }
            )
            PasswordOptionRow(
                label = stringResource(R.string.lowercase),
                checked = state.passwordDefaults.includeLowercase,
                onCheckedChange = { viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.LOWERCASE, it) }
            )
            PasswordOptionRow(
                label = stringResource(R.string.numbers),
                checked = state.passwordDefaults.includeNumbers,
                onCheckedChange = { viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.NUMBERS, it) }
            )
            PasswordOptionRow(
                label = stringResource(R.string.symbols),
                checked = state.passwordDefaults.includeSymbols,
                onCheckedChange = { viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.SYMBOLS, it) }
            )
            PasswordOptionRow(
                label = stringResource(R.string.exclude_ambiguous),
                checked = state.passwordDefaults.excludeAmbiguous,
                onCheckedChange = { viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.AMBIGUOUS, it) }
            )
        }
    }
}

@Composable
private fun PasswordOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
