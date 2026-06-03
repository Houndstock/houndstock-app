package com.houndstock.app.ui.screens.schemes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houndstock.app.data.network.dto.SchemeSummaryDto
import com.houndstock.app.data.repository.SchemesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** UI state for the schemes list screen. */
sealed interface SchemesListUiState {
    data object Idle : SchemesListUiState
    data object Loading : SchemesListUiState
    data class Success(val results: List<SchemeSummaryDto>) : SchemesListUiState
    data class Error(val message: String) : SchemesListUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SchemesListViewModel @Inject constructor(
    private val repo: SchemesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SchemesListUiState>(SchemesListUiState.Idle)
    val state: StateFlow<SchemesListUiState> = _state.asStateFlow()

    init {
        // Debounce typing so we don't hammer the backend on every keystroke.
        // Backend's mfapi.in proxy is rate-limited.
        _query
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.trim().length >= 2 || it.isEmpty() }
            .onEach { q ->
                if (q.trim().length < 2) {
                    _state.value = SchemesListUiState.Idle
                } else {
                    runSearch(q.trim())
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    private suspend fun runSearch(q: String) {
        _state.value = SchemesListUiState.Loading
        repo.search(q).fold(
            onSuccess = { hits ->
                _state.value = if (hits.isEmpty()) {
                    SchemesListUiState.Success(emptyList())
                } else {
                    SchemesListUiState.Success(hits)
                }
            },
            onFailure = { e ->
                _state.value = SchemesListUiState.Error(
                    e.message ?: "Something went wrong fetching schemes."
                )
            }
        )
    }
}
