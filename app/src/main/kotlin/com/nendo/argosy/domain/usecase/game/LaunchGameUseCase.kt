package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.data.wallpaper.LockScreenArtManager
import com.nendo.argosy.domain.model.LaunchStep
import javax.inject.Inject

/**
 * Builds the launch intent, joining the caller's [LaunchProgressTracker] launch or opening one, and
 * opens the play session for an external emulator; LibretroActivity opens in-process sessions.
 */
class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker,
    private val libraryPointerRepair: LibraryPointerRepair,
    private val lockScreenArtManager: LockScreenArtManager,
    private val launchProgressTracker: LaunchProgressTracker,
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        prefetchedGame: GameEntity? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL
    ): LaunchResult {
        val joined = launchProgressTracker.current
        val ticket = joined ?: launchProgressTracker.begin(
            prefetchedGame?.title ?: gameRepository.getById(gameId)?.title
        ) ?: return LaunchResult.Cancelled
        var result: LaunchResult = LaunchResult.Cancelled
        try {
            result = launch(ticket, gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame, origin)
            return result
        } finally {
            if (joined == null) launchProgressTracker.finish(ticket, launched = result is LaunchResult.Success)
        }
    }

    private suspend fun launch(
        ticket: LaunchProgressTracker.Ticket,
        gameId: Long,
        discId: Long?,
        forResume: Boolean,
        selectedDiscPath: String?,
        variantFileId: Long?,
        skipVariantPrompt: Boolean,
        allowVariantPrompt: Boolean,
        prefetchedGame: GameEntity?,
        origin: LaunchOrigin
    ): LaunchResult {
        libraryPointerRepair.await()
        val result = gameLauncher.launch(gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame)
        if (ticket.isCancelled) return LaunchResult.Cancelled
        if (result is LaunchResult.Success && !forResume) {
            ticket.step(LaunchStep.PreparingUi)
            lockScreenArtManager.showBeforeLaunch(gameId)
            if (ticket.isCancelled) {
                lockScreenArtManager.showLibraryAfterCancelledLaunch()
                return LaunchResult.Cancelled
            }
        }
        if (result is LaunchResult.Success && !result.inProcess && !forResume) {
            val coreName = extractCoreName(result.intent)
            playSessionTracker.startSession(
                gameId = gameId,
                emulatorPackage = result.intent.component?.packageName
                    ?: result.intent.`package`
                    ?: "",
                coreName = coreName,
                isNewGame = true,
                variantFileId = result.variantFileId,
                origin = origin
            )
        }
        return result
    }

    private fun extractCoreName(intent: Intent): String? {
        val libretroPath = intent.getStringExtra("LIBRETRO") ?: return null
        val coreFile = libretroPath.substringAfterLast("/")
        return coreFile
            .removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
            .takeIf { it.isNotEmpty() }
    }
}
