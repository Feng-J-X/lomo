package com.lomo.domain.usecase

import com.lomo.domain.repository.AppVersionRepository
import com.lomo.domain.repository.AudioPlaybackController
import com.lomo.domain.repository.DirectorySettingsRepository
import com.lomo.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class StartupMaintenanceUseCase
    constructor(
        private val directorySettingsRepository: DirectorySettingsRepository,
        private val mediaRepository: MediaRepository,
        private val initializeWorkspaceUseCase: InitializeWorkspaceUseCase,
        private val syncAndRebuildUseCase: SyncAndRebuildUseCase,
        private val appVersionRepository: AppVersionRepository,
        private val audioPlaybackController: AudioPlaybackController,
    ) {
        suspend fun initializeRootDirectory(): String? {
            val rootLocation = initializeWorkspaceUseCase.currentRootLocation()
            audioPlaybackController.setRootLocation(rootLocation)
            return rootLocation?.raw
        }

        suspend fun runDeferredStartupTasks(
            rootDir: String?,
            currentVersion: String,
        ) {
            warmImageCacheOnStartup()
            resyncCachesIfAppVersionChanged(rootDir = rootDir, currentVersion = currentVersion)
        }

        fun observeRootDirectoryChanges(): Flow<String?> =
            directorySettingsRepository
                .observeRootLocation()
                .drop(1)
                .onEach { location ->
                    audioPlaybackController.setRootLocation(location)
                }.map { it?.raw }

        fun observeVoiceDirectoryChanges(): Flow<String?> =
            directorySettingsRepository
                .observeVoiceLocation()
                .onEach { voiceLocation ->
                    audioPlaybackController.setVoiceLocation(voiceLocation)
                }.map { it?.raw }

        private suspend fun warmImageCacheOnStartup() {
            try {
                mediaRepository.refreshImageLocations()
            } catch (_: Exception) {
                // Best-effort cache warm-up.
            }
        }

        private suspend fun resyncCachesIfAppVersionChanged(
            rootDir: String?,
            currentVersion: String,
        ) {
            val lastVersion = appVersionRepository.getLastAppVersionOnce()
            if (lastVersion == currentVersion) return

            if (rootDir != null) {
                try {
                    syncAndRebuildUseCase(forceSync = false)
                } catch (_: Exception) {
                    // Best-effort refresh.
                }

                try {
                    mediaRepository.refreshImageLocations()
                } catch (_: Exception) {
                    // Best-effort cache rebuild.
                }
            }

            appVersionRepository.updateLastAppVersion(currentVersion)
        }
    }
