package com.nendo.argosy.data.remote.romm

import android.content.Context
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RomMConnectionManagerRegisterTest {

    private val api: RomMApi = mockk(relaxed = true)
    private val registration = slot<RomMDeviceRegistration>()

    private fun manager(serverVersion: String, allowRemoteInstalls: Boolean): RomMConnectionManager {
        val preferences: UserPreferencesRepository = mockk(relaxed = true)
        every { preferences.preferences } returns flowOf(
            UserPreferences(allowRemoteInstalls = allowRemoteInstalls)
        )
        val apiFactory: RomMApiFactory = mockk()
        every { apiFactory.create(any(), any(), any()) } returns api
        coEvery { api.heartbeat() } returns Response.success(
            RomMHeartbeatResponse(system = RomMSystem(version = serverVersion))
        )
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 1L, username = "player", enabled = true, role = "admin")
        )
        coEvery { api.registerDevice(capture(registration)) } returns Response.success(
            RomMDeviceRegistrationResponse(deviceId = "device-1")
        )
        val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
        val accountRepository: RomMAccountRepository = mockk(relaxed = true)
        val biosRepository: BiosRepository = mockk(relaxed = true)
        return RomMConnectionManager(
            context = mockk<Context>(relaxed = true),
            userPreferencesRepository = preferences,
            saveSyncRepository = dagger.Lazy { saveSyncRepository },
            biosRepository = biosRepository,
            rommAccountRepository = dagger.Lazy { accountRepository },
            accountRemovalService = dagger.Lazy { mockk(relaxed = true) },
            syncCoordinator = dagger.Lazy { mockk(relaxed = true) },
            retroAchievementsRepository = dagger.Lazy { mockk(relaxed = true) },
            apiFactory = apiFactory
        )
    }

    private suspend fun registerAgainst(serverVersion: String, allowRemoteInstalls: Boolean): RomMDeviceRegistration {
        val result = manager(serverVersion, allowRemoteInstalls).connect("http://romm.local/", "token")
        assertTrue("connect: $result", result is RomMResult.Success)
        assertTrue("registerDevice was not called", registration.isCaptured)
        return registration.captured
    }

    @Test
    fun `5_4_0 registers the install capability with the setting on`() = runTest {
        val sent = registerAgainst("5.4.0", allowRemoteInstalls = true)

        assertEquals(mapOf("install" to true), sent.capabilities)
    }

    @Test
    fun `5_4_0 registers the install capability with the setting off`() = runTest {
        val sent = registerAgainst("5.4.0", allowRemoteInstalls = false)

        assertEquals(mapOf("install" to false), sent.capabilities)
    }

    @Test
    fun `5_4_1 registers the install capability`() = runTest {
        val sent = registerAgainst("5.4.1", allowRemoteInstalls = true)

        assertEquals(mapOf("install" to true), sent.capabilities)
    }

    @Test
    fun `5_3_9 registers without capabilities`() = runTest {
        val sent = registerAgainst("5.3.9", allowRemoteInstalls = true)

        assertNull(sent.capabilities)
    }
}
