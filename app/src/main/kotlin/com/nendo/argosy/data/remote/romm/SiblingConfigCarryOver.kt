package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SiblingConfigCarryOver"

/**
 * Copies the per-game emulator config of a sibling group's single configured member to every
 * other regional copy in the same `siblingGroupKey` that has none. Only the launcher fields
 * travel; the save folder and memory card stay with the member that owns them. Runs once per
 * device, after [SiblingSplitRepair] has completed; an existing config is never replaced.
 */
@Singleton
class SiblingConfigCarryOver @Inject constructor(
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val syncPreferences: SyncPreferencesRepository
) {
    suspend fun runOnce(): Unit = withContext(Dispatchers.IO) {
        if (syncPreferences.isSiblingConfigCarryOverDone()) return@withContext
        if (!syncPreferences.isSiblingSplitRepairDone()) return@withContext
        try {
            val copied = carryOver()
            syncPreferences.setSiblingConfigCarryOverDone()
            Logger.info(TAG, "runOnce: complete, $copied configs copied")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "runOnce: failed, retrying after the next library pass: ${e.message}")
        }
    }

    internal suspend fun carryOver(): Int {
        val overrides = emulatorConfigDao.getGameOverrides()
            .mapNotNull { config -> config.gameId?.let { it to config } }
            .toMap()
        if (overrides.isEmpty()) return 0
        var copied = 0
        val groups = gameDao.getSiblingRows().filter { !it.isHackVariant }.groupBy { it.siblingGroupKey }
        for ((groupKey, members) in groups) {
            if (members.size < 2) continue
            val configured = members.filter { it.id in overrides }
            val source = configured.singleOrNull()?.let { overrides.getValue(it.id) }
            if (source == null) {
                if (configured.size > 1) {
                    Logger.info(TAG, "carryOver: $groupKey has ${configured.size} configured members; left as is")
                }
                continue
            }
            for (member in members) {
                if (member.id in overrides) continue
                if (emulatorConfigDao.getByGameId(member.id) != null) continue
                emulatorConfigDao.insert(
                    source.copy(id = 0, gameId = member.id, savePath = null, selectedMemcardPath = null)
                )
                copied++
                Logger.info(TAG, "carryOver: game ${source.gameId} config -> game ${member.id} ($groupKey)")
            }
        }
        return copied
    }
}
