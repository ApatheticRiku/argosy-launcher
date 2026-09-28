package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VariantFileCleanup"

/**
 * One-time device cleanup for the variant launch rules: rows whose category never launches lose
 * their launch flag, game file selections that cannot run as a variant are cleared, and legacy
 * `versionGroup` tags are removed. Files and saves on disk are never touched.
 *
 * Deferred while version-grouped rows remain and the sibling split repair has not finished; the
 * repair's variant save carry-over reads both the tags and the selections.
 */
@Singleton
class VariantFileCleanup @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val appPreferences: AppPreferencesRepository,
    private val syncPreferences: SyncPreferencesRepository
) {
    suspend fun runOnce(): Unit = withContext(Dispatchers.IO) {
        if (appPreferences.isVariantFileCleanupDone()) return@withContext
        if (gameFileDao.hasVersionGroupedFiles() && !syncPreferences.isSiblingSplitRepairDone()) {
            Logger.info(TAG, "runOnce: deferred until the sibling split repair completes")
            return@withContext
        }
        try {
            val demoted = gameFileDao.clearLaunchTargetForCategories(NON_LAUNCH_CATEGORY_KEYS)
            val selectionsCleared = clearNonLaunchSelections()
            val ungrouped = gameFileDao.clearVersionGroups()
            appPreferences.setVariantFileCleanupDone()
            Logger.info(
                TAG,
                "runOnce: demoted $demoted rows, cleared $selectionsCleared selections, " +
                    "untagged $ungrouped version-grouped rows"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "runOnce: failed, retrying on the next run: ${e.message}")
        }
    }

    private suspend fun clearNonLaunchSelections(): Int {
        var cleared = 0
        for (game in gameDao.getGamesWithFileSelection()) {
            game.activeVariantFileId?.takeUnless { launchesAsVariant(it, game) }?.let { fileId ->
                gameDao.updateActiveVariantFileId(game.id, null)
                Logger.info(TAG, "selections: cleared activeVariantFileId $fileId on gameId=${game.id}")
                cleared++
            }
            game.lastPlayedFileId?.takeUnless { launchesAsVariant(it, game) }?.let { fileId ->
                gameDao.updateLastPlayedFileId(game.id, null)
                Logger.info(TAG, "selections: cleared lastPlayedFileId $fileId on gameId=${game.id}")
                cleared++
            }
        }
        return cleared
    }

    private suspend fun launchesAsVariant(fileId: Long, game: GameEntity): Boolean =
        gameFileDao.getById(fileId)?.isLaunchableVariantOf(game) == true

    private companion object {
        val NON_LAUNCH_CATEGORY_KEYS = VariantCategory.entries.filterNot { it.isLaunchTarget }.map { it.key }
    }
}
