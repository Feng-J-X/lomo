package com.lomo.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lomo.data.local.datastore.LomoDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavSyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: LomoDataStore,
    ) {
        suspend fun reschedule() {
            val enabled = dataStore.webDavSyncEnabled.first()
            val autoSyncEnabled = dataStore.webDavAutoSyncEnabled.first()
            val workManager = WorkManager.getInstance(context)
            if (!enabled || !autoSyncEnabled) {
                cancel()
                return
            }

            val interval = dataStore.webDavAutoSyncInterval.first()
            val duration = parseInterval(interval)
            val request =
                PeriodicWorkRequestBuilder<WebDavSyncWorker>(duration)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()

            workManager.enqueueUniquePeriodicWork(
                WebDavSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
            Timber.d("WebDAV auto-sync scheduled with interval: %s", interval)
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WebDavSyncWorker.WORK_NAME)
            Timber.d("WebDAV auto-sync cancelled")
        }

        private fun parseInterval(interval: String): Duration =
            when (interval) {
                "30min" -> Duration.ofMinutes(30)
                "1h" -> Duration.ofHours(1)
                "6h" -> Duration.ofHours(6)
                "12h" -> Duration.ofHours(12)
                "24h" -> Duration.ofHours(24)
                else -> Duration.ofHours(1)
            }
    }
