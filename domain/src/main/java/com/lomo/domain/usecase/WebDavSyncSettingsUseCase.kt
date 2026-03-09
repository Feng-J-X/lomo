package com.lomo.domain.usecase

import com.lomo.domain.model.SyncBackendType
import com.lomo.domain.model.WebDavProvider
import com.lomo.domain.model.WebDavSyncResult
import com.lomo.domain.model.WebDavSyncState
import com.lomo.domain.repository.SyncPolicyRepository
import com.lomo.domain.repository.WebDavSyncRepository
import kotlinx.coroutines.flow.Flow

class WebDavSyncSettingsUseCase
    constructor(
        private val webDavSyncRepository: WebDavSyncRepository,
        private val syncPolicyRepository: SyncPolicyRepository,
        private val syncAndRebuildUseCase: SyncAndRebuildUseCase,
    ) {
        fun observeWebDavSyncEnabled(): Flow<Boolean> = webDavSyncRepository.isWebDavSyncEnabled()

        fun observeProvider(): Flow<WebDavProvider> = webDavSyncRepository.getProvider()

        fun observeBaseUrl(): Flow<String?> = webDavSyncRepository.getBaseUrl()

        fun observeEndpointUrl(): Flow<String?> = webDavSyncRepository.getEndpointUrl()

        fun observeUsername(): Flow<String?> = webDavSyncRepository.getUsername()

        fun observeAutoSyncEnabled(): Flow<Boolean> = webDavSyncRepository.getAutoSyncEnabled()

        fun observeAutoSyncInterval(): Flow<String> = webDavSyncRepository.getAutoSyncInterval()

        fun observeSyncOnRefreshEnabled(): Flow<Boolean> = webDavSyncRepository.getSyncOnRefreshEnabled()

        fun observeLastSyncTimeMillis(): Flow<Long?> = webDavSyncRepository.observeLastSyncTimeMillis()

        fun observeSyncState(): Flow<WebDavSyncState> = webDavSyncRepository.syncState()

        suspend fun isPasswordConfigured(): Boolean = webDavSyncRepository.isPasswordConfigured()

        suspend fun updateWebDavSyncEnabled(enabled: Boolean) {
            syncPolicyRepository.setRemoteSyncBackend(if (enabled) SyncBackendType.WEBDAV else SyncBackendType.NONE)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

        suspend fun updateProvider(provider: WebDavProvider) {
            webDavSyncRepository.setProvider(provider)
        }

        suspend fun updateBaseUrl(url: String) {
            webDavSyncRepository.setBaseUrl(url)
        }

        suspend fun updateEndpointUrl(url: String) {
            webDavSyncRepository.setEndpointUrl(url)
        }

        suspend fun updateUsername(username: String) {
            webDavSyncRepository.setUsername(username)
        }

        suspend fun updatePassword(password: String) {
            webDavSyncRepository.setPassword(password)
        }

        suspend fun updateAutoSyncEnabled(enabled: Boolean) {
            webDavSyncRepository.setAutoSyncEnabled(enabled)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

        suspend fun updateAutoSyncInterval(interval: String) {
            webDavSyncRepository.setAutoSyncInterval(interval)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

        suspend fun updateSyncOnRefreshEnabled(enabled: Boolean) {
            webDavSyncRepository.setSyncOnRefreshEnabled(enabled)
        }

        suspend fun triggerSyncNow() {
            syncAndRebuildUseCase(forceSync = true)
        }

        suspend fun testConnection(): WebDavSyncResult = webDavSyncRepository.testConnection()
    }
