package com.lomo.app.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel
    @Inject
    constructor(
        private val repository: MemoRepository,
        private val imageMapProvider: com.lomo.domain.provider.ImageMapProvider,
        val mapper: com.lomo.app.feature.main.MemoUiMapper,
    ) : ViewModel() {
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage

        // Track optimistically processed (restored/deleted) items with timestamp for clock-sync animations
        sealed interface TrashMutation {
            data class Delete(
                val timestamp: Long,
                val isHidden: Boolean = false,
            ) : TrashMutation
        }

        private val _pendingMutations = MutableStateFlow<Map<String, TrashMutation>>(emptyMap())
        val pendingMutations: StateFlow<Map<String, TrashMutation>> = _pendingMutations

        // Image map provided by shared ImageMapProvider
        val imageMap: StateFlow<Map<String, android.net.Uri>> = imageMapProvider.imageMap
        val imageDirectory: StateFlow<String?> =
            repository.getImageDirectory().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null,
            )

        // Filter out optimistically deleted items for smooth animation and map to UiModel
        // Filter out optimistically deleted items for smooth animation and map to UiModel
        @OptIn(ExperimentalCoroutinesApi::class)
        val pagedTrash: Flow<PagingData<Memo>> =
            repository
                .getDeletedMemos()
                .cachedIn(viewModelScope)

        private data class DataBundle(
            val rootDir: String?,
            val imageDir: String?,
            val imageMap: Map<String, android.net.Uri>,
        )

        val dateFormat: StateFlow<String> =
            repository
                .getDateFormat()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.lomo.data.util.PreferenceKeys.Defaults.DATE_FORMAT)

        val timeFormat: StateFlow<String> =
            repository
                .getTimeFormat()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.lomo.data.util.PreferenceKeys.Defaults.TIME_FORMAT)

        val rootDirectory: StateFlow<String?> =
            repository
                .getRootDirectory()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        fun restoreMemo(memo: Memo) {
            val timestamp = System.currentTimeMillis()
            // 1. Optimistic: track with timestamp for clock-sync (Visible Phase)
            _pendingMutations.update { it + (memo.id to TrashMutation.Delete(timestamp, isHidden = false)) }

            viewModelScope.launch {
                try {
                    // 2. Wait for UI animations (Fade 300ms)
                    delay(300)

                    // 3. Optimistic Filter (Collapse Item)
                    _pendingMutations.update { it + (memo.id to TrashMutation.Delete(timestamp, isHidden = true)) }

                    repository.restoreMemo(memo)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _pendingMutations.update { it - memo.id }
                    throw e
                } catch (e: Exception) {
                    // Rollback on error
                    _pendingMutations.update { it - memo.id }
                    _errorMessage.value = "Failed to restore memo: ${e.message}"
                } finally {
                    // Clear mutation after ensuring DB has updated
                    delay(3000)
                    _pendingMutations.update { it - memo.id }
                }
            }
        }

        fun deletePermanently(memo: Memo) {
            val timestamp = System.currentTimeMillis()
            // 1. Optimistic: track with timestamp for clock-sync (Visible Phase)
            _pendingMutations.update { it + (memo.id to TrashMutation.Delete(timestamp, isHidden = false)) }

            viewModelScope.launch {
                try {
                    // 2. Wait for UI animations (Fade 300ms)
                    delay(300)

                    // 3. Optimistic Filter (Collapse Item)
                    _pendingMutations.update { it + (memo.id to TrashMutation.Delete(timestamp, isHidden = true)) }

                    repository.deletePermanently(memo)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _pendingMutations.update { it - memo.id }
                    throw e
                } catch (e: Exception) {
                    // Rollback on error
                    _pendingMutations.update { it - memo.id }
                    _errorMessage.value = "Failed to delete memo: ${e.message}"
                } finally {
                    // Clear mutation after ensuring DB has updated
                    delay(3000)
                    _pendingMutations.update { it - memo.id }
                }
            }
        }

        fun clearError() {
            _errorMessage.value = null
        }
    }
