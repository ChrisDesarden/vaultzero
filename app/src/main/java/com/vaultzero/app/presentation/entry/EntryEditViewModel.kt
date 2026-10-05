package com.vaultzero.app.presentation.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.usecase.DeleteEntryUseCase
import com.vaultzero.app.domain.usecase.GeneratePasswordUseCase
import com.vaultzero.app.domain.usecase.GetEntryUseCase
import com.vaultzero.app.domain.usecase.GetGroupsUseCase
import com.vaultzero.app.domain.usecase.GetSettingsUseCase
import com.vaultzero.app.domain.usecase.ObserveGroupsUseCase
import com.vaultzero.app.domain.usecase.SaveEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EntryEditViewModel @Inject constructor(
    private val getEntry: GetEntryUseCase,
    private val saveEntry: SaveEntryUseCase,
    private val deleteEntry: DeleteEntryUseCase,
    private val observeGroups: ObserveGroupsUseCase,
    private val getSettings: GetSettingsUseCase,
    private val generatePassword: GeneratePasswordUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryEditUiState())
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EntryEditEvent>()
    val events: SharedFlow<EntryEditEvent> = _events.asSharedFlow()

    fun loadEntry(entryId: String?) {
        viewModelScope.launch {
            val groups = observeGroups().first()
            val settings = getSettings().first()
            if (entryId == null) {
                _uiState.value = EntryEditUiState(
                    groups = groups,
                    groupId = groups.firstOrNull()?.id,
                    passwordDefaults = mapSettings(settings)
                )
            } else {
                val entry = getEntry(entryId)
                _uiState.value = entry?.let {
                    EntryEditUiState(
                        id = it.id,
                        title = it.title,
                        username = it.username,
                        password = it.password,
                        url = it.url,
                        notes = it.notes,
                        groupId = it.groupId,
                        groups = groups,
                        isEdit = true,
                        passwordDefaults = mapSettings(settings)
                    )
                } ?: EntryEditUiState(groups = groups)
            }
        }
    }

    fun onTitleChanged(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun onUsernameChanged(value: String) { _uiState.value = _uiState.value.copy(username = value) }
    fun onPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(password = value) }
    fun onUrlChanged(value: String) { _uiState.value = _uiState.value.copy(url = value) }
    fun onNotesChanged(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun onGroupChanged(groupId: String?) { _uiState.value = _uiState.value.copy(groupId = groupId) }

    fun generatePassword() {
        viewModelScope.launch {
            val cfg = com.vaultzero.app.crypto.CryptoManager.PasswordConfig(
                length = _uiState.value.passwordDefaults.length,
                includeUppercase = _uiState.value.passwordDefaults.includeUppercase,
                includeLowercase = _uiState.value.passwordDefaults.includeLowercase,
                includeNumbers = _uiState.value.passwordDefaults.includeNumbers,
                includeSymbols = _uiState.value.passwordDefaults.includeSymbols,
                excludeAmbiguous = _uiState.value.passwordDefaults.excludeAmbiguous
            )
            _uiState.value = _uiState.value.copy(password = generatePassword(cfg).concatToString())
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val entry = VaultEntry(
                id = state.id.ifBlank { UUID.randomUUID().toString() },
                title = state.title,
                username = state.username,
                password = state.password,
                url = state.url,
                notes = state.notes,
                groupId = state.groupId
            )
            saveEntry(entry)
            _events.emit(EntryEditEvent.Saved)
        }
    }

    fun delete() {
        viewModelScope.launch {
            _uiState.value.id.takeIf { it.isNotBlank() }?.let { id ->
                deleteEntry(id)
                _events.emit(EntryEditEvent.Deleted)
            }
        }
    }

    private fun mapSettings(settings: com.vaultzero.app.domain.model.VaultSettings): PasswordGeneratorDefaults {
        return PasswordGeneratorDefaults(
            length = settings.passwordLength,
            includeUppercase = settings.includeUppercase,
            includeLowercase = settings.includeLowercase,
            includeNumbers = settings.includeDigits,
            includeSymbols = settings.includeSymbols,
            excludeAmbiguous = settings.excludeAmbiguous
        )
    }

    data class EntryEditUiState(
        val id: String = "",
        val title: String = "",
        val username: String = "",
        val password: String = "",
        val url: String = "",
        val notes: String = "",
        val groupId: String? = null,
        val groups: List<VaultGroup> = emptyList(),
        val isEdit: Boolean = false,
        val passwordDefaults: PasswordGeneratorDefaults = PasswordGeneratorDefaults()
    )

    sealed class EntryEditEvent {
        data object Saved : EntryEditEvent()
        data object Deleted : EntryEditEvent()
    }
}
