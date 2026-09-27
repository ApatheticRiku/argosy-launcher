package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "PushInstallUseCase"
private const val HTTP_NOT_FOUND = 404

private val NON_INSTALL_CATEGORIES = setOf(
    VariantCategory.SOUNDTRACK,
    VariantCategory.SCREENSHOT,
    VariantCategory.CHEAT,
    VariantCategory.MANUAL,
    VariantCategory.WALKTHROUGH
)

sealed interface PushInstallOutcome {
    data object Queued : PushInstallOutcome
    data object AlreadyInstalled : PushInstallOutcome
    data class Failed(val failure: PushInstallFailure, val detail: String? = null) : PushInstallOutcome
}

enum class PushInstallFailure {
    ROM_UNRESOLVED,
    NO_EMULATOR,
    INSUFFICIENT_STORAGE,
    NO_INSTALLABLE_FILES,
    DOWNLOAD_ERROR
}

/**
 * Lands one install request from RomM in the download queue. The rom is always refreshed with a
 * single-rom fetch; a stored copy is used only when the server cannot be reached and it already
 * lists every requested file. Once the rom resolves, its platform is made visible.
 */
class PushInstallUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val gameRepository: GameRepository,
    private val emulatorDetector: EmulatorDetector,
    private val downloadGameUseCase: DownloadGameUseCase
) {
    suspend operator fun invoke(romId: Long, requestedFileIds: List<Long>): PushInstallOutcome {
        val game = resolveGame(romId, requestedFileIds)
            ?: return PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED)
        platformDao.updateVisibility(game.platformId, true)

        if (gameRepository.validateAndDiscoverGame(game.id)) return PushInstallOutcome.AlreadyInstalled
        if (!hasEmulatorFor(game)) return PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR)

        val files = gameFileDao.getFilesForGame(game.id)
        val selection = selectInstallFiles(files, requestedFileIds)
        if (requestedFileIds.isNotEmpty() && selection == null) {
            return PushInstallOutcome.Failed(PushInstallFailure.NO_INSTALLABLE_FILES)
        }
        if (!hasRoomFor(files, selection)) {
            return PushInstallOutcome.Failed(PushInstallFailure.INSUFFICIENT_STORAGE)
        }

        return when (val result = downloadGameUseCase(game.id, selection)) {
            DownloadResult.Queued, is DownloadResult.MultiDiscQueued -> PushInstallOutcome.Queued
            DownloadResult.AlreadyDownloaded -> PushInstallOutcome.AlreadyInstalled
            is DownloadResult.Error -> if (result.reason == DownloadGameFailureReason.AllDiscsAlreadyDownloaded) {
                PushInstallOutcome.AlreadyInstalled
            } else {
                PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, result.reason.detailToken())
            }
            is DownloadResult.ExtractionFailed -> PushInstallOutcome.Failed(
                PushInstallFailure.DOWNLOAD_ERROR,
                "previous extraction failed"
            )
        }
    }

    private fun DownloadGameFailureReason.detailToken(): String = when (this) {
        DownloadGameFailureReason.GameNotFound -> "game not found"
        DownloadGameFailureReason.GameNotSynced -> "game not synced"
        is DownloadGameFailureReason.InvalidFileType -> "invalid file type .$extension"
        is DownloadGameFailureReason.RomInfoFetchFailed -> "rom info fetch failed: $serverMessage"
        DownloadGameFailureReason.NoDiscsFound -> "no discs found"
        DownloadGameFailureReason.AllDiscsAlreadyDownloaded -> "all discs already downloaded"
        DownloadGameFailureReason.NoDiscsQueued -> "no discs queued"
        DownloadGameFailureReason.GameNotFoundForRepair -> "game not found"
        DownloadGameFailureReason.NotMultiDisc -> "not multi-disc"
        is DownloadGameFailureReason.Extraction -> "extraction failed"
    }

    private suspend fun resolveGame(romId: Long, requestedFileIds: List<Long>): GameEntity? {
        val synced = try {
            romMRepository.syncSingleRom(romId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "single-rom sync failed")
        }
        when (synced) {
            is RomMResult.Success -> return synced.data
            is RomMResult.Error -> {
                Logger.warn(TAG, "resolveGame: rom $romId not refreshed: ${synced.message} (${synced.code})")
                if (synced.code == HTTP_NOT_FOUND) return null
            }
        }
        val local = gameDao.getByRommId(romId) ?: return null
        val localFileIds = gameFileDao.getFilesForGame(local.id).mapNotNull { it.rommFileId }.toSet()
        return local.takeIf { localFileIds.containsAll(requestedFileIds) }
    }

    private suspend fun hasEmulatorFor(game: GameEntity): Boolean {
        if (game.platformId == LocalPlatformIds.ANDROID) return true
        if (emulatorDetector.installedEmulators.value.isEmpty()) emulatorDetector.detectEmulators()
        return emulatorDetector.hasAnyEmulator(game.platformSlug)
    }

    private fun selectInstallFiles(files: List<GameFileEntity>, requested: List<Long>): List<Long>? {
        if (requested.isEmpty()) return null
        if (files.isEmpty()) return requested
        val byRommId = files.associateBy { it.rommFileId }
        return requested
            .filter { id -> byRommId[id]?.let { VariantCategory.fromKey(it.category) !in NON_INSTALL_CATEGORIES } == true }
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun hasRoomFor(files: List<GameFileEntity>, selection: List<Long>?): Boolean {
        val needed = files
            .filter { file ->
                if (selection != null) {
                    file.rommFileId in selection
                } else {
                    VariantCategory.fromKey(file.category) !in NON_INSTALL_CATEGORIES
                }
            }
            .sumOf { it.fileSize }
        if (needed <= 0L) return true
        val available = gameRepository.getAvailableStorageBytes()
        return available <= 0L || available >= needed
    }
}
