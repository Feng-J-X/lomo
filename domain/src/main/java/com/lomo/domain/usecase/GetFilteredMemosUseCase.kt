package com.lomo.domain.usecase

import androidx.paging.PagingData
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetFilteredMemosUseCase @Inject constructor(private val repository: MemoRepository) {
    operator fun invoke(query: String, tag: String?): Flow<PagingData<Memo>> = when {
        tag != null -> repository.getMemosByTag(tag)
        query.isNotBlank() -> repository.searchMemos(query)
        else -> repository.getAllMemos()
    }
}
