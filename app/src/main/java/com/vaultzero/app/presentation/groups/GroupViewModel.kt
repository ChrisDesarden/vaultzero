package com.vaultzero.app.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.usecase.DeleteGroupUseCase
import com.vaultzero.app.domain.usecase.ObserveGroupsUseCase
import com.vaultzero.app.domain.usecase.SaveGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    observeGroups: ObserveGroupsUseCase,
    private val saveGroup: SaveGroupUseCase,
    private val deleteGroup: DeleteGroupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun onNewGroupNameChanged(value: String) {
        _uiState.update { it.copy(newGroupName = value) }
    }

    fun addGroup() {
        viewModelScope.launch {
            val name = _uiState.value.newGroupName.trim()
            if (name.isNotBlank()) {
                saveGroup(VaultGroup(id = UUID.randomUUID().toString(), name = name))
                _uiState.update { it.copy(newGroupName = "") }
            }
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch { deleteGroup(id) }
    }

    data class GroupUiState(
        val groups: List<VaultGroup> = emptyList(),
        val newGroupName: String = ""
    )
}
