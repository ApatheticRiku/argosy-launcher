package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.GameCubeHeaderParser
import com.nendo.argosy.data.emulator.GameCubeGameInfo
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GciSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "GciSaveHandler"
        private val REGIONS = listOf("USA", "EUR", "JAP", "KOR")
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val gciPaths = unitMembers(localPath)
            if (gciPaths.isEmpty()) {
                Logger.debug(TAG, "prepareForUpload: No GCI files found | localPath=$localPath")
                return@withContext null
            }
            if (gciPaths.size == 1) {
                return@withContext PreparedSave(fal.getTransformedFile(gciPaths[0]), isTemporary = false, gciPaths)
            }

            val outputFile = File(this@GciSaveHandler.context.cacheDir, "gci_bundle_${System.currentTimeMillis()}.zip")
            if (!createBundle(gciPaths, outputFile)) {
                Logger.error(TAG, "prepareForUpload: Bundle creation failed")
                return@withContext null
            }

            PreparedSave(outputFile, isTemporary = true, gciPaths)
        }

    override suspend fun sourcePathsFor(localPath: String, context: SaveContext): List<String> =
        withContext(Dispatchers.IO) { unitMembers(localPath).ifEmpty { listOf(localPath) } }

    /**
     * The save unit [anyMemberPath] belongs to: every `.gci` in its card folder whose header
     * carries the same game code, one entry per GCI identity, ordered by file name.
     */
    fun unitMembers(anyMemberPath: String): List<String> {
        if (!fal.exists(anyMemberPath)) return emptyList()
        val gameId = GameCubeHeaderParser.parseGciHeader(fal.getTransformedFile(anyMemberPath))?.gameId
            ?: return listOf(anyMemberPath)
        val cardDir = File(anyMemberPath).parent ?: return listOf(anyMemberPath)
        val seen = mutableSetOf<Triple<String, String, String>>()
        return fal.listFiles(cardDir).orEmpty()
            .filter { it.isFile && it.extension.equals("gci", ignoreCase = true) && !it.name.contains(".deleted") }
            .sortedBy { it.name }
            .mapNotNull { file ->
                val header = GameCubeHeaderParser.parseGciHeader(fal.getTransformedFile(file.path))
                    ?.takeIf { it.gameId.equals(gameId, ignoreCase = true) }
                    ?: return@mapNotNull null
                file.path.takeIf { seen.add(Triple(header.makerCode, header.gameId, header.internalFilename)) }
            }
            .ifEmpty { listOf(anyMemberPath) }
    }

    /**
     * Writes a GameCube unit archive into [cardDir]: the game's existing members are replaced by
     * exactly the archive's `.gci` entries, under their own names. Returns the written paths, or
     * an empty list when the archive holds no readable GCI.
     */
    suspend fun placeUnitArchive(zipFile: File, cardDir: String): List<String> =
        withContext(Dispatchers.IO) {
            val staged = File(this@GciSaveHandler.context.cacheDir, "gci_unit_${System.currentTimeMillis()}")
            staged.mkdirs()
            try {
                val members = ZipFile(zipFile).use { zip ->
                    zip.entries.toList()
                        .filter { !it.isDirectory && it.name.endsWith(".gci", ignoreCase = true) }
                        .map { entry ->
                            File(staged, File(entry.name).name).also { out ->
                                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                            }
                        }
                }
                val gameCode = members.firstNotNullOfOrNull { GameCubeHeaderParser.parseGciHeader(it)?.gameId }
                    ?: return@withContext emptyList()
                fal.mkdirs(cardDir)
                fal.listFiles(cardDir).orEmpty()
                    .filter { it.isFile && it.extension.equals("gci", ignoreCase = true) }
                    .filter {
                        GameCubeHeaderParser.parseGciHeader(fal.getTransformedFile(it.path))?.gameId
                            .equals(gameCode, ignoreCase = true)
                    }
                    .forEach { fal.delete(it.path) }
                members.mapNotNull { member ->
                    val target = "$cardDir/${member.name}"
                    fal.prepareSaveAccess(target)
                    target.takeIf { fal.copyFile(member.absolutePath, target) }
                }
            } finally {
                staged.deleteRecursively()
            }
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            val romPath = context.romPath
            if (romPath == null) {
                return@withContext ExtractResult(false, null, "ROM path required for GCI extraction")
            }

            if (isZipBundle(tempFile)) {
                val paths = extractBundle(
                    tempFile, context.config, romPath, context.gameId, context.basePathOverride
                )
                if (paths.isEmpty()) {
                    return@withContext ExtractResult(false, null, "GCI bundle extraction failed")
                }
                ExtractResult(true, paths.first())
            } else {
                val path = extractSingleGci(
                    tempFile, romPath, context.config, context.basePathOverride
                )
                if (path == null) {
                    return@withContext ExtractResult(false, null, "Single GCI extraction failed")
                }
                ExtractResult(true, path)
            }
        }

    fun parseRomHeader(romPath: String): GameCubeGameInfo? {
        val romFile = fal.getTransformedFile(romPath)
        if (!romFile.exists()) {
            Logger.debug(TAG, "ROM file does not exist | path=$romPath")
            return null
        }
        return GameCubeHeaderParser.parseRomHeader(romFile)
    }

    fun discoverSavePath(
        config: SavePathConfig,
        romPath: String,
        basePathOverride: String? = null
    ): String? {
        val gameInfo = parseRomHeader(romPath)
        if (gameInfo == null) {
            Logger.debug(TAG, "Failed to parse ROM header | romPath=$romPath")
            return null
        }

        Logger.debug(TAG, "Parsed ROM | gameId=${gameInfo.gameId}, region=${gameInfo.region}, name=${gameInfo.gameName}")

        val resolvedPaths = if (basePathOverride != null) {
            listOf(basePathOverride)
        } else {
            SavePathRegistry.resolvePath(config, "ngc", context.filesDir.absolutePath)
        }
        Logger.debug(TAG, "Searching ${resolvedPaths.size} paths for GCI saves${if (basePathOverride != null) " (user override)" else ""}")

        for (basePath in resolvedPaths) {
            if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
                Logger.verbose(TAG) { "Save dir does not exist | path=$basePath" }
                continue
            }

            val gciFiles = findGciFilesInPath(basePath, gameInfo.gameId)
            if (gciFiles.isNotEmpty()) {
                val firstGci = gciFiles.first()
                Logger.debug(TAG, "Found ${gciFiles.size} GCI file(s), using: $firstGci")
                return firstGci
            }
        }

        Logger.debug(TAG, "No GCI saves found for gameId=${gameInfo.gameId}")
        return null
    }

    fun discoverAllSavePaths(
        config: SavePathConfig,
        romPath: String,
        basePathOverride: String? = null
    ): List<String> {
        val gameInfo = parseRomHeader(romPath)
        if (gameInfo == null) {
            Logger.debug(TAG, "Failed to parse ROM header | romPath=$romPath")
            return emptyList()
        }

        Logger.debug(TAG, "Parsed ROM | gameId=${gameInfo.gameId}, region=${gameInfo.region}")

        val resolvedPaths = if (basePathOverride != null) {
            listOf(basePathOverride)
        } else {
            SavePathRegistry.resolvePath(config, "ngc", context.filesDir.absolutePath)
        }

        val allGciFiles = mutableListOf<String>()
        for (basePath in resolvedPaths) {
            if (!fal.exists(basePath) || !fal.isDirectory(basePath)) continue
            allGciFiles.addAll(findGciFilesInPath(basePath, gameInfo.gameId))
        }

        Logger.debug(TAG, "Found ${allGciFiles.size} total GCI file(s) for gameId=${gameInfo.gameId}")
        return allGciFiles
    }

    suspend fun createBundle(
        gciPaths: List<String>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (gciPaths.isEmpty()) {
            Logger.warn(TAG, "No GCI files to bundle")
            return@withContext false
        }

        try {
            Logger.debug(TAG, "Creating bundle with ${gciPaths.size} file(s)")
            val files = gciPaths.map { fal.getTransformedFile(it) }
            saveArchiver.zipFiles(files, outputFile)
            Logger.debug(TAG, "Bundle created | path=${outputFile.absolutePath}, size=${outputFile.length()}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create bundle", e)
            false
        }
    }

    /**
     * [basePathOverride] is the folder the user pointed this emulator at, and it must be honoured
     * here as well as during discovery. Extracting to the packaged default while discovery reads
     * the override writes the save somewhere the next sync will not look, so the stale copy keeps
     * winning and the user is told the download succeeded.
     */
    suspend fun extractBundle(
        zipFile: File,
        config: SavePathConfig,
        romPath: String,
        gameId: Long,
        basePathOverride: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val extractedPaths = mutableListOf<String>()

        val romInfo = parseRomHeader(romPath)
        if (romInfo == null) {
            Logger.error(TAG, "Failed to parse ROM header for extraction | romPath=$romPath")
            return@withContext emptyList()
        }

        val basePaths = basePathOverride?.let { listOf(it) }
            ?: SavePathRegistry.resolvePath(config, "ngc", context.filesDir.absolutePath)
        val baseDir = basePaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: basePaths.firstOrNull()
            ?: run {
                Logger.error(TAG, "No valid base path for GCI extraction")
                return@withContext emptyList()
            }

        Logger.debug(TAG, "Extracting bundle | zipFile=${zipFile.name}, baseDir=$baseDir, region=${romInfo.region}, gameId=${romInfo.gameId}")

        try {
            val cardDir = File(GameCubeHeaderParser.buildGciPath(baseDir, romInfo.region, "x.gci")).parent
            val existingFiles = findGciFilesInPath(baseDir, romInfo.gameId)
                .filter { File(it).parent == cardDir }
            if (existingFiles.isNotEmpty()) {
                Logger.debug(TAG, "Deleting ${existingFiles.size} existing GCI file(s) for prefix ${romInfo.gameId}")
                for (path in existingFiles) {
                    if (fal.delete(path)) {
                        Logger.debug(TAG, "Deleted existing | path=$path")
                    }
                }
            }

            ZipFile(zipFile).use { zip ->
                val entries = zip.entries.toList().filter { !it.isDirectory && it.name.endsWith(".gci", ignoreCase = true) }
                Logger.debug(TAG, "Found ${entries.size} GCI entries in bundle")

                for (entry in entries) {
                    val entryName = File(entry.name).name
                    val tempGciFile = File(context.cacheDir, "temp_$entryName")
                    try {
                        zip.getInputStream(entry).use { input ->
                            tempGciFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val targetPath = GameCubeHeaderParser.buildGciPath(baseDir, romInfo.region, entryName)
                        val parentDir = File(targetPath).parent
                        if (parentDir != null) fal.mkdirs(parentDir)
                        fal.prepareSaveAccess(targetPath)

                        if (fal.copyFile(tempGciFile.absolutePath, targetPath)) {
                            extractedPaths.add(targetPath)
                            Logger.debug(TAG, "Extracted | entry=$entryName -> $targetPath")
                        } else {
                            Logger.error(TAG, "Failed to copy | entry=$entryName -> $targetPath")
                        }
                    } finally {
                        tempGciFile.delete()
                    }
                }
            }

            Logger.debug(TAG, "Extraction complete | extractedFiles=${extractedPaths.size}")
            extractedPaths
        } catch (e: Exception) {
            Logger.error(TAG, "Extraction failed", e)
            emptyList()
        }
    }

    suspend fun extractSingleGci(
        tempGciFile: File,
        romPath: String,
        config: SavePathConfig,
        basePathOverride: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val romInfo = parseRomHeader(romPath)
        if (romInfo == null) {
            Logger.error(TAG, "Failed to parse ROM header | romPath=$romPath")
            return@withContext null
        }

        val gciInfo = GameCubeHeaderParser.parseGciHeader(tempGciFile)
        if (gciInfo == null) {
            Logger.error(TAG, "Failed to parse GCI header")
            return@withContext null
        }

        if (!gciInfo.gameId.equals(romInfo.gameId, ignoreCase = true)) {
            Logger.error(
                TAG,
                "Refusing a gci that belongs to another game | archive=${gciInfo.gameId}, rom=${romInfo.gameId}"
            )
            return@withContext null
        }

        val gciFilename = GameCubeHeaderParser.buildGciFilename(
            gciInfo.makerCode,
            gciInfo.gameId,
            gciInfo.internalFilename
        )

        val basePaths = basePathOverride?.let { listOf(it) }
            ?: SavePathRegistry.resolvePath(config, "ngc", context.filesDir.absolutePath)
        val baseDir = basePaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: basePaths.firstOrNull()
            ?: return@withContext null

        val targetPath = GameCubeHeaderParser.buildGciPath(baseDir, romInfo.region, gciFilename)
        val parentDir = File(targetPath).parent
        if (parentDir != null) fal.mkdirs(parentDir)
        fal.prepareSaveAccess(targetPath)

        if (fal.copyFile(tempGciFile.absolutePath, targetPath)) {
            Logger.debug(TAG, "Extracted single GCI | $targetPath")
            targetPath
        } else {
            Logger.error(TAG, "Failed to copy single GCI | $targetPath")
            null
        }
    }

    fun findGciFilesInPath(basePath: String, gameId: String): List<String> {
        val results = mutableListOf<String>()

        for (region in REGIONS) {
            val regionPath = "$basePath/$region"
            if (!fal.exists(regionPath) || !fal.isDirectory(regionPath)) continue

            fal.listFiles(regionPath)
                ?.filter { it.isDirectory && it.name.startsWith("Card", ignoreCase = true) }
                ?.forEach { cardDir -> searchGciInDirectory(cardDir.path, gameId, results) }

            searchGciInDirectory(regionPath, gameId, results)
        }

        if (results.isEmpty()) {
            searchGciInDirectory(basePath, gameId, results)
            results.removeAll { !GameCubeHeaderParser.isValidGciPath(it) }
        }

        return results
    }

    private fun searchGciInDirectory(dirPath: String, gameId: String, results: MutableList<String>) {
        fal.listFiles(dirPath)?.forEach { file ->
            if (!file.isFile) return@forEach
            if (!file.extension.equals("gci", ignoreCase = true)) return@forEach
            if (file.name.contains(".deleted")) return@forEach

            val matches = file.name.contains(gameId, ignoreCase = true) ||
                GameCubeHeaderParser.parseGciHeader(fal.getTransformedFile(file.path))?.gameId.equals(gameId, ignoreCase = true)
            if (matches) results.add(file.path)
        }
    }

    fun isZipBundle(file: File): Boolean {
        if (file.length() < 4) return false
        return try {
            file.inputStream().use { stream ->
                val magic = ByteArray(2)
                stream.read(magic)
                magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun buildTargetPath(baseDir: String, region: String, gciFilename: String): String {
        return GameCubeHeaderParser.buildGciPath(baseDir, region, gciFilename)
    }
}
