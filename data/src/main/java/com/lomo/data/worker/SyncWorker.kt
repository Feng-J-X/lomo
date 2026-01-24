package com.lomo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val memoSynchronizer: com.lomo.data.repository.MemoSynchronizer,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            Timber.d("SyncWorker started")
            return try {
                memoSynchronizer.refresh()
                Timber.d("SyncWorker success")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "SyncWorker failed")
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }

        companion object {
            const val WORK_NAME = "com.lomo.data.worker.SyncWorker"
        }
    }
