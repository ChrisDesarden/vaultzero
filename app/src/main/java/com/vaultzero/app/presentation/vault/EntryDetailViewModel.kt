package com.vaultzero.app.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.clipboard.ClipboardHelper
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val clipboardHelper: ClipboardHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EntryDetailEvent>()
    val events: SharedFlow<EntryDetailEvent> = _events.asSharedFlow()

    fun loadEntry(entryId: String) {
        viewModelScope.launch {
            val entry = repository.getEntry(entryId)
            _uiState.value = _uiState.value.copy(entry = entry, isLoading = false)
        }
    }

    fun copyPassword(password: String) {
        clipboardHelper.copySensitive("password", password, 30)
        viewModelScope.launch {
            _events.emit(EntryDetailEvent.Copied)
        }
    }

    fun copyUsername(username: String) {
        clipboardHelper.copySensitive("username", username, 30)
        viewModelScope.launch {
            _events.emit(EntryDetailEvent.Copied)
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            _events.emit(EntryDetailEvent.Deleted)
        }
    }

    data class EntryDetailUiState(
        val entry: VaultEntry? = null,
        val isLoading: Boolean = true
    )

    sealed class EntryDetailEvent {
        data object Copied : EntryDetailEvent()
        data object Deleted : EntryDetailEvent()
    }
}
