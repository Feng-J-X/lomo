package com.lomo.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.lomo.app.feature.main.MemoUiModel
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import com.lomo.ui.util.stateInViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// SearchUiState removed - using PagingData direct flow

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val repository: MemoRepository,
        private val mapper: com.lomo.app.feature.main.MemoUiMapper,
        private val imageMapProvider: com.lomo.domain.provider.ImageMapProvider,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        val rootDirectory: StateFlow<String?> =
            repository
                .getRootDirectory()
                .stateInViewModel(viewModelScope, null) // Using extension

        val imageDirectory: StateFlow<String?> =
            repository
                .getImageDirectory()
                .stateInViewModel(viewModelScope, null)

        val dateFormat: StateFlow<String> =
            repository
                .getDateFormat()
                .stateInViewModel(viewModelScope, com.lomo.data.util.PreferenceKeys.Defaults.DATE_FORMAT)

        val timeFormat: StateFlow<String> =
            repository
                .getTimeFormat()
                .stateInViewModel(viewModelScope, com.lomo.data.util.PreferenceKeys.Defaults.TIME_FORMAT)

        // Optimistic UI: Pending mutations
        private val _pendingMutations = MutableStateFlow<Map<String, Mutation>>(emptyMap())

        sealed interface Mutation {
            data class Delete(
                val isHidden: Boolean = false,
            ) : Mutation
        }

        // Image map loading removed - using shared ImageMapProvider

        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        val pagedResults: kotlinx.coroutines.flow.Flow<PagingData<com.lomo.app.feature.main.MemoUiModel>> =
            kotlinx.coroutines.flow
                .combine(
                    _searchQuery.debounce(300),
                    rootDirectory,
                    imageDirectory,
                    imageMapProvider.imageMap, // Use shared ImageMapProvider
                    _pendingMutations,
                ) { query, root, imageRoot, imageMap, mutations ->
                    DataBundle(query, root, imageRoot, imageMap, mutations)
                }.flatMapLatest { bundle ->
                    if (bundle.query.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(PagingData.empty<MemoUiModel>())
                    } else {
                        repository.searchMemos(bundle.query).map { pagingData ->
                            pagingData
                                .map { memo ->
                                    mapper.mapToUiModel(
                                        memo = memo,
                                        rootPath = bundle.root,
                                        imagePath = bundle.imageRoot,
                                        imageMap = bundle.imageMap,
                                        isDeleting = bundle.mutations[memo.id] is Mutation.Delete,
                                    )
                                }.filter { memo ->
                                    val mutation = bundle.mutations[memo.memo.id]
                                    !(mutation is Mutation.Delete && mutation.isHidden)
                                }
                        }
                    }
                }.catch { e ->
                    e.printStackTrace()
                    emit(PagingData.empty<MemoUiModel>())
                }.cachedIn(viewModelScope)

        private data class DataBundle(
            val query: String,
            val root: String?,
            val imageRoot: String?,
            val imageMap: Map<String, android.net.Uri>,
            val mutations: Map<String, Mutation>,
        )

        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        fun deleteMemo(memo: Memo) {
            // 1. Optimistic Delete (Visible Phase)
            _pendingMutations.update { it + (memo.id to Mutation.Delete(isHidden = false)) }

            viewModelScope.launch {
                try {
                    // 2. Wait for UI animations (300ms)
                    delay(300)

                    // 3. Optimistic Filter (Collapse Item)
                    _pendingMutations.update { it + (memo.id to Mutation.Delete(isHidden = true)) }

                    repository.deleteMemo(memo)
                } catch (e: Exception) {
                    _pendingMutations.update { it - memo.id }
                } finally {
                    // Keep the mutation mask for 3s to ensure Paging stream reflects the deletion
                    delay(3000)
                    _pendingMutations.update { it - memo.id }
                }
            }
        }
    }
