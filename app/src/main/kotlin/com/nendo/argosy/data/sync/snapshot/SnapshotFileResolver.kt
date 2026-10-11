package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMRomFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The RomM file a game's channels key to: the file Argosy launches when RomM lists it, else the
 * one RomM keys by, a disc set's loader file before its tracks and then the first game file by name.
 */
@Singleton
class SnapshotFileResolver @Inject constructor() {
    suspend fun launchedFile(api: RomMApi, game: GameEntity): RomMRomFile? {
        val rommId = game.rommId ?: return null
        val files = runCatching { api.getRom(rommId) }.getOrNull()?.body()?.files.orEmpty()
        if (files.isEmpty()) return null
        val launched = game.localPath?.let { File(it).name }
        files.firstOrNull { it.fileName == launched }?.let { return it }
        val games = files
            .filter { it.category == null || it.category.equals(GAME_FILE_CATEGORY, ignoreCase = true) }
            .sortedBy { it.fileName.lowercase() }
        return games.firstOrNull { it.fileName.substringAfterLast('.').lowercase() in LOADER_EXTENSIONS }
            ?: games.firstOrNull()
    }

    private companion object {
        const val GAME_FILE_CATEGORY = "game"
        val LOADER_EXTENSIONS = setOf("cue", "gdi", "ccd", "mds", "toc")
    }
}
