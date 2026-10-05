package com.vaultzero.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.usecase.LockVaultUseCase
import com.vaultzero.app.domain.usecase.ObserveEntriesUseCase
import com.vaultzero.app.domain.usecase.ObserveGroupsUseCase
import com.vaultzero.app.domain.usecase.SearchEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeEntries: ObserveEntriesUseCase,
    observeGroups: ObserveGroupsUseCase,
    private val searchEntries: SearchEntriesUseCase,
    private val lockVault: LockVaultUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _filteredEntries = _query.flatMapLatest { q ->
        if (q.isBlank()) {
            observeEntries()
        } else {
            searchEntries(q)
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val collator: Collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }

    init {
        viewModelScope.launch {
            combine(
                _filteredEntries,
                observeGroups(),
                _query
            ) { entries, groups, query ->
                val sorted = entries.sortedWith(compareBy(collator) { it.title.trim() })
                val sections = sorted.groupBy { entry ->
                    val first = entry.title.trim().firstOrNull()
                    if (first?.isLetter() == true) first.uppercaseChar().toString() else "#"
                }.toSortedMap()
                HomeUiState(
                    entries = entries,
                    groups = groups,
                    query = query,
                    isEmpty = entries.isEmpty() && query.isBlank(),
                    alphabeticalSections = sections.map { (letter, list) ->
                        AlphabeticalSection(letter, list)
                    }
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onSearchQueryChanged(value: String) {
        _query.value = value
    }

    fun lock() {
        viewModelScope.launch { lockVault() }
    }

    data class HomeUiState(
        val entries: List<VaultEntry> = emptyList(),
        val groups: List<VaultGroup> = emptyList(),
        val query: String = "",
        val isEmpty: Boolean = false,
        val alphabeticalSections: List<AlphabeticalSection> = emptyList()
    )

    data class AlphabeticalSection(
        val letter: String,
        val entries: List<VaultEntry>
    )
}
