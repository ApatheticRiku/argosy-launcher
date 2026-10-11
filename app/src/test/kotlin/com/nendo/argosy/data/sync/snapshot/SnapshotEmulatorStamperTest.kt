package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import android.content.pm.PackageInfo
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.SaveSyncApiClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotEmulatorStamperTest {

    private val context = mockk<Context>(relaxed = true)
    private val resolver = mockk<EmulatorResolver>()
    private val client = mockk<SaveSyncApiClient>()
    private val coreVersions = mockk<CoreVersionDao>()
    private val extractor = mockk<CoreVersionExtractor>()
    private val stamper = SnapshotEmulatorStamper(context, resolver, { client }, coreVersions, extractor)

    private val game = GameEntity(
        id = 3, platformId = 1, title = "Emerald", sortTitle = "emerald", localPath = "/roms/emerald.gba",
        rommId = 7, igdbId = null, source = GameSource.ROMM_SYNCED, platformSlug = "gba"
    )

    private fun installed(packageName: String, version: String) {
        coEvery { resolver.getEmulatorPackageForGame(3, 1, "gba") } returns packageName
        every { context.packageManager.getPackageInfo(packageName, 0) } returns
            PackageInfo().apply { versionName = version }
    }

    @Test
    fun `the built-in emulator reports libretro with its core and installed core build`() = runBlocking {
        coEvery { client.resolveCoreForGame(game, EmulatorRegistry.BUILTIN_ID) } returns "mgba"
        coEvery { coreVersions.getByCoreId("mgba") } returns CoreVersionEntity(coreId = "mgba", installedVersion = "2026-09-30")

        val stamp = stamper.stampFor(game, EmulatorRegistry.BUILTIN_ID)

        assertEquals(SnapshotEmulatorStamp("libretro", null, "mgba", "2026-09-30"), stamp)
    }

    @Test
    fun `retroarch reports its app version and the core's info version`() = runBlocking {
        coEvery { client.resolveCoreForGame(game, "retroarch") } returns "mupen64plus_next_gles3"
        installed("com.retroarch", "1.19.1")
        every { extractor.getRetroArchCoreVersion("mupen64plus_next_gles3", "com.retroarch") } returns "2.6"

        val stamp = stamper.stampFor(game, "retroarch")

        assertEquals(SnapshotEmulatorStamp("retroarch", "1.19.1", "mupen64plus_next", "2.6"), stamp)
    }

    @Test
    fun `a standalone emulator reports its id and app version with no core`() = runBlocking {
        coEvery { client.resolveCoreForGame(game, "mgba_standalone") } returns null
        installed("io.mgba.android", "0.10.3")

        val stamp = stamper.stampFor(game, "mgba_standalone")

        assertEquals(SnapshotEmulatorStamp("mgba_standalone", "0.10.3", null, null), stamp)
    }

    @Test
    fun `unknown versions are left out of the manifest`() {
        val manifest = SnapshotEmulatorStamp("libretro", null, "mgba", null).writeTo(JSONObject())

        assertEquals("libretro", manifest.getString("emulator"))
        assertEquals("mgba", manifest.getString("core"))
        assertTrue(!manifest.has("emulator_version") && !manifest.has("core_version"))
    }
}
