package com.lomo.app.feature.settings

import com.lomo.domain.model.PreferenceDefaults
import com.lomo.domain.model.SyncBackendType
import com.lomo.domain.model.WebDavProvider
import com.lomo.domain.model.WebDavSyncResult
import com.lomo.domain.model.WebDavSyncState
import com.lomo.domain.repository.SyncPolicyRepository
import com.lomo.domain.repository.WebDavSyncRepository
import com.lomo.domain.usecase.SyncAndRebuildUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SettingsWebDavConnectionTestState {
    data object Idle : SettingsWebDavConnectionTestState

    data object Testing : SettingsWebDavConnectionTestState

    data class Success(
        val message: String,
    ) : SettingsWebDavConnectionTestState

    data class Error(
        val message: String,
    ) : SettingsWebDavConnectionTestState
}

class SettingsWebDavCoordinator(
    private val webDavSyncRepository: WebDavSyncRepository,
    private val syncPolicyRepository: SyncPolicyRepository,
    private val syncAndRebuildUseCase: SyncAndRebuildUseCase,
    scope: CoroutineScope,
) {
    val webDavSyncEnabled: StateFlow<Boolean> =
        webDavSyncRepository
            .isWebDavSyncEnabled()
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                PreferenceDefaults.WEBDAV_SYNC_ENABLED,
            )

    val webDavProvider: StateFlow<WebDavProvider> =
        webDavSyncRepository
            .getProvider()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), WebDavProvider.NUTSTORE)

    val webDavBaseUrl: StateFlow<String> =
        webDavSyncRepository
            .getBaseUrl()
            .map { it ?: "" }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val webDavEndpointUrl: StateFlow<String> =
        webDavSyncRepository
            .getEndpointUrl()
            .map { it ?: "" }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val webDavUsername: StateFlow<String> =
        webDavSyncRepository
            .getUsername()
            .map { it ?: "" }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    private val _passwordConfigured = MutableStateFlow(false)
    val passwordConfigured: StateFlow<Boolean> = _passwordConfigured.asStateFlow()

    val webDavAutoSyncEnabled: StateFlow<Boolean> =
        webDavSyncRepository
            .getAutoSyncEnabled()
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                PreferenceDefaults.WEBDAV_AUTO_SYNC_ENABLED,
            )

    val webDavAutoSyncInterval: StateFlow<String> =
        webDavSyncRepository
            .getAutoSyncInterval()
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                PreferenceDefaults.WEBDAV_AUTO_SYNC_INTERVAL,
            )

    val webDavSyncOnRefreshEnabled: StateFlow<Boolean> =
        webDavSyncRepository
            .getSyncOnRefreshEnabled()
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                PreferenceDefaults.WEBDAV_SYNC_ON_REFRESH,
            )

    val webDavLastSyncTime: StateFlow<Long> =
        webDavSyncRepository
            .observeLastSyncTimeMillis()
            .map { it ?: 0L }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0L)

    val webDavSyncState: StateFlow<WebDavSyncState> =
        webDavSyncRepository
            .syncState()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), WebDavSyncState.Idle)

    private val _connectionTestState = MutableStateFlow<SettingsWebDavConnectionTestState>(SettingsWebDavConnectionTestState.Idle)
    val connectionTestState: StateFlow<SettingsWebDavConnectionTestState> = _connectionTestState.asStateFlow()

    suspend fun refreshPasswordConfigured(): String? =
        runWithError("Failed to read WebDAV password state") {
            _passwordConfigured.value = webDavSyncRepository.isPasswordConfigured()
        }

    suspend fun updateWebDavSyncEnabled(enabled: Boolean): String? =
        runWithError("Failed to update WebDAV sync setting") {
            syncPolicyRepository.setRemoteSyncBackend(if (enabled) SyncBackendType.WEBDAV else SyncBackendType.NONE)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

    suspend fun updateWebDavProvider(provider: WebDavProvider): String? =
        runWithError("Failed to update WebDAV provider") {
            webDavSyncRepository.setProvider(provider)
        }

    suspend fun updateWebDavBaseUrl(url: String): String? =
        runWithError("Failed to update WebDAV base URL") {
            webDavSyncRepository.setBaseUrl(url)
        }

    suspend fun updateWebDavEndpointUrl(url: String): String? =
        runWithError("Failed to update WebDAV endpoint") {
            webDavSyncRepository.setEndpointUrl(url)
        }

    suspend fun updateWebDavUsername(username: String): String? =
        runWithError("Failed to update WebDAV username") {
            webDavSyncRepository.setUsername(username)
        }

    suspend fun updateWebDavPassword(password: String): String? =
        runWithError("Failed to update WebDAV password") {
            webDavSyncRepository.setPassword(password)
            _passwordConfigured.value = password.isNotBlank()
        }

    suspend fun updateWebDavAutoSyncEnabled(enabled: Boolean): String? =
        runWithError("Failed to update WebDAV auto-sync setting") {
            webDavSyncRepository.setAutoSyncEnabled(enabled)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

    suspend fun updateWebDavAutoSyncInterval(interval: String): String? =
        runWithError("Failed to update WebDAV auto-sync interval") {
            webDavSyncRepository.setAutoSyncInterval(interval)
            syncPolicyRepository.applyRemoteSyncPolicy()
        }

    suspend fun updateWebDavSyncOnRefresh(enabled: Boolean): String? =
        runWithError("Failed to update WebDAV sync-on-refresh setting") {
            webDavSyncRepository.setSyncOnRefreshEnabled(enabled)
        }

    suspend fun triggerWebDavSyncNow(): String? =
        runWithError("Failed to run WebDAV sync") {
            syncAndRebuildUseCase(forceSync = true)
        }

    suspend fun testWebDavConnection(): String? =
        try {
            _connectionTestState.value = SettingsWebDavConnectionTestState.Testing
            val result = webDavSyncRepository.testConnection()
            _connectionTestState.value =
                when (result) {
                    is WebDavSyncResult.Success -> SettingsWebDavConnectionTestState.Success(result.message)
                    is WebDavSyncResult.Error -> SettingsWebDavConnectionTestState.Error(sanitizeMessage(result.message, "Failed to test WebDAV connection"))
                    WebDavSyncResult.NotConfigured -> SettingsWebDavConnectionTestState.Error("WebDAV sync is not configured")
                }
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val message = sanitizeMessage(throwable.message, "Failed to test WebDAV connection")
            _connectionTestState.value = SettingsWebDavConnectionTestState.Error(message)
            message
        }

    fun resetConnectionTestState() {
        _connectionTestState.value = SettingsWebDavConnectionTestState.Idle
    }

    fun isValidWebDavUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.startsWith("https://") || trimmed.startsWith("http://") || !trimmed.contains("://")
    }

    private suspend fun runWithError(
        fallbackMessage: String,
        action: suspend () -> Unit,
    ): String? =
        try {
            action()
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            sanitizeMessage(throwable.message, fallbackMessage)
        }

    private fun sanitizeMessage(
        rawMessage: String?,
        fallbackMessage: String,
    ): String {
        val normalized = rawMessage?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return normalized.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: fallbackMessage
    }
}
