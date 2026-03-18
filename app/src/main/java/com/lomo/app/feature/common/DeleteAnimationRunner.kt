package com.lomo.app.feature.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal suspend fun runDeleteAnimationWithRollback(
    itemId: String,
    deletingIds: MutableStateFlow<Set<String>>,
    animationDelayMs: Long = 300L,
    mutation: suspend () -> Unit,
): Result<Unit> {
    return runDeleteAnimationWithRollback(
        itemIds = setOf(itemId),
        deletingIds = deletingIds,
        animationDelayMs = animationDelayMs,
        mutation = mutation,
    )
}

internal suspend fun runDeleteAnimationWithRollback(
    itemIds: Set<String>,
    deletingIds: MutableStateFlow<Set<String>>,
    animationDelayMs: Long = 300L,
    mutation: suspend () -> Unit,
): Result<Unit> {
    if (itemIds.isEmpty()) {
        return Result.success(Unit)
    }

    deletingIds.update { it + itemIds }
    delay(animationDelayMs)

    return try {
        mutation()
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        deletingIds.update { it - itemIds }
        throw cancellation
    } catch (throwable: Throwable) {
        deletingIds.update { it - itemIds }
        Result.failure(throwable)
    }
}
