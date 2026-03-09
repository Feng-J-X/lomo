package com.lomo.domain.usecase

import com.lomo.domain.model.StorageLocation
import com.lomo.domain.repository.MediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SaveImageUseCaseTest {
    private val mediaRepository: MediaRepository = mockk()
    private val useCase = SaveImageUseCase(mediaRepository)

    @Test
    fun `saveWithCacheSyncStatus returns success when both save and cache sync succeed`() =
        runTest {
            val source = StorageLocation("uri")
            val saved = StorageLocation("/images/a.jpg")
            coEvery { mediaRepository.importImage(source) } returns saved

            val result = useCase.saveWithCacheSyncStatus(source)

            assertEquals(SaveImageResult.SavedAndCacheSynced(saved), result)
            coVerify(exactly = 0) { mediaRepository.refreshImageLocations() }
        }

    @Test
    fun `saveWithCacheSyncStatus rethrows import failure and skips full refresh`() =
        runTest {
            val source = StorageLocation("uri")
            val failure = IllegalArgumentException("invalid source")
            coEvery { mediaRepository.importImage(source) } throws failure

            val thrown = runCatching { useCase.saveWithCacheSyncStatus(source) }.exceptionOrNull()

            assertSame(failure, thrown)
            coVerify(exactly = 0) { mediaRepository.refreshImageLocations() }
        }
}
