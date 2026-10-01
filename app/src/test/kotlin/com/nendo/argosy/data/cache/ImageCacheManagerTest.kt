package com.nendo.argosy.data.cache

import android.content.Context
import coil.Coil
import coil.ImageLoader
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameImageCacheInfo
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageVolumeHealth
import com.nendo.argosy.data.storage.VolumeProbe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ImageCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var volumeHealth: StorageVolumeHealth
    private lateinit var fileAccessLayer: FileAccessLayer
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var defaultCacheDir: File
    private lateinit var legacyCacheDir: File

    @Before
    fun setup() {
        legacyCacheDir = tempFolder.newFolder("cache")
        defaultCacheDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true) {
            every { cacheDir } returns legacyCacheDir
            every { filesDir } returns defaultCacheDir
        }
        gameDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        achievementDao = mockk(relaxed = true)
        volumeHealth = mockk(relaxed = true)
        fileAccessLayer = mockk(relaxed = true)
        imageCacheManager = ImageCacheManager(
            context,
            gameDao,
            platformDao,
            achievementDao,
            volumeHealth,
            fileAccessLayer
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun stubDecodedImageCache() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(Coil)
        mockkStatic(Coil::class)
        every { Coil.imageLoader(any()) } returns mockk<ImageLoader>(relaxed = true)
    }

    private fun cacheFile(vararg segments: String): File {
        val file = segments.fold(File(imageCacheManager.getCurrentCachePath())) { dir, name -> File(dir, name) }
        file.parentFile?.mkdirs()
        file.writeText("image")
        return file
    }

    private fun game(
        id: Long = 7L,
        coverPath: String? = null,
        coverOverridePath: String? = null,
        backgroundOverridePath: String? = null,
        logoOverridePath: String? = null
    ) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "snes",
        title = "Game",
        sortTitle = "game",
        localPath = null,
        rommId = 42L,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        coverPath = coverPath,
        coverOverridePath = coverOverridePath,
        backgroundOverridePath = backgroundOverridePath,
        logoOverridePath = logoOverridePath
    )

    @Test
    fun `cached art matches its source url and stops matching once the server url changes`() {
        val oldUrl = "https://romm.example/assets/romm/resources/roms/1/42/cover/big.png?ts=2026-09-01"
        val newUrl = "https://romm.example/assets/romm/resources/roms/1/42/cover/big.png?ts=2026-09-30"
        val hash = java.security.MessageDigest.getInstance("MD5").digest(oldUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)
        val cached = "/data/user/0/app/files/covers/snes/cover_42_$hash.jpg"

        assertTrue(imageCacheManager.isCachedFromAny(cached, listOf(oldUrl)))
        assertFalse(imageCacheManager.isCachedFromAny(cached, listOf(newUrl)))
        assertTrue(imageCacheManager.isCachedFromAny(cached, listOf(newUrl, oldUrl)))
    }

    @Test
    fun `getCustomCachePath returns null by default`() {
        assertNull(imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `setCustomCachePath updates customCachePath`() {
        val customPath = tempFolder.newFolder("custom").absolutePath

        imageCacheManager.setCustomCachePath(customPath)

        assertEquals(customPath, imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `setCustomCachePath with null clears customCachePath`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        imageCacheManager.setCustomCachePath(customPath)

        imageCacheManager.setCustomCachePath(null)

        assertNull(imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `getDefaultCachePath returns path under context filesDir`() {
        val path = imageCacheManager.getDefaultCachePath()

        assertTrue(path.startsWith(defaultCacheDir.absolutePath))
        assertTrue(path.endsWith("images"))
    }

    @Test
    fun `getCurrentCachePath returns default path when customCachePath is null`() {
        val path = imageCacheManager.getCurrentCachePath()

        assertEquals(imageCacheManager.getDefaultCachePath(), path)
    }

    @Test
    fun `getCurrentCachePath returns custom path when customCachePath is set`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        imageCacheManager.setCustomCachePath(customPath)

        val path = imageCacheManager.getCurrentCachePath()

        assertTrue(path.startsWith(customPath))
        assertTrue(path.endsWith("argosy_images"))
    }

    @Test
    fun `setCustomCachePath creates argosy_images subfolder`() {
        val customPath = tempFolder.newFolder("custom").absolutePath

        imageCacheManager.setCustomCachePath(customPath)

        val expectedSubfolder = File(customPath, "argosy_images")
        assertTrue(expectedSubfolder.exists())
        assertTrue(expectedSubfolder.isDirectory)
    }

    @Test
    fun `getCurrentCachePath switches correctly between default and custom`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        val defaultPath = imageCacheManager.getDefaultCachePath()

        assertEquals(defaultPath, imageCacheManager.getCurrentCachePath())

        imageCacheManager.setCustomCachePath(customPath)
        assertTrue(imageCacheManager.getCurrentCachePath().startsWith(customPath))

        imageCacheManager.setCustomCachePath(null)
        assertEquals(defaultPath, imageCacheManager.getCurrentCachePath())
    }

    @Test
    fun `platform cache clear deletes server art and keeps every override file`() = runTest {
        stubDecodedImageCache()
        val serverCover = cacheFile("snes", "covers", "cover_42_abc.jpg")
        val coverOverride = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        val backgroundOverride = cacheFile("snes", "backgrounds", "bg_override_7_def.jpg")
        val logoOverride = cacheFile("snes", "logos", "logo_override_7_ghi.png")
        coEvery { gameDao.getArtOverridePathsForPlatform("snes") } returns listOf(
            coverOverride.absolutePath,
            backgroundOverride.absolutePath,
            logoOverride.absolutePath
        )

        imageCacheManager.clearPlatformCache("snes")

        assertFalse(serverCover.exists())
        assertTrue(coverOverride.exists())
        assertTrue(backgroundOverride.exists())
        assertTrue(logoOverride.exists())
        coVerify(exactly = 1) { gameDao.clearCachedArtForPlatform("snes") }
        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
        coVerify(exactly = 0) { gameDao.clearBackgroundOverride(any()) }
        coVerify(exactly = 0) { gameDao.clearLogoOverride(any()) }
    }

    @Test
    fun `clearing an override clears the column and deletes its file`() = runTest {
        val override = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        coEvery { gameDao.getById(7L) } returns game(coverOverridePath = override.absolutePath)

        imageCacheManager.clearArtOverride(7L, ArtSlot.COVER)

        assertFalse(override.exists())
        coVerify(exactly = 1) { gameDao.clearCoverOverride(7L) }
    }

    @Test
    fun `clearing a background override leaves the cover override alone`() = runTest {
        val cover = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        val background = cacheFile("snes", "backgrounds", "bg_override_7_def.jpg")
        coEvery { gameDao.getById(7L) } returns game(
            coverOverridePath = cover.absolutePath,
            backgroundOverridePath = background.absolutePath
        )

        imageCacheManager.clearArtOverride(7L, ArtSlot.BACKGROUND)

        assertTrue(cover.exists())
        assertFalse(background.exists())
        coVerify(exactly = 1) { gameDao.clearBackgroundOverride(7L) }
        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
    }

    @Test
    fun `clearing an override never deletes a file the override did not write`() = runTest {
        val foreign = cacheFile("snes", "covers", "cover_42_abc.jpg")
        coEvery { gameDao.getById(7L) } returns game(logoOverridePath = foreign.absolutePath)

        imageCacheManager.clearArtOverride(7L, ArtSlot.LOGO)

        assertTrue(foreign.exists())
        coVerify(exactly = 1) { gameDao.clearLogoOverride(7L) }
    }

    @Test
    fun `clearing a slot with no override writes nothing`() = runTest {
        coEvery { gameDao.getById(7L) } returns game()

        imageCacheManager.clearArtOverride(7L, ArtSlot.COVER)

        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
    }

    @Test
    fun `an unreadable picked file sets no override`() = runTest {
        every { fileAccessLayer.readBytes("/storage/emulated/0/Pictures/art.png") } returns null

        val applied = imageCacheManager.applyArtOverrideFromFile(
            7L,
            ArtSlot.BACKGROUND,
            "/storage/emulated/0/Pictures/art.png"
        )

        assertFalse(applied)
        coVerify(exactly = 0) { gameDao.setBackgroundOverride(any(), any()) }
    }

    @Test
    fun `missing-file sweep clears an override whose file is gone`() = runTest {
        stubDecodedImageCache()
        val probe = mockk<VolumeProbe>(relaxed = true)
        every { volumeHealth.newProbe() } returns probe
        every { probe.isGenuinelyAbsent("/gone/cover_override_7_abc.jpg") } returns true
        every { probe.isGenuinelyAbsent("/kept/bg_override_7_def.jpg") } returns false
        coEvery { gameDao.getAllImageCacheInfo() } returns listOf(
            GameImageCacheInfo(
                id = 7L,
                coverPath = null,
                backgroundPath = null,
                cachedScreenshotPaths = null,
                logoPath = null,
                coverOverridePath = "/gone/cover_override_7_abc.jpg",
                backgroundOverridePath = "/kept/bg_override_7_def.jpg",
                logoOverridePath = null
            )
        )
        coEvery { platformDao.getAllPlatforms() } returns emptyList()

        imageCacheManager.validateAndCleanCache(force = true)

        coVerify(exactly = 1) { gameDao.clearCoverOverride(7L) }
        coVerify(exactly = 0) { gameDao.clearBackgroundOverride(any()) }
    }
}
