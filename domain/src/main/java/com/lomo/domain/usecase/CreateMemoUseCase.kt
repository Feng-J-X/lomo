package com.lomo.domain.usecase

import com.lomo.domain.repository.MemoRepository
import javax.inject.Inject

class CreateMemoUseCase
    @Inject
    constructor(
        private val repository: MemoRepository,
    ) {
        suspend operator fun invoke(content: String) {
            repository.saveMemo(content)
        }
    }
