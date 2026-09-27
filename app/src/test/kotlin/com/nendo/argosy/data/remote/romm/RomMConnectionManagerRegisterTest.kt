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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RomMConnectionManagerRegisterTest {

    private val api: RomMApi = mockk(relaxed = true)

    private fun manager(serverVersion: String): RomMConnectionManager {
        val preferences: UserPreferencesRepository = mockk(relaxed = true)
        every { preferences.preferences } returns flowOf(UserPreferences())
        val apiFactory: RomMApiFactory = mockk()
        every { apiFactory.create(any(), any(), any()) } returns api
        coEvery { api.heartbeat() } returns Response.success(
            RomMHeartbeatResponse(system = RomMSystem(version = serverVersion))
        )
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 1L, username = "player", enabled = true, role = "admin")
        )
        coEvery { api.registerDevice(any()) } returns Response.success(
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

    @Test
    fun `a fresh registration publishes its device id`() = runTest {
        val manager = manager("5.4.0")

        val result = manager.connect("http://romm.local/", "token")

        assertTrue("connect: $result", result is RomMResult.Success)
        assertEquals("device-1", manager.deviceIdState.value)
    }
}
