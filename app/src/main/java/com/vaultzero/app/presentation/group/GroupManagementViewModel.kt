package com.vaultzero.app.presentation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupManagementUiState())
    val uiState: StateFlow<GroupManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groups.collect { groups ->
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
    }

    fun onNewGroupNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(newGroupName = value)
    }

    fun addGroup() {
        viewModelScope.launch {
            val name = _uiState.value.newGroupName.trim()
            if (name.isNotBlank()) {
                repository.saveGroup(VaultGroup(id = "", name = name))
                _uiState.value = _uiState.value.copy(newGroupName = "")
            }
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            repository.deleteGroup(id)
        }
    }

    data class GroupManagementUiState(
        val groups: List<VaultGroup> = emptyList(),
        val newGroupName: String = ""
    )
}
