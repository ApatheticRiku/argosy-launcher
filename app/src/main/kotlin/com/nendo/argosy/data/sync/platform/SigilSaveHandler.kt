package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.BuiltinSaveBase
import com.nendo.argosy.data.emulator.CartFeatureScanner
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.local.dao.SigilSyncStateDao
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.FalSigilFileAccess
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilException
import com.nendo.sigil.SigilResult
import com.nendo.sigil.SigilSaveUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SigilRoute(
    val layout: String,
    val root: String,
    val emulatorPackage: String?,
    val profile: String?,
    val options: Map<String, String> = emptyMap(),
    val libretro: Boolean = false
)

sealed class SigilCollect {
    data object NotRouted : SigilCollect()
    data object Absent : SigilCollect()
    data class Unreadable(val reason: String) : SigilCollect()
    data class Found(
        val data: ByteArray,
        val artifact: String,
        val contentHash: String,
        val identityHash: String,
        val shape: String = SHAPE_SINGLE
    ) : SigilCollect()

    companion object {
        const val SHAPE_SINGLE = "SINGLE"
        const val SHAPE_MULTI = "MULTI"
        const val SHAPE_FOLDER = "FOLDER"
        const val SHAPE_FOLDERS = "FOLDERS"
    }
}

sealed class SigilRestore {
    data object NotRouted : SigilRestore()
    data class Restored(val hardcoreMarker: Boolean) : SigilRestore()
    data class Refused(val reason: String) : SigilRestore()
}

/**
 * The save handler for every emulator whose saves Sigil collects and restores: memory cards,
 * GCI folders and profile save trees. Sigil decides which files are the game's and what travels
 * to RomM; Argosy supplies the save root, the file access and the decision to write.
 */
@Singleton
class SigilSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val switchProfileParser: SwitchProfileParser,
    private val titleDbRepository: TitleDbRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val fal: FileAccessLayer,
    private val connectionManager: dagger.Lazy<RomMConnectionManager>,
    private val saveUnitResolver: SaveUnitResolver,
    private val builtinSaveBase: BuiltinSaveBase,
    private val retroArchPathResolver: RetroArchPathResolver,
    private val sigilSyncStateDao: SigilSyncStateDao,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val cartFeatureScanner: CartFeatureScanner
) : PlatformSaveHandler {

    companion object {
        private const val TAG = "SigilSaveHandler"

        private val LAYOUTS: Map<String, Map<String, String>> = mapOf(
            "gc" to mapOf("dolphin" to "dolphin_standalone", "dolphin_mmjr" to "dolphin_standalone"),
            "psx" to mapOf("duckstation" to "duckstation"),
            "ps2" to listOf("nethersx2", "aethersx2", "pcsx2", "armsx2_refresh", "armsx2")
                .associateWith { "pcsx2_standalone" },
            "psp" to mapOf("ppsspp" to "ppsspp_standalone", "ppsspp_gold" to "ppsspp_standalone"),
            "vita" to mapOf("vita3k" to "vita3k", "vita3k-zx" to "vita3k"),
            "ps3" to mapOf("aps3e" to "aps3e", "armsx3" to "armsx3", "armsx3_play" to "armsx3"),
            "switch" to listOf(
                "yuzu", "eden", "citron", "sudachi", "lemon", "skyline", "strato", "ryujinx", "kenjinx"
            ).associateWith { it },
            "wiiu" to mapOf("cemu" to "cemu", "cemu_dualscreen" to "cemu"),
            "3ds" to mapOf("citra" to "citra", "azahar" to "azahar", "lime3ds" to "lime3ds")
        )

        private val ROOT_ANCHORS: Map<String, String> = mapOf(
            "dolphin_standalone" to "GC",
            "duckstation" to "memcards",
            "pcsx2_standalone" to "memcards",
            "ppsspp_standalone" to "PSP",
            "vita3k" to "ux0",
            "aps3e" to "dev_hdd0",
            "armsx3" to "dev_hdd0",
            "yuzu" to "nand",
            "eden" to "nand",
            "citron" to "nand",
            "sudachi" to "nand",
            "lemon" to "nand",
            "skyline" to "switch",
            "strato" to "switch",
            "ryujinx" to "bis",
            "kenjinx" to "bis",
            "cemu" to "mlc01",
            "citra" to "sdmc",
            "azahar" to "sdmc",
            "lime3ds" to "sdmc"
        )

        private val GB_CORES = setOf("gambatte", "mgba", "vbam", "sameboy", "tgbdual")

        private val LIBRETRO_LAYOUTS: Map<String, Set<String>> = mapOf(
            "saturn" to setOf("mednafen_saturn", "kronos", "yabause", "yabasanshiro"),
            "scd" to setOf("genesis_plus_gx"),
            "dreamcast" to setOf("flycast"),
            "gb" to GB_CORES,
            "gbc" to GB_CORES,
            "n64" to setOf("mupen64plus_next", "parallel_n64")
        )

        private val LIBRETRO_LAYOUT_ALIASES: Map<String, String> = mapOf(
            "mupen64plus_next_gles3" to "mupen64plus_next",
            "mupen64plus_next_gles2" to "mupen64plus_next"
        )

        private const val FLYCAST_LAYOUT = "flycast"
        private const val FLYCAST_PER_CONTENT_VMUS = "reicast_per_content_vmus"
        private val FLYCAST_PER_GAME_VALUES = setOf("VMU A1", "All VMUs")
        private const val FLYCAST_SHARED_DIR = "dc"

        private const val PCSX2_SLOT1_OPTION = "Slot1_Filename"

        private val CLOSED_EMULATOR_LAYOUTS = setOf("ryujinx", "kenjinx")

        private val PROFILE_FROM_EMULATOR_LAYOUTS = setOf("yuzu", "eden", "citron", "sudachi", "lemon")

        /**
         * The unowned shared-volume saves that were not unowned at the previous collect, given that
         * collect's newline-joined names. Null [previous] means no collect has seen the volume yet,
         * and then nothing is new.
         */
        fun newlyUnowned(previous: String?, unowned: List<String>): List<String> {
            if (previous == null) return emptyList()
            val known = previous.split('\n').filter { it.isNotEmpty() }.toSet()
            return unowned.filterNot { it in known }
        }

        /**
         * The spec's shape for a unit's bytes. [sigilShape] is what collect reported, or null for a
         * unit read back from the cache, which is then read from the bytes: not a zip is SINGLE, a
         * flat zip MULTI, and a zip of folders FOLDER or FOLDERS by how many roots it has.
         */
        fun unitShape(sigilShape: SigilSaveUnit.Shape?, data: ByteArray): String {
            if (sigilShape == SigilSaveUnit.Shape.Single || sigilShape == SigilSaveUnit.Shape.None) return SigilCollect.SHAPE_SINGLE
            if (sigilShape == SigilSaveUnit.Shape.Multi) return SigilCollect.SHAPE_MULTI
            val names = runCatching {
                java.util.zip.ZipInputStream(data.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.map { it.name }.toList()
                }
            }.getOrDefault(emptyList())
            if (names.isEmpty()) return SigilCollect.SHAPE_SINGLE
            if (names.none { '/' in it.trimEnd('/') } && sigilShape == null) return SigilCollect.SHAPE_MULTI
            val roots = names.map { it.substringBefore('/') }.toSet()
            return if (roots.size > 1) SigilCollect.SHAPE_FOLDERS else SigilCollect.SHAPE_FOLDER
        }

        fun layoutFor(emulatorId: String, platformSlug: String): String? {
            val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
            val platform = if (canonical == "n3ds") "3ds" else canonical
            return LAYOUTS[platform]?.get(emulatorId)
        }

        fun rootFor(basePath: String, layout: String): String? =
            ROOT_ANCHORS[layout]?.let { rootFor(basePath, listOf(it)) }

        /**
         * The save root a layout expects, found in [basePath] by the first folder of one of the
         * layout's subfolders: `memcards` gives the folder that holds `memcards/`.
         */
        fun rootFor(basePath: String, subdirs: List<String>): String? {
            val path = basePath.trimEnd('/')
            for (subdir in subdirs) {
                val index = anchorIndex(path, subdir.substringBefore('/')) ?: continue
                return path.substring(0, index)
            }
            return null
        }

        /**
         * Whether deleting [path] would take more than one game's saves out of a folder Sigil
         * owns: a save root, its anchor folder, or a card or volume directly inside it. Deeper
         * paths, such as one game's folder on a PCSX2 folder card, are not protected.
         */
        fun isProtectedSavePath(path: String): Boolean {
            val trimmed = path.trimEnd('/')
            return ROOT_ANCHORS.values.distinct().any { anchor ->
                val index = anchorIndex(trimmed, anchor) ?: return@any false
                val below = trimmed.substring(index + anchor.length + 1).trimStart('/')
                below.isEmpty() || !below.contains('/')
            }
        }

        private fun anchorIndex(path: String, anchor: String): Int? {
            val marker = "/$anchor"
            return path.indexOf("$marker/").takeIf { it >= 0 }
                ?: path.takeIf { it.endsWith(marker) }?.let { it.length - marker.length }
        }
    }

    private val fileAccess = FalSigilFileAccess(fal)

    /**
     * Whether saves travel as Sigil units. Servers before RomM 5.5, and a disconnected client,
     * keep the formats the legacy handlers write, so every device on an older server stays readable.
     */
    fun unitsEnabled(): Boolean = connectionManager.get().getCapabilities().supportsSnapshots

    fun routes(emulatorId: String, platformSlug: String): Boolean =
        unitsEnabled() && layoutFor(emulatorId, platformSlug) != null

    suspend fun route(gameId: Long, emulatorId: String? = null): SigilRoute? = withContext(Dispatchers.IO) {
        if (!unitsEnabled()) return@withContext null
        val game = gameDao.getById(gameId) ?: return@withContext null
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val effectiveId = emulatorId?.takeIf { it.isNotBlank() && it != "default" }
            ?: emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
            ?: emulatorResolver.getPreferredEmulatorId(game.platformSlug)
            ?: return@withContext null
        if (effectiveId in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) {
            return@withContext libretroRoute(game, effectiveId)
        }
        val config = SavePathRegistry.getConfigForPlatform(effectiveId, game.platformSlug)
            ?: emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: return@withContext null
        val layout = layoutFor(config.emulatorId, game.platformSlug) ?: return@withContext null
        val root = rootFor(game, config, layout, emulatorPackage) ?: return@withContext null
        SigilRoute(
            layout = layout,
            root = root,
            emulatorPackage = emulatorPackage,
            profile = profileFor(layout, emulatorPackage, root),
            options = optionsFor(layout, game.id, config.emulatorId)
        )
    }

    private suspend fun libretroRoute(game: GameEntity, emulatorId: String): SigilRoute? {
        val cores = LIBRETRO_LAYOUTS[PlatformDefinitions.getCanonicalSlug(game.platformSlug)] ?: return null
        val core = saveUnitResolver.layoutFor(game, emulatorId, null) ?: return null
        val layout = (LIBRETRO_LAYOUT_ALIASES[core] ?: core).takeIf { it in cores } ?: return null
        val retroArch = emulatorId in PlatformSaveHandlerRegistry.RETROARCH_EMULATOR_IDS
        val options = if (retroArch) {
            emptyMap()
        } else {
            saveUnitResolver.optionsFor(core, game.id.takeIf { game.perGameSettingsEnabled })
        }
        val root = if (retroArch) retroArchRoot(game, emulatorId, layout) else builtinRoot(game, layout, options)
        if (root == null) {
            Logger.warn(TAG, "[SaveSync] SIGIL | no root for layout=$layout emulator=$emulatorId")
            return null
        }
        return SigilRoute(
            layout = layout,
            root = root,
            emulatorPackage = null,
            profile = null,
            options = options,
            libretro = true
        )
    }

    private suspend fun builtinRoot(game: GameEntity, layout: String, options: Map<String, String>): String {
        val sharedVmus = layout == FLYCAST_LAYOUT &&
            options[FLYCAST_PER_CONTENT_VMUS]?.let { it !in FLYCAST_PER_GAME_VALUES } == true
        return if (sharedVmus) {
            File(AppPaths.libretroSystemDir(context.filesDir), FLYCAST_SHARED_DIR).absolutePath
        } else {
            builtinSaveBase.forGame(game)
        }
    }

    private suspend fun retroArchRoot(game: GameEntity, emulatorId: String, layout: String): String? {
        val dirs = retroArchPathResolver.resolveSaveDirectories(
            RetroArchPathResolver.Request(emulatorId = emulatorId, coreName = layout, romPath = game.localPath)
        )
        return dirs.firstOrNull { fal.isDirectory(it) } ?: dirs.firstOrNull()
    }

    private suspend fun optionsFor(layout: String, gameId: Long, configEmulatorId: String): Map<String, String> {
        if (layout != "pcsx2_standalone") return emptyMap()
        val card = emulatorConfigDao.getSelectedMemcardForGame(gameId)
            ?: emulatorSaveConfigRepository.getByEmulator(configEmulatorId)?.selectedMemcardPath
        val name = card?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return mapOf(PCSX2_SLOT1_OPTION to name)
    }

    private suspend fun rootFor(game: GameEntity, config: SavePathConfig, layout: String, pkg: String?): String? {
        val override = emulatorSaveConfigRepository.resolveEffectiveSavePath(config.emulatorId, game.platformSlug)
            ?.takeIf { it.isNotBlank() }
        val candidates = listOfNotNull(override) + SavePathRegistry.resolvePathWithPackage(
            config, pkg, context.filesDir.absolutePath, fal.externalStorageRoots()
        )
        val roots = candidates.mapNotNull { rootFor(it, layout) }.distinct()
        if (roots.isEmpty()) {
            Logger.warn(TAG, "[SaveSync] SIGIL | no root for layout=$layout in ${candidates.firstOrNull()}")
        }
        return roots.firstOrNull { fal.isDirectory(it) } ?: roots.firstOrNull()
    }

    private fun profileFor(layout: String, pkg: String?, root: String): String? =
        if (layout in PROFILE_FROM_EMULATOR_LAYOUTS && pkg != null) {
            runCatching { switchProfileParser.parseActiveProfile(pkg, root) }.getOrNull()
        } else null

    /**
     * The game's unit as it is on disk now. [claimNewSaves] is for a caller that runs while or
     * right after this game played: shared-volume saves no game owned that were not there at the
     * previous collect are given to this game. On the first collect of a volume nothing is claimed.
     */
    suspend fun collect(
        gameId: Long,
        emulatorId: String? = null,
        claimNewSaves: Boolean = false
    ): SigilCollect = withContext(Dispatchers.IO) {
        val route = route(gameId, emulatorId) ?: return@withContext SigilCollect.NotRouted
        val game = gameDao.getById(gameId) ?: return@withContext SigilCollect.NotRouted
        collect(route, game, claimNewSaves)
    }

    private suspend fun collect(route: SigilRoute, game: GameEntity, claimNewSaves: Boolean): SigilCollect {
        if (!fal.isDirectory(route.root)) return SigilCollect.Absent
        fal.prepareSaveAccess(route.root)
        val ids = titleIdCandidates(game)
        val stateKey = stateKey(route, game)
        val stored = loadState(stateKey)
        val sigilGame = identity(game, ids)
        return try {
            fun run(claimed: List<String>) = Sigil.collect(
                game = sigilGame,
                core = route.layout,
                contentPath = contentName(game),
                saveRoot = route.root,
                options = route.options,
                gameIds = ids,
                state = stored?.state,
                unmanaged = true,
                claimed = claimed,
                profile = route.profile,
                fileAccess = fileAccess
            )
            val first = run(emptyList())
            val fresh = if (claimNewSaves) newlyUnowned(stored?.unowned, first.unowned) else emptyList()
            val result = if (fresh.isEmpty()) first else run(fresh)
            if (fresh.isNotEmpty()) {
                Logger.info(TAG, "[SaveSync] SIGIL | claimed ${fresh.size} new shared-volume saves for game ${game.id} | $fresh")
            }
            if (result.restoreAgain) {
                Logger.warn(TAG, "[SaveSync] SIGIL | the emulator overwrote the last restore for game ${game.id}")
            }
            saveState(stateKey, result.state, result.unowned)
            val data = result.data
            if (data == null || data.isEmpty()) {
                SigilCollect.Absent
            } else {
                SigilCollect.Found(data, result.artifact, result.contentHash, result.identityHash, unitShape(result.shape, data))
            }
        } catch (e: SigilException) {
            when (e.code) {
                SigilException.NOT_FOUND -> SigilCollect.Absent
                else -> SigilCollect.Unreadable(describe(e))
            }
        }
    }

    suspend fun restore(
        gameId: Long,
        unitFile: File,
        emulatorId: String? = null
    ): SigilRestore = withContext(Dispatchers.IO) {
        val route = route(gameId, emulatorId) ?: return@withContext SigilRestore.NotRouted
        val game = gameDao.getById(gameId) ?: return@withContext SigilRestore.NotRouted
        if (route.layout in CLOSED_EMULATOR_LAYOUTS && !emulatorIsClosed(route.emulatorPackage)) {
            return@withContext SigilRestore.Refused("the emulator must be closed before its saves are restored")
        }
        fal.prepareSaveAccess(route.root)
        val unit = unitFile.readBytes()
        try {
            restore(route, game, unit)
        } catch (e: SigilException) {
            SigilRestore.Refused(describe(e))
        }
    }

    private suspend fun restore(route: SigilRoute, game: GameEntity, unit: ByteArray): SigilRestore {
        val ids = titleIdCandidates(game)
        val stateKey = stateKey(route, game)
        val result = Sigil.restore(
            unit = unit,
            game = identity(game, ids),
            core = route.layout,
            contentPath = contentName(game),
            saveRoot = route.root,
            options = route.options,
            gameIds = ids,
            state = loadState(stateKey)?.state,
            unmanaged = true,
            overwriteLocal = true,
            profile = route.profile,
            fileAccess = fileAccess
        )
        if (!fal.commitSaveAccess(route.root)) {
            return SigilRestore.Refused("the restored files did not reach ${route.root}")
        }
        saveState(stateKey, result.state, result.unowned)
        return SigilRestore.Restored(result.hardcoreMarker)
    }

    private data class StateKey(val ownerUserId: Long, val platformSlug: String, val layout: String, val root: String)

    private suspend fun stateKey(route: SigilRoute, game: GameEntity): StateKey =
        StateKey(
            ownerUserId = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER,
            platformSlug = Sigil.platformSlug(game.platformSlug),
            layout = route.layout,
            root = route.root
        )

    private suspend fun loadState(key: StateKey): SigilSyncStateEntity? =
        sigilSyncStateDao.get(key.ownerUserId, key.platformSlug, key.layout, key.root)

    private suspend fun saveState(key: StateKey, state: ByteArray, unowned: List<String>) {
        sigilSyncStateDao.upsert(
            SigilSyncStateEntity(
                ownerUserId = key.ownerUserId,
                platformSlug = key.platformSlug,
                layout = key.layout,
                root = key.root,
                state = state,
                unowned = unowned.joinToString("\n"),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun emulatorIsClosed(pkg: String?): Boolean {
        if (pkg == null || !RootShell.isAvailable) return false
        val pid = RootShell.execute("pidof ${RootShell.quote(pkg)}").getOrNull()?.trim()
        return pid.isNullOrEmpty()
    }

    private suspend fun titleIdCandidates(game: GameEntity): List<String> =
        if (game.titleId.isNullOrBlank() && PlatformDefinitions.getCanonicalSlug(game.platformSlug) == "switch") {
            titleDbRepository.getCachedCandidates(game.id)
        } else emptyList()

    private suspend fun identity(game: GameEntity, candidates: List<String>): SigilResult =
        SigilResult.persisted(
            game.platformSlug,
            game.titleId?.takeIf { it.isNotBlank() } ?: candidates.firstOrNull().orEmpty(),
            game.saveId ?: "",
            cartFeatureScanner.featuresFor(game)
        )

    private fun contentName(game: GameEntity): String =
        game.localPath?.let { File(it).name } ?: game.title

    private fun describe(e: SigilException): String =
        listOf(e.message, e.problem.takeIf { it.isNotEmpty() }).filterNotNull().joinToString(": ")

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? {
        val collected = collect(context.gameId, context.emulatorId) as? SigilCollect.Found ?: return null
        val dir = File(this.context.cacheDir, "sigil_upload_${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, collected.artifact)
        file.writeBytes(collected.data)
        return PreparedSave(file, isTemporary = true, originalPaths = listOf(localPath))
    }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        when (val restored = restore(context.gameId, tempFile, context.emulatorId)) {
            is SigilRestore.Restored -> ExtractResult(true, route(context.gameId, context.emulatorId)?.root)
            is SigilRestore.Refused -> ExtractResult(false, null, restored.reason)
            SigilRestore.NotRouted -> ExtractResult(false, null, "no Sigil layout for ${context.emulatorId}")
        }
}
