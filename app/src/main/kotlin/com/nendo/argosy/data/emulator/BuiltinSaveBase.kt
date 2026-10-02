package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The folder the built-in emulator saves [GameEntity] into at launch: the game's own save path
 * when set, else beside the ROM when that is enabled, else the platform or global save folder.
 */
@Singleton
class BuiltinSaveBase @Inject constructor(
    private val libretroSavePathResolver: LibretroSavePathResolver,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val emulatorConfigDao: EmulatorConfigDao
) {
    suspend fun forGame(game: GameEntity, romPath: String? = game.localPath): String {
        emulatorConfigDao.getSavePathForGame(game.id)?.takeIf { it.isNotBlank() }?.let { return it }
        val besideRom = emulatorSaveConfigRepository.getByEmulator(EmulatorRegistry.BUILTIN_ID)?.savesBesideRom == true
        return libretroSavePathResolver.liveSaveBaseDir(
            platformId = game.platformId,
            besideRomDir = if (besideRom) romPath?.let { File(it).parent } else null,
        ).absolutePath
    }
}
