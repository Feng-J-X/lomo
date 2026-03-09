package com.lomo.app.feature.main

import com.lomo.app.BuildConfig
import com.lomo.domain.usecase.StartupMaintenanceUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MainStartupCoordinator
    @Inject
    constructor(
        private val startupMaintenanceUseCase: StartupMaintenanceUseCase,
    ) {
        suspend fun initializeRootDirectory(): String? = startupMaintenanceUseCase.initializeRootDirectory()

        suspend fun runDeferredStartupTasks(rootDir: String?) {
            startupMaintenanceUseCase.runDeferredStartupTasks(
                rootDir = rootDir,
                currentVersion = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
            )
        }

        fun observeRootDirectoryChanges(): Flow<String?> = startupMaintenanceUseCase.observeRootDirectoryChanges()

        fun observeVoiceDirectoryChanges(): Flow<String?> = startupMaintenanceUseCase.observeVoiceDirectoryChanges()
    }
