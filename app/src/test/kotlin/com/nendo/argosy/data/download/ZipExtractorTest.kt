package com.nendo.argosy.data.download

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class ZipExtractorTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("zip_extractor_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isNswPlatform returns true for switch`() {
        assertTrue(ZipExtractor.isNswPlatform("switch"))
    }

    @Test
    fun `isNswPlatform returns true for nsw`() {
        assertTrue(ZipExtractor.isNswPlatform("nsw"))
    }

    @Test
    fun `isNswPlatform returns true case insensitive`() {
        assertTrue(ZipExtractor.isNswPlatform("Switch"))
        assertTrue(ZipExtractor.isNswPlatform("NSW"))
        assertTrue(ZipExtractor.isNswPlatform("SWITCH"))
    }

    @Test
    fun `isNswPlatform returns false for other platforms`() {
        assertFalse(ZipExtractor.isNswPlatform("psx"))
        assertFalse(ZipExtractor.isNswPlatform("vita"))
        assertFalse(ZipExtractor.isNswPlatform("wiiu"))
    }

    @Test
    fun `hasUpdateSupport returns true for switch`() {
        assertTrue(ZipExtractor.hasUpdateSupport("switch"))
        assertTrue(ZipExtractor.hasUpdateSupport("nsw"))
    }

    @Test
    fun `hasUpdateSupport returns true for vita`() {
        assertTrue(ZipExtractor.hasUpdateSupport("vita"))
        assertTrue(ZipExtractor.hasUpdateSupport("psvita"))
    }

    @Test
    fun `hasUpdateSupport returns true for wiiu`() {
        assertTrue(ZipExtractor.hasUpdateSupport("wiiu"))
    }

    @Test
    fun `hasUpdateSupport returns true for wii`() {
        assertTrue(ZipExtractor.hasUpdateSupport("wii"))
    }

    @Test
    fun `hasUpdateSupport returns false for disc-only platforms`() {
        assertFalse(ZipExtractor.hasUpdateSupport("psx"))
        assertFalse(ZipExtractor.hasUpdateSupport("saturn"))
        assertFalse(ZipExtractor.hasUpdateSupport("dreamcast"))
    }

    @Test
    fun `getPlatformConfig returns correct config for switch`() {
        val config = ZipExtractor.getPlatformConfig("switch")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("xci"))
        assertTrue(config.gameExtensions.contains("nsp"))
    }

    @Test
    fun `getPlatformConfig returns correct config for vita`() {
        val config = ZipExtractor.getPlatformConfig("vita")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("vpk"))
    }

    @Test
    fun `getPlatformConfig returns correct config for wiiu`() {
        val config = ZipExtractor.getPlatformConfig("wiiu")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("wua"))
        assertTrue(config.gameExtensions.contains("wud"))
    }

    @Test
    fun `getPlatformConfig returns correct config for wii`() {
        val config = ZipExtractor.getPlatformConfig("wii")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("wbfs"))
        assertTrue(config.gameExtensions.contains("iso"))
    }

    @Test
    fun `getPlatformConfig returns null for unknown platform`() {
        assertNull(ZipExtractor.getPlatformConfig("gba"))
        assertNull(ZipExtractor.getPlatformConfig("nes"))
        assertNull(ZipExtractor.getPlatformConfig("psx"))
    }

    @Test
    fun `getPlatformConfig is case insensitive`() {
        assertNotNull(ZipExtractor.getPlatformConfig("Switch"))
        assertNotNull(ZipExtractor.getPlatformConfig("VITA"))
        assertNotNull(ZipExtractor.getPlatformConfig("WiiU"))
    }

    @Test
    fun `isZipFile returns false for non-existent file`() {
        val fakeFile = File("/non/existent/path/file.zip")
        assertFalse(ZipExtractor.isZipFile(fakeFile))
    }

    @Test
    fun `nsw config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("switch")!!
        val expectedExtensions = setOf("xci", "nsp", "nca", "nro", "nsz", "xcz")
        assertEquals(expectedExtensions, config.gameExtensions)
    }

    @Test
    fun `vita config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("vita")!!
        assertTrue(config.gameExtensions.contains("vpk"))
        assertTrue(config.gameExtensions.contains("zip"))
    }

    @Test
    fun `wii config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("wii")!!
        val expectedExtensions = setOf("wbfs", "iso", "ciso", "wia", "rvz")
        assertEquals(expectedExtensions, config.gameExtensions)
    }

    @Test
    fun `wiiu config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("wiiu")!!
        assertTrue(config.gameExtensions.contains("wua"))
        assertTrue(config.gameExtensions.contains("wud"))
        assertTrue(config.gameExtensions.contains("wux"))
        assertTrue(config.gameExtensions.contains("wup"))
        assertTrue(config.gameExtensions.contains("rpx"))
    }

    @Test
    fun `update extensions match dlc extensions for all platforms`() {
        val platforms = listOf("switch", "vita", "wiiu", "wii")
        for (platform in platforms) {
            val config = ZipExtractor.getPlatformConfig(platform)!!
            assertEquals(
                "Platform $platform should have matching update and dlc extensions",
                config.updateExtensions,
                config.dlcExtensions
            )
        }
    }

    @Test
    fun `organizeNswSingleFile creates game folder and moves file`() {
        val platformDir = File(tempDir, "switch").apply { mkdirs() }
        val romFile = File(platformDir, "game.xci").apply { writeText("test content") }

        val result = ZipExtractor.organizeNswSingleFile(romFile, "Test Game", platformDir)

        assertEquals("Test Game", result.parentFile?.name)
        assertEquals("Test Game.xci", result.name)
        assertTrue(result.exists())
        assertFalse(romFile.exists())
    }

    @Test
    fun `organizeNswSingleFile preserves original filename when it starts with title`() {
        val platformDir = File(tempDir, "switch").apply { mkdirs() }
        val romFile = File(platformDir, "Test Game [v1.0].xci").apply { writeText("test") }

        val result = ZipExtractor.organizeNswSingleFile(romFile, "Test Game", platformDir)

        assertEquals("Test Game [v1.0].xci", result.name)
    }

    @Test
    fun `getUpdatesFolder returns folder when it exists`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val updateFolder = File(gameFolder, "update").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getUpdatesFolder(romFile.absolutePath, "switch")

        assertNotNull(result)
        assertEquals(updateFolder.absolutePath, result!!.absolutePath)
    }

    @Test
    fun `getUpdatesFolder returns null when folder does not exist`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getUpdatesFolder(romFile.absolutePath, "switch")

        assertNull(result)
    }

    @Test
    fun `getDlcFolder returns folder when it exists`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val dlcFolder = File(gameFolder, "dlc").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getDlcFolder(romFile.absolutePath, "switch")

        assertNotNull(result)
        assertEquals(dlcFolder.absolutePath, result!!.absolutePath)
    }

    @Test
    fun `getDlcFolder returns null for unsupported platform`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val romFile = File(gameFolder, "game.iso").apply { writeText("test") }

        val result = ZipExtractor.getDlcFolder(romFile.absolutePath, "snes")

        assertNull(result)
    }

    @Test
    fun `listUpdateFiles returns nsp files from update folder`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val updateFolder = File(gameFolder, "update").apply { mkdirs() }
        File(updateFolder, "update_v1.nsp").apply { writeText("update1") }
        File(updateFolder, "update_v2.nsp").apply { writeText("update2") }
        File(updateFolder, "readme.txt").apply { writeText("ignored") }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.listUpdateFiles(romFile.absolutePath, "switch")

        assertEquals(2, result.size)
        assertTrue(result.all { it.extension == "nsp" })
    }

    @Test
    fun `listDlcFiles returns dlc files sorted by name`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val dlcFolder = File(gameFolder, "dlc").apply { mkdirs() }
        File(dlcFolder, "dlc_b.nsp").apply { writeText("dlc2") }
        File(dlcFolder, "dlc_a.nsp").apply { writeText("dlc1") }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.listDlcFiles(romFile.absolutePath, "switch")

        assertEquals(2, result.size)
        assertEquals("dlc_a.nsp", result[0].name)
        assertEquals("dlc_b.nsp", result[1].name)
    }

    @Test
    fun `isZipFile returns true for valid zip`() {
        val zipFile = File(tempDir, "test.zip")
        createTestZip(zipFile, mapOf("test.txt" to "content"))

        assertTrue(ZipExtractor.isZipFile(zipFile))
    }

    @Test
    fun `isZipFile returns false for non-zip file`() {
        val textFile = File(tempDir, "test.txt").apply { writeText("not a zip") }

        assertFalse(ZipExtractor.isZipFile(textFile))
    }

    @Test
    fun `extractFolderRom extracts files and sets primary file`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("game.xci" to "game content"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Test Game", tempDir, "switch")

        assertNotNull(result.primaryFile)
        assertEquals("game.xci", result.primaryFile?.name)
        assertTrue(result.gameFolder.exists())
        assertEquals("Test Game", result.gameFolder.name)
    }

    @Test
    fun `extractFolderRom generates m3u for multiple disc files`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Game (Disc 1).bin" to "disc1",
            "Game (Disc 1).cue" to "cue1",
            "Game (Disc 2).bin" to "disc2",
            "Game (Disc 2).cue" to "cue2"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc Game", tempDir, "psx")

        assertNotNull(result.m3uFile)
        assertEquals("Multi Disc Game.m3u", result.m3uFile?.name)
        val m3uContent = result.m3uFile?.readText() ?: ""
        assertTrue(m3uContent.contains("Game (Disc 1)"))
        assertTrue(m3uContent.contains("Game (Disc 2)"))
    }

    @Test
    fun `extractFolderRom prunes archive m3u and regenerates its own`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Game (Disc 1).chd" to "disc1",
            "Game (Disc 2).chd" to "disc2",
            "Game.m3u" to "Game (Disc 1).chd\nGame (Disc 2).chd"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc Game", tempDir, "psx")

        assertNotNull(result.m3uFile)
        assertEquals("Multi Disc Game.m3u", result.m3uFile?.name)
        assertFalse(File(result.gameFolder, "Game.m3u").exists())
    }

    @Test
    fun `extractFolderRom does not generate m3u for single disc file`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("Game.chd" to "single disc"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Single Disc Game", tempDir, "psx")

        assertNull(result.m3uFile)
        assertEquals(1, result.discFiles.size)
    }

    @Test
    fun `extractFolderRom preserves subfolder structure`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "game.xci" to "main game",
            "update/update_v1.nsp" to "update",
            "dlc/dlc_pack.nsp" to "dlc"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "NSW Game", tempDir, "switch")

        val updateFolder = File(result.gameFolder, "update")
        val dlcFolder = File(result.gameFolder, "dlc")
        assertTrue(updateFolder.exists())
        assertTrue(dlcFolder.exists())
        assertTrue(File(updateFolder, "update_v1.nsp").exists())
        assertTrue(File(dlcFolder, "dlc_pack.nsp").exists())
    }

    @Test
    fun `extractFolderRom ignores disc files in subfolders for m3u`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "main.chd" to "main game",
            "extras/bonus.chd" to "bonus content"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Game With Extras", tempDir, "psx")

        assertNull(result.m3uFile)
        assertEquals(1, result.discFiles.size)
        assertEquals("main.chd", result.discFiles[0].name)
    }

    @Test
    fun `launchPath returns m3u path when present`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Disc 1.chd" to "disc1",
            "Disc 2.chd" to "disc2"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc", tempDir, "psx")

        assertTrue(result.launchPath.endsWith(".m3u"))
    }

    @Test
    fun `launchPath returns primary file when no m3u`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("game.nsp" to "switch game"))

        val result = ZipExtractor.extractFolderRom(zipFile, "NSW Game", tempDir, "switch")

        assertTrue(result.launchPath.endsWith(".nsp"))
    }

    @Test
    fun `launchPath points at single extracted nes file when extension not in isGameFile whitelist`() {
        val zipFile = File(tempDir, "tetris.zip")
        createTestZip(zipFile, mapOf("Tetris/tetris.nes" to "nes rom bytes"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Tetris", tempDir, "nes")

        assertEquals(File(File(tempDir, "Tetris"), "tetris.nes").absolutePath, result.primaryFile?.absolutePath)
        assertTrue(result.discFiles.isEmpty())
        assertTrue(
            "launchPath should end with .nes but was ${result.launchPath}",
            result.launchPath.endsWith(".nes")
        )
    }

    @Test
    fun `launchPath points at single extracted sfc file from 7z-style folder layout`() {
        val zipFile = File(tempDir, "mario.zip")
        createTestZip(zipFile, mapOf("Super Mario World/smw.sfc" to "snes rom bytes"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Super Mario World", tempDir, "snes")

        assertTrue(
            "launchPath should end with .sfc but was ${result.launchPath}",
            result.launchPath.endsWith(".sfc")
        )
    }

    @Test
    fun `launchPath points at single flat-extracted gba file`() {
        val zipFile = File(tempDir, "advance.zip")
        createTestZip(zipFile, mapOf("metroid.gba" to "gba rom bytes"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Metroid Fusion", tempDir, "gba")

        assertTrue(
            "launchPath should end with .gba but was ${result.launchPath}",
            result.launchPath.endsWith(".gba")
        )
    }

    @Test
    fun `shouldExtractArchive keeps arcade zip zipped because zip is the rom format`() {
        val zipFile = File(tempDir, "mame_rom.zip")
        createTestZip(zipFile, mapOf("game.bin" to "arcade rom content"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "mame"))
    }

    @Test
    fun `shouldExtractArchive keeps game and watch zip zipped because mame mess loads the set`() {
        val zipFile = File(tempDir, "gnw_ball.zip")
        createTestZip(
            zipFile,
            mapOf("ball.bin" to "rom data", "ball.svg" to "artwork", "ball.lay" to "layout")
        )

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "g-and-w"))
        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "gameandwatch"))
    }

    @Test
    fun `shouldExtractArchive extracts single-file switch zip because switch cannot read zipped roms`() {
        val zipFile = File(tempDir, "switch_game.zip")
        createTestZip(zipFile, mapOf("game.nsp" to "switch rom"))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "switch"))
    }

    @Test
    fun `shouldExtractArchive extracts single-file ps2 chd because chd should never be zipped`() {
        val zipFile = File(tempDir, "ps2_game.zip")
        createTestZip(zipFile, mapOf("game.chd" to "chd content"))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "ps2"))
    }

    @Test
    fun `shouldExtractArchive keeps single-file nes zip zipped because retroarch extracts at runtime`() {
        val zipFile = File(tempDir, "mario.zip")
        createTestZip(zipFile, mapOf("mario.nes" to "nes rom"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "nes"))
    }

    @Test
    fun `shouldExtractArchive keeps single-file snes zip zipped because retroarch extracts at runtime`() {
        val zipFile = File(tempDir, "smw.zip")
        createTestZip(zipFile, mapOf("smw.sfc" to "snes rom"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "snes"))
    }

    @Test
    fun `shouldExtractArchive keeps single-file genesis zip zipped because retroarch extracts at runtime`() {
        val zipFile = File(tempDir, "sonic.zip")
        createTestZip(zipFile, mapOf("sonic.md" to "genesis rom"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "genesis"))
    }

    @Test
    fun `shouldExtractArchive extracts multi-file zip even on retro platform`() {
        val zipFile = File(tempDir, "multi_disc.zip")
        createTestZip(zipFile, mapOf(
            "game.cue" to "cue file",
            "game.bin" to "bin file"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "segacd"))
    }

    @Test
    fun `shouldExtractArchive extracts file in subfolder on switch`() {
        val zipFile = File(tempDir, "nsw_game.zip")
        createTestZip(zipFile, mapOf("update/patch.nsp" to "update content"))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "switch"))
    }

    @Test
    fun `shouldExtractArchive extracts mixed root and subfolder files on switch`() {
        val zipFile = File(tempDir, "nsw_complete.zip")
        createTestZip(zipFile, mapOf(
            "game.xci" to "main game",
            "update/update_v1.nsp" to "update",
            "dlc/bonus.nsp" to "dlc"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "switch"))
    }

    @Test
    fun `shouldExtractArchive returns false for non-zip file`() {
        val textFile = File(tempDir, "not_a_zip.txt").apply { writeText("plain text") }

        assertFalse(ZipExtractor.shouldExtractArchive(textFile))
    }

    @Test
    fun `shouldExtractArchive returns false for non-existent file`() {
        val fakeFile = File(tempDir, "does_not_exist.zip")

        assertFalse(ZipExtractor.shouldExtractArchive(fakeFile))
    }

    @Test
    fun `shouldExtractArchive returns false for empty zip`() {
        val zipFile = File(tempDir, "empty.zip")
        createTestZip(zipFile, emptyMap())

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive handles MAME style single rom correctly`() {
        val zipFile = File(tempDir, "pacman.zip")
        createTestZip(zipFile, mapOf("pacman.rom" to "arcade rom data"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile, "mame"))
    }

    @Test
    fun `shouldExtractArchive handles multi-disc PSX game correctly`() {
        val zipFile = File(tempDir, "ff7.zip")
        createTestZip(zipFile, mapOf(
            "Final Fantasy VII (Disc 1).chd" to "disc1",
            "Final Fantasy VII (Disc 2).chd" to "disc2",
            "Final Fantasy VII (Disc 3).chd" to "disc3"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile, "psx"))
    }

    @Test
    fun `a complete archive smaller than its source files still validates`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(
            zipFile,
            mapOf(
                "Game.xci" to "x".repeat(4096),
                "Game.nfo" to "n".repeat(4096),
                "Game.sfv" to "s".repeat(4096)
            )
        )
        val rawTotal = 3L * 4096

        assertTrue("fixture must actually compress", zipFile.length() < rawTotal)
        assertTrue(
            ZipExtractor.validateArchive(zipFile, 0) is ZipExtractor.ArchiveValidationResult.Valid
        )
        assertTrue(
            "the raw sum is what used to reject it",
            ZipExtractor.validateArchive(zipFile, rawTotal)
                is ZipExtractor.ArchiveValidationResult.Invalid
        )
    }

    @Test
    fun `a truncated archive is still rejected without a size to compare against`() {
        val zipFile = File(tempDir, "truncated.zip")
        createTestZip(zipFile, mapOf("Game.xci" to "x".repeat(4096)))
        val bytes = zipFile.readBytes()
        zipFile.writeBytes(bytes.copyOf(bytes.size / 2))

        assertTrue(
            ZipExtractor.validateArchive(zipFile, 0)
                is ZipExtractor.ArchiveValidationResult.Invalid
        )
    }

    @Test
    fun `scene and art files beside a rom are companions`() {
        listOf("Game.nfo", "Game.sfv", "Game.md5", "Game.diz", "Game.url", "Game.jpg")
            .forEach { assertTrue(it, ZipExtractor.isCompanionFileName(it)) }
    }

    @Test
    fun `a pico8 cart is not a companion`() {
        assertFalse(ZipExtractor.isCompanionFileName("Celeste.p8.png"))
        assertFalse(ZipExtractor.isCompanionFileName("Celeste.png"))
    }

    @Test
    fun `rom formats are never companions`() {
        listOf("Game.xci", "Game.nsp", "Game.bin", "Game.cue", "Game.chd", "Game.iso")
            .forEach { assertFalse(it, ZipExtractor.isCompanionFileName(it)) }
    }

    @Test
    fun `a single folder wrapping every file is the wrapper`() {
        assertEquals(
            "Day of the Tentacle",
            ZipExtractor.wrapperFolderOf(
                listOf("Day of the Tentacle/TENTACLE.000", "Day of the Tentacle/sub/MONSTER.SOU")
            )
        )
    }

    @Test
    fun `mac metadata beside the wrapper does not break it`() {
        assertEquals(
            "Game",
            ZipExtractor.wrapperFolderOf(listOf("Game/a.bin", "__MACOSX/Game/._a.bin"))
        )
    }

    @Test
    fun `hidden files at the root do not break the wrapper`() {
        assertEquals(
            "Game",
            ZipExtractor.wrapperFolderOf(listOf("Game/a.bin", "._Game", ".DS_Store"))
        )
    }

    @Test
    fun `a hidden folder at the root does not break the wrapper`() {
        assertEquals("Game", ZipExtractor.wrapperFolderOf(listOf("Game/a.bin", ".fseventsd/x")))
    }

    @Test
    fun `hidden files inside the wrapper stay part of it`() {
        assertEquals("Game", ZipExtractor.wrapperFolderOf(listOf("Game/a.bin", "Game/.config/x")))
    }

    @Test
    fun `a file at the root means there is no wrapper`() {
        assertNull(ZipExtractor.wrapperFolderOf(listOf("Game/a.bin", "readme.txt")))
    }

    @Test
    fun `two top level folders mean there is no wrapper`() {
        assertNull(ZipExtractor.wrapperFolderOf(listOf("code/a.rpx", "content/b.bin")))
    }

    @Test
    fun `a layout folder is never treated as a wrapper`() {
        listOf("PS3_GAME", "psp_game", "USRDIR", "dlc", "updates", "extcontent").forEach { top ->
            assertNull(top, ZipExtractor.wrapperFolderOf(listOf("$top/a.bin", "$top/b/c.bin")))
        }
    }

    @Test
    fun `a wrapped archive extracts its files into the game folder itself`() {
        val platformDir = File(tempDir, "scummvm").apply { mkdirs() }
        val zipFile = File(tempDir, "dott.zip")
        createTestZip(
            zipFile,
            mapOf(
                "Day of the Tentacle/TENTACLE.000" to "data",
                "Day of the Tentacle/MONSTER.SOU" to "sound"
            )
        )

        val result = ZipExtractor.extractFolderRom(zipFile, "Day of the Tentacle", platformDir, "scummvm")

        val gameFolder = File(platformDir, "Day of the Tentacle")
        assertEquals(gameFolder.absolutePath, result.gameFolder.absolutePath)
        assertTrue(File(gameFolder, "TENTACLE.000").isFile)
        assertTrue(File(gameFolder, "MONSTER.SOU").isFile)
        assertFalse(File(gameFolder, "Day of the Tentacle").exists())
    }

    @Test
    fun `root junk does not stop a wrapped archive from extracting flat`() {
        val platformDir = File(tempDir, "scummvm").apply { mkdirs() }
        val zipFile = File(tempDir, "sam.zip")
        createTestZip(
            zipFile,
            mapOf(
                "Sam and Max/SAMNMAX.000" to "data",
                "._Sam and Max" to "fork",
                ".DS_Store" to "finder"
            )
        )

        ZipExtractor.extractFolderRom(zipFile, "Sam and Max", platformDir, "scummvm")

        val gameFolder = File(platformDir, "Sam and Max")
        assertTrue(File(gameFolder, "SAMNMAX.000").isFile)
        assertFalse(File(gameFolder, "Sam and Max").exists())
    }

    @Test
    fun `wrapped disc images are found at the game folder root`() {
        val platformDir = File(tempDir, "psx").apply { mkdirs() }
        val zipFile = File(tempDir, "ff7.zip")
        createTestZip(
            zipFile,
            mapOf(
                "Final Fantasy VII/Disc 1.cue" to "cue",
                "Final Fantasy VII/Disc 1.bin" to "bin"
            )
        )

        val result = ZipExtractor.extractFolderRom(zipFile, "Final Fantasy VII", platformDir, "psx")

        assertEquals(listOf("Disc 1.bin", "Disc 1.cue"), result.discFiles.map { it.name })
    }

    @Test
    fun `an unwrapped archive keeps its layout`() {
        val platformDir = File(tempDir, "scummvm").apply { mkdirs() }
        val zipFile = File(tempDir, "loom.zip")
        createTestZip(zipFile, mapOf("LOOM.000" to "data", "extra/notes.txt" to "notes"))

        ZipExtractor.extractFolderRom(zipFile, "Loom", platformDir, "scummvm")

        val gameFolder = File(platformDir, "Loom")
        assertTrue(File(gameFolder, "LOOM.000").isFile)
        assertTrue(File(gameFolder, "extra/notes.txt").isFile)
    }

    private fun createTestZip(zipFile: File, entries: Map<String, String>) {
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
    }
}
