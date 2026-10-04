package com.vaultzero.app.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import com.vaultzero.app.presentation.util.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditEntryViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val passwordGenerator: PasswordGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditEntryUiState())
    val uiState: StateFlow<EditEntryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditEntryEvent>()
    val events: SharedFlow<EditEntryEvent> = _events.asSharedFlow()

    fun loadEntry(entryId: String?, defaultGroupId: String?) {
        viewModelScope.launch {
            val groups = repository.groups.first()
            if (entryId == null) {
                _uiState.value = EditEntryUiState(
                    groups = groups,
                    groupId = defaultGroupId ?: groups.firstOrNull()?.id
                )
            } else {
                val entry = repository.getEntry(entryId)
                _uiState.value = entry?.let {
                    EditEntryUiState(
                        id = it.id,
                        title = it.title,
                        username = it.username,
                        password = it.password,
                        url = it.url,
                        notes = it.notes,
                        groupId = it.groupId,
                        groups = groups,
                        isEdit = true
                    )
                } ?: EditEntryUiState(groups = groups)
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
            val defaults = repository.passwordDefaults.first()
            _uiState.value = _uiState.value.copy(password = passwordGenerator.generate(defaults))
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val entry = VaultEntry(
                id = state.id,
                title = state.title,
                username = state.username,
                password = state.password,
                url = state.url,
                notes = state.notes,
                groupId = state.groupId
            )
            repository.saveEntry(entry)
            _events.emit(EditEntryEvent.Saved)
        }
    }

    data class EditEntryUiState(
        val id: String = "",
        val title: String = "",
        val username: String = "",
        val password: String = "",
        val url: String = "",
        val notes: String = "",
        val groupId: String? = null,
        val groups: List<VaultGroup> = emptyList(),
        val isEdit: Boolean = false
    )

    sealed class EditEntryEvent {
        data object Saved : EditEntryEvent()
    }
}
