package com.vaultzero.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.entries, repository.groups) { entries, groups ->
                Pair(entries, groups)
            }.collect { (entries, groups) ->
                applyFilter(_uiState.value.query, entries, groups)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(query = query)
            applyFilter(query, repository.entries.value, repository.groups.value)
        }
    }

    fun lock() {
        viewModelScope.launch { repository.lock() }
    }

    private fun applyFilter(query: String, entries: List<VaultEntry>, groups: List<VaultGroup>) {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) entries else entries.filter {
            it.title.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.url.lowercase().contains(q) ||
                groups.find { g -> g.id == it.groupId }?.name?.lowercase()?.contains(q) == true
        }
        _uiState.value = _uiState.value.copy(
            entries = filtered,
            groups = groups,
            isEmpty = entries.isEmpty()
        )
    }

    data class HomeUiState(
        val entries: List<VaultEntry> = emptyList(),
        val groups: List<VaultGroup> = emptyList(),
        val query: String = "",
        val isEmpty: Boolean = false
    )
}
