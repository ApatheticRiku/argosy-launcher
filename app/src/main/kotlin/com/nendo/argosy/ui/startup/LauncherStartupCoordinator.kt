package com.nendo.argosy.ui.startup

import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.EmulatorUpdateManager
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.home.delegates.HomeLibraryDelegate
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LauncherStartup"
private const val STORAGE_READY_TIMEOUT_MS = 10_000L
private const val STORAGE_RETRY_MS = 30_000L

/**
 * The launcher's startup, run once per process whichever surface composes first: recovers a
 * session the last process left open, waits for storage, runs the maintenance pass and loads the
 * home library. [status] names the step for the splash; [complete] turns true once home can render.
 */
@Singleton
class LauncherStartupCoordinator @Inject constructor(
    private val playSessionTracker: PlaySessionTracker,
    private val gameRepository: GameRepository,
    private val startupMaintenance: StartupMaintenanceCoordinator,
    private val homeLibraryDelegate: HomeLibraryDelegate,
    private val emulatorUpdateManager: EmulatorUpdateManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, TAG)

    private val _status = MutableStateFlow<Int?>(null)
    val status: StateFlow<Int?> = _status.asStateFlow()

    private val _complete = MutableStateFlow(false)
    val complete: StateFlow<Boolean> = _complete.asStateFlow()

    private val run = scope.launch(start = CoroutineStart.LAZY) {
        _status.value = R.string.ui_startup_status_initializing
        playSessionTracker.endSession()

        while (!gameRepository.awaitStorageReady(timeoutMs = STORAGE_READY_TIMEOUT_MS)) {
            Logger.warn(TAG, "Storage not ready after timeout, retrying")
            _status.value = R.string.ui_startup_status_waiting_for_storage
            delay(STORAGE_RETRY_MS)
        }

        val statusMirror = launch { startupMaintenance.status.filterNotNull().collect { _status.value = it } }
        try {
            startupMaintenance.awaitPass()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Startup maintenance failed, continuing to home", e)
        } finally {
            statusMirror.cancel()
        }

        _status.value = R.string.ui_startup_status_preparing_home
        homeLibraryDelegate.ensureInitialLoad(scope)
        emulatorUpdateManager.checkIfNeeded()
        _complete.value = true
    }

    fun start() {
        run.start()
    }
}
