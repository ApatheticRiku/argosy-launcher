package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class SessionRecoveryAttemptsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.repo(): SessionPreferencesRepository {
        val file = tempFolder.newFile("session.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return SessionPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private suspend fun SessionPreferencesRepository.persist(gameId: Long = 5L) = persistActiveSession(
        gameId = gameId,
        emulatorPackage = "com.retroarch",
        startTime = Instant.parse("2026-10-02T10:00:00Z"),
        coreName = null,
        isHardcore = false
    )

    @Test
    fun `a session whose save capture keeps failing is dropped on the third failure`() = runTest {
        val repo = repo()
        repo.persist()

        assertTrue(repo.keepSessionForRetry(maxAttempts = 3))
        assertTrue(repo.keepSessionForRetry(maxAttempts = 3))
        assertNotNull(repo.getPersistedSession())

        assertFalse(repo.keepSessionForRetry(maxAttempts = 3))
        assertNull(repo.getPersistedSession())
    }

    @Test
    fun `a new session starts with a clean count`() = runTest {
        val repo = repo()
        repo.persist()
        repo.keepSessionForRetry(maxAttempts = 3)
        repo.keepSessionForRetry(maxAttempts = 3)

        repo.persist(gameId = 6L)

        assertTrue(repo.keepSessionForRetry(maxAttempts = 3))
        assertEquals(6L, repo.getPersistedSession()?.gameId)
    }
}
