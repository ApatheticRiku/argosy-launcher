package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.FalSigilFileAccess
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilException
import com.nendo.sigil.SigilResult
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
    val profile: String?
)

sealed class SigilCollect {
    data object NotRouted : SigilCollect()
    data object Absent : SigilCollect()
    data class Unreadable(val reason: String) : SigilCollect()
    data class Found(
        val data: ByteArray,
        val artifact: String,
        val contentHash: String,
        val identityHash: String
    ) : SigilCollect()
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
    private val fal: FileAccessLayer
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

        private val CLOSED_EMULATOR_LAYOUTS = setOf("ryujinx", "kenjinx")

        private val PROFILE_FROM_EMULATOR_LAYOUTS = setOf("yuzu", "eden", "citron", "sudachi", "lemon")

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

    suspend fun route(gameId: Long, emulatorId: String? = null): SigilRoute? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val effectiveId = emulatorId?.takeIf { it.isNotBlank() && it != "default" }
            ?: emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
            ?: emulatorResolver.getPreferredEmulatorId(game.platformSlug)
            ?: return@withContext null
        val config = SavePathRegistry.getConfigForPlatform(effectiveId, game.platformSlug)
            ?: emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: return@withContext null
        val layout = layoutFor(config.emulatorId, game.platformSlug) ?: return@withContext null
        val root = rootFor(game, config, layout, emulatorPackage) ?: return@withContext null
        SigilRoute(layout, root, emulatorPackage, profileFor(layout, emulatorPackage, root))
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

    suspend fun collect(gameId: Long, emulatorId: String? = null): SigilCollect = withContext(Dispatchers.IO) {
        val route = route(gameId, emulatorId) ?: return@withContext SigilCollect.NotRouted
        val game = gameDao.getById(gameId) ?: return@withContext SigilCollect.NotRouted
        collect(route, game)
    }

    private suspend fun collect(route: SigilRoute, game: GameEntity): SigilCollect {
        if (!fal.isDirectory(route.root)) return SigilCollect.Absent
        fal.prepareSaveAccess(route.root)
        val ids = titleIdCandidates(game)
        return try {
            val result = Sigil.collect(
                game = identity(game, ids),
                core = route.layout,
                contentPath = contentName(game),
                saveRoot = route.root,
                gameIds = ids,
                unmanaged = true,
                profile = route.profile,
                fileAccess = fileAccess
            )
            val data = result.data
            if (data == null || data.isEmpty()) {
                SigilCollect.Absent
            } else {
                SigilCollect.Found(data, result.artifact, result.contentHash, result.identityHash)
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
        val ids = titleIdCandidates(game)
        try {
            val result = Sigil.restore(
                unit = unitFile.readBytes(),
                game = identity(game, ids),
                core = route.layout,
                contentPath = contentName(game),
                saveRoot = route.root,
                gameIds = ids,
                unmanaged = true,
                overwriteLocal = true,
                profile = route.profile,
                fileAccess = fileAccess
            )
            if (!fal.commitSaveAccess(route.root)) {
                return@withContext SigilRestore.Refused("the restored files did not reach ${route.root}")
            }
            SigilRestore.Restored(result.hardcoreMarker)
        } catch (e: SigilException) {
            SigilRestore.Refused(describe(e))
        }
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

    private fun identity(game: GameEntity, candidates: List<String>): SigilResult =
        SigilResult.persisted(
            game.platformSlug,
            game.titleId?.takeIf { it.isNotBlank() } ?: candidates.firstOrNull().orEmpty(),
            game.saveId ?: "",
            game.saveFeatures ?: 0
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
