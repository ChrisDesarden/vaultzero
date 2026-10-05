package com.vaultzero.app.presentation.settings

import android.content.Context
import android.content.ContextWrapper
import android.util.Log

import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var biometricMessage by remember { mutableStateOf<String?>(null) }

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
                Spacer(modifier = Modifier.height(8.dp))
                BiometricRow(
                    enabled = state.biometricEnabled,
                    onToggle = { enabled ->
                        val activity = context.unwrapToFragmentActivity()
                        activity?.let {
                            viewModel.setBiometricEnabled(it, enabled) { msg ->
                                biometricMessage = msg
                            }
                        } ?: run {
                            biometricMessage = "Could not get FragmentActivity context"
                        }
                    }
                )
                biometricMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.import_export))
            ImportExportSection(
                viewModel = viewModel,
                onMessage = { msg -> biometricMessage = msg }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.password_generator))
            PasswordDefaultsCard(
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ImportExportSection(
    viewModel: SettingsViewModel,
    onMessage: (String?) -> Unit
) {
    val context = LocalContext.current
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingPasswordSafeUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            pendingExportUri = uri
            if (uri == null) onMessage("Export cancelled")
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            pendingImportUri = uri
            if (uri == null) onMessage("Import cancelled")
        }
    )

    val passwordSafeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            pendingPasswordSafeUri = uri
            if (uri == null) onMessage("PasswordSafe import cancelled")
        }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { exportLauncher.launch("vaultzero-export.vzc") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export encrypted CSV")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import encrypted CSV")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { passwordSafeLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import PasswordSafe database")
        }
    }

    pendingExportUri?.let { uri ->
        PasswordDialog(
            title = "Export password",
            onDismiss = { pendingExportUri = null },
            onConfirm = { password ->
                pendingExportUri = null
                viewModel.exportVault(uri, password) { msg -> onMessage(msg) }
            }
        )
    }

    pendingImportUri?.let { uri ->
        PasswordDialog(
            title = "Import password",
            onDismiss = { pendingImportUri = null },
            onConfirm = { password ->
                pendingImportUri = null
                viewModel.importVault(uri, password) { msg -> onMessage(msg) }
            }
        )
    }

    pendingPasswordSafeUri?.let { uri ->
        PasswordDialog(
            title = "PasswordSafe password",
            onDismiss = { pendingPasswordSafeUri = null },
            onConfirm = { password ->
                pendingPasswordSafeUri = null
                viewModel.importPasswordSafe(uri, password) { msg -> onMessage(msg) }
            }
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

private fun Context.unwrapToFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun BiometricRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                onValueChange = onToggle,
                role = Role.Switch
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.biometric_setup),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit
) {
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

@OptIn(ExperimentalMaterial3Api::class)
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
