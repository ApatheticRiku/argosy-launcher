package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.SaveSyncApiClient
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SnapshotEmulatorStamp(
    val emulator: String,
    val emulatorVersion: String?,
    val core: String?,
    val coreVersion: String?
) {
    fun writeTo(manifest: JSONObject): JSONObject = manifest.apply {
        put(EMULATOR_KEY, emulator.take(NAME_MAX_LENGTH))
        emulatorVersion?.let { put(EMULATOR_VERSION_KEY, it.take(VERSION_MAX_LENGTH)) }
        core?.let { put(CORE_KEY, it.take(NAME_MAX_LENGTH)) }
        coreVersion?.let { put(CORE_VERSION_KEY, it.take(VERSION_MAX_LENGTH)) }
    }

    private companion object {
        const val EMULATOR_KEY = "emulator"
        const val EMULATOR_VERSION_KEY = "emulator_version"
        const val CORE_KEY = "core"
        const val CORE_VERSION_KEY = "core_version"
        const val NAME_MAX_LENGTH = 50
        const val VERSION_MAX_LENGTH = 100
    }
}

/**
 * What a snapshot push reports about the emulator that wrote the save: the built-in emulator as
 * `libretro` with its core and the installed core build, RetroArch with its app version and core,
 * and a standalone emulator by its id and app version.
 */
@Singleton
class SnapshotEmulatorStamper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorResolver: EmulatorResolver,
    private val apiClient: Lazy<SaveSyncApiClient>,
    private val coreVersionDao: CoreVersionDao,
    private val coreVersionExtractor: CoreVersionExtractor
) {
    suspend fun stampFor(game: GameEntity, emulatorId: String): SnapshotEmulatorStamp {
        val core = apiClient.get().resolveCoreForGame(game, emulatorId)
        return when {
            emulatorId == EmulatorRegistry.BUILTIN_ID -> SnapshotEmulatorStamp(
                emulator = LIBRETRO,
                emulatorVersion = null,
                core = core?.let { EmulatorRegistry.toServerEmulator(emulatorId, it) },
                coreVersion = core?.let { coreVersionDao.getByCoreId(it)?.installedVersion }
            )
            RetroArchPathResolver.isRetroArch(emulatorId) -> {
                val packageName = packageFor(game)
                SnapshotEmulatorStamp(
                    emulator = RETROARCH,
                    emulatorVersion = packageName?.let(::appVersion),
                    core = core?.let { EmulatorRegistry.toServerEmulator(emulatorId, it) },
                    coreVersion = core?.let { coreVersionExtractor.getRetroArchCoreVersion(it, packageName) }
                )
            }
            else -> SnapshotEmulatorStamp(
                emulator = emulatorId,
                emulatorVersion = packageFor(game)?.let(::appVersion),
                core = null,
                coreVersion = null
            )
        }
    }

    private suspend fun packageFor(game: GameEntity): String? =
        emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)

    private fun appVersion(packageName: String): String? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

    private companion object {
        const val LIBRETRO = "libretro"
        const val RETROARCH = "retroarch"
    }
}
