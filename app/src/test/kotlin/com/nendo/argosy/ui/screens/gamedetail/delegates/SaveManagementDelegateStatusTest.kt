package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class SaveManagementDelegateStatusTest {

    private val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
    private val saveCacheManager = mockk<SaveCacheManager>(relaxed = true)

    private val delegate = SaveManagementDelegate(
        context = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        activeSaveRepository = activeSaveRepository,
        saveSyncDao = mockk(relaxed = true),
        savePathAuthority = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        builtinSaveBase = mockk(relaxed = true),
        saveCacheManager = saveCacheManager,
        saveSyncRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        getUnifiedSavesUseCase = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        retroArchPathResolver = mockk(relaxed = true),
        coreVersionExtractor = mockk(relaxed = true),
        saveChannelDelegate = mockk(relaxed = true)
    )

    @Test
    fun `a channel switch activates that channel's own newest row even when another channel shares its time`() = runBlocking {
        val sharedTime = Instant.parse("2026-10-05T20:45:55Z")
        val mainGame = SaveCacheEntity(
            id = 41, gameId = 3, emulatorId = "argosy", cachedAt = sharedTime,
            saveSize = 1, cachePath = "x", channelName = "Main Game"
        )
        coEvery { saveCacheManager.getMostRecentInChannel(3, "Main Game") } returns mainGame

        delegate.loadSaveStatusInfo(3, "argosy", "Main Game", activeSaveTimestamp = null, includeServer = false)

        coVerify { activeSaveRepository.activateCache(3, 41) }
    }
}
