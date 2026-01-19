package com.lomo.domain.usecase

import androidx.paging.PagingData
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetMemosUseCase @Inject constructor(private val repository: MemoRepository) {
    operator fun invoke(): Flow<PagingData<Memo>> {
        return repository.getAllMemos()
    }
}
