package com.lomo.data.repository

import com.lomo.data.local.dao.WebDavSyncMetadataDao
import com.lomo.data.local.datastore.LomoDataStore
import com.lomo.data.source.FileDataSource
import com.lomo.data.webdav.WebDavClient
import com.lomo.data.webdav.WebDavClientFactory
import com.lomo.data.webdav.LocalMediaSyncStore
import com.lomo.data.webdav.WebDavCredentialStore
import com.lomo.data.webdav.WebDavEndpointResolver
import com.lomo.domain.model.WebDavProvider
import com.lomo.domain.model.WebDavSyncResult
import com.lomo.domain.model.WebDavSyncState
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavSyncRepositoryImplTest {
    @MockK(relaxed = true)
    private lateinit var dataStore: LomoDataStore

    @MockK(relaxed = true)
    private lateinit var credentialStore: WebDavCredentialStore

    @MockK(relaxed = true)
    private lateinit var endpointResolver: WebDavEndpointResolver

    @MockK(relaxed = true)
    private lateinit var clientFactory: WebDavClientFactory

    @MockK(relaxed = true)
    private lateinit var client: WebDavClient

    @MockK(relaxed = true)
    private lateinit var fileDataSource: FileDataSource

    @MockK(relaxed = true)
    private lateinit var localMediaSyncStore: LocalMediaSyncStore

    @MockK(relaxed = true)
    private lateinit var metadataDao: WebDavSyncMetadataDao

    @MockK(relaxed = true)
    private lateinit var memoSynchronizer: MemoSynchronizer

    private lateinit var planner: WebDavSyncPlanner
    private lateinit var repository: WebDavSyncRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        planner = WebDavSyncPlanner()
        every { dataStore.webDavSyncEnabled } returns flowOf(true)
        every { dataStore.webDavProvider } returns flowOf(WebDavProvider.NUTSTORE.name.lowercase())
        every { dataStore.webDavBaseUrl } returns flowOf(null)
        every { dataStore.webDavEndpointUrl } returns flowOf("https://dav.example.com/root/")
        every { dataStore.webDavUsername } returns flowOf("alice")
        every { dataStore.webDavLastSyncTime } returns flowOf(0L)
        every { dataStore.webDavAutoSyncEnabled } returns flowOf(false)
        every { dataStore.webDavAutoSyncInterval } returns flowOf("24h")
        every { dataStore.webDavSyncOnRefresh } returns flowOf(false)
        every { endpointResolver.resolve(WebDavProvider.NUTSTORE, null, "https://dav.example.com/root/", "alice") } returns "https://dav.example.com/root/"
        every { credentialStore.getUsername() } returns null
        every { credentialStore.getPassword() } returns "secret"
        every { clientFactory.create("https://dav.example.com/root/", "alice", "secret") } returns client
        repository =
            WebDavSyncRepositoryImpl(
                dataStore = dataStore,
                credentialStore = credentialStore,
                endpointResolver = endpointResolver,
                clientFactory = clientFactory,
                fileDataSource = fileDataSource,
                localMediaSyncStore = localMediaSyncStore,
                metadataDao = metadataDao,
                memoSynchronizer = memoSynchronizer,
                planner = planner,
            )
    }

    @Test
    fun `test connection success keeps sync state idle`() =
        runTest {
            val result = repository.testConnection()

            assertTrue(result is WebDavSyncResult.Success)
            assertEquals(WebDavSyncState.Idle, repository.syncState().first())
            verify(exactly = 1) { client.testConnection() }
        }

    @Test
    fun `test connection failure does not overwrite sync state`() =
        runTest {
            val failure = IllegalStateException("boom")
            every { client.testConnection() } throws failure

            val result = repository.testConnection()

            assertTrue(result is WebDavSyncResult.Error)
            assertEquals(WebDavSyncState.Idle, repository.syncState().first())
        }
}
