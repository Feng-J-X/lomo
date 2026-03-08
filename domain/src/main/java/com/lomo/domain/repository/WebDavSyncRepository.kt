package com.lomo.domain.repository

import com.lomo.domain.model.WebDavProvider
import com.lomo.domain.model.WebDavSyncResult
import com.lomo.domain.model.WebDavSyncState
import com.lomo.domain.model.WebDavSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

interface WebDavSyncRepository {
    fun isWebDavSyncEnabled(): Flow<Boolean>

    fun getProvider(): Flow<WebDavProvider>

    fun getBaseUrl(): Flow<String?>

    fun getEndpointUrl(): Flow<String?>

    fun getUsername(): Flow<String?>

    fun getAutoSyncEnabled(): Flow<Boolean>

    fun getAutoSyncInterval(): Flow<String>

    fun getSyncOnRefreshEnabled(): Flow<Boolean>

    fun observeLastSyncTimeMillis(): Flow<Long?>

    fun observeLastSyncInstant(): Flow<Instant?> = observeLastSyncTimeMillis().map { value -> value?.let(Instant::ofEpochMilli) }

    suspend fun setWebDavSyncEnabled(enabled: Boolean)

    suspend fun setProvider(provider: WebDavProvider)

    suspend fun setBaseUrl(url: String)

    suspend fun setEndpointUrl(url: String)

    suspend fun setUsername(username: String)

    suspend fun setPassword(password: String)

    suspend fun isPasswordConfigured(): Boolean

    suspend fun setAutoSyncEnabled(enabled: Boolean)

    suspend fun setAutoSyncInterval(interval: String)

    suspend fun setSyncOnRefreshEnabled(enabled: Boolean)

    suspend fun sync(): WebDavSyncResult

    suspend fun getStatus(): WebDavSyncStatus

    suspend fun testConnection(): WebDavSyncResult

    fun syncState(): Flow<WebDavSyncState>
}
