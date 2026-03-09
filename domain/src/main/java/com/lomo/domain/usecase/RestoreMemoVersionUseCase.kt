package com.lomo.domain.usecase

import com.lomo.domain.model.Memo
import com.lomo.domain.model.MemoVersion
import com.lomo.domain.repository.MemoRepository

class RestoreMemoVersionUseCase
    constructor(
        private val memoRepository: MemoRepository,
    ) {
        suspend operator fun invoke(
            memo: Memo,
            version: MemoVersion,
        ) {
            memoRepository.updateMemo(memo, version.memoContent)
        }
    }
