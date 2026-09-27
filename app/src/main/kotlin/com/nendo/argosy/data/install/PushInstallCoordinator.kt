package com.nendo.argosy.data.install

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDeviceCapabilitiesUpdate
import com.nendo.argosy.data.remote.romm.RomMDeviceSocket
import com.nendo.argosy.data.remote.romm.RomMInstallReport
import com.nendo.argosy.data.remote.romm.RomMInstallRequest
import com.nendo.argosy.data.remote.romm.deviceCapabilities
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.domain.usecase.download.PushInstallFailure
import com.nendo.argosy.domain.usecase.download.PushInstallOutcome
import com.nendo.argosy.domain.usecase.download.PushInstallUseCase
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushInstallCoordinator"
private const val STATUS_DONE = "done"
private const val STATUS_ALREADY_INSTALLED = "already_installed"
private const val STATUS_FAILED = "failed"
private const val REASON_MAX_LENGTH = 500

/**
 * Receives installs pushed from RomM: reports this device's install capability, holds the
 * `/devices` socket while remote installs are allowed, and drains the claim endpoint on app
 * start, on every return to the foreground, on socket (re)connect and on `install:queued`.
 * Nothing runs against a server older than
 * [com.nendo.argosy.data.remote.romm.RomMCapabilities.DEVICE_INSTALL_MIN_VERSION].
 */
@Singleton
class PushInstallCoordinator @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val accountRepository: RomMAccountRepository,
    private val deviceSocket: RomMDeviceSocket,
    private val pushInstall: dagger.Lazy<PushInstallUseCase>
) {
    private data class Session(
        val baseUrl: String,
        val token: String,
        val deviceId: String,
        val allowRemoteInstalls: Boolean
    )

    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val refreshMutex = Mutex()
    private val drainMutex = Mutex()
    private val drainRequested = AtomicBoolean(false)
    private var reportedCapability: Pair<String, Boolean>? = null

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch { refresh() }
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        scope.launch {
            combine(
                connectionManager.connectionState,
                connectionManager.deviceIdState,
                preferencesRepository.preferences.map { it.allowRemoteInstalls }.distinctUntilChanged(),
                accountRepository.observeActiveAccount().map { it?.id }.distinctUntilChanged()
            ) { state, deviceId, allow, accountId -> listOf(state, deviceId, allow, accountId) }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        scope.launch {
            deviceSocket.events.collect { event ->
                when (event) {
                    RomMDeviceSocket.Event.Connected, RomMDeviceSocket.Event.InstallQueued -> requestDrain()
                    is RomMDeviceSocket.Event.Refused -> Unit
                }
            }
        }
    }

    private suspend fun currentSession(): Session? {
        val connected = connectionManager.connectionState.value as? ConnectionState.Connected ?: return null
        if (!connected.capabilities.supportsDeviceInstall) return null
        if (accountRepository.activeAccount() == null) return null
        val deviceId = connectionManager.getDeviceId() ?: return null
        val token = connectionManager.getAccessToken() ?: return null
        val baseUrl = connectionManager.getBaseUrl().takeIf { it.isNotBlank() } ?: return null
        val allow = preferencesRepository.preferences.first().allowRemoteInstalls
        return Session(baseUrl, token, deviceId, allow)
    }

    private suspend fun refresh() {
        val session = refreshMutex.withLock {
            val current = currentSession()
            if (current == null) {
                deviceSocket.disconnect()
                return@withLock null
            }
            reportCapability(current)
            if (current.allowRemoteInstalls) {
                deviceSocket.connect(RomMDeviceSocket.Target(current.baseUrl, current.token))
            } else {
                deviceSocket.disconnect()
            }
            current
        }
        if (session?.allowRemoteInstalls == true) requestDrain()
    }

    private suspend fun reportCapability(session: Session) {
        val report = session.deviceId to session.allowRemoteInstalls
        if (reportedCapability == report) return
        val api = connectionManager.getApi() ?: return
        val sent = try {
            api.updateDeviceCapabilities(
                session.deviceId,
                RomMDeviceCapabilitiesUpdate(deviceCapabilities(session.allowRemoteInstalls))
            ).isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "reportCapability: ${e.message}")
            false
        }
        if (sent) reportedCapability = report
        Logger.info(TAG, "reportCapability: install=${session.allowRemoteInstalls} sent=$sent")
    }

    private fun requestDrain() {
        drainRequested.set(true)
        scope.launch {
            while (drainRequested.get()) {
                if (!drainMutex.tryLock()) return@launch
                try {
                    while (drainRequested.getAndSet(false)) drainOnce()
                } finally {
                    drainMutex.unlock()
                }
            }
        }
    }

    private suspend fun drainOnce() {
        val session = currentSession() ?: return
        if (!session.allowRemoteInstalls) return
        val api = connectionManager.getApi() ?: return
        val claimed = try {
            val response = api.claimInstallRequests(session.deviceId)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "drain: claim returned ${response.code()}")
                return
            }
            response.body().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "drain: claim failed: ${e.message}")
            return
        }
        if (claimed.isEmpty()) return
        Logger.info(TAG, "drain: claimed ${claimed.size} install request(s)")
        for (request in claimed) {
            report(session.deviceId, request, install(request))
        }
    }

    private suspend fun install(request: RomMInstallRequest): PushInstallOutcome = try {
        pushInstall.get()(request.romId, request.fileIds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "install: request ${request.id} for rom ${request.romId} threw", e)
        PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, e.message)
    }

    private suspend fun report(deviceId: String, request: RomMInstallRequest, outcome: PushInstallOutcome) {
        val body = when (outcome) {
            PushInstallOutcome.Queued -> RomMInstallReport(STATUS_DONE)
            PushInstallOutcome.AlreadyInstalled -> RomMInstallReport(STATUS_ALREADY_INSTALLED)
            is PushInstallOutcome.Failed -> RomMInstallReport(STATUS_FAILED, failureReason(outcome))
        }
        Logger.info(TAG, "report: request ${request.id} rom ${request.romId} -> ${body.status} ${body.reason.orEmpty()}")
        val api = connectionManager.getApi() ?: return
        try {
            val response = api.reportInstallRequest(deviceId, request.id, body)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "report: request ${request.id} returned ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "report: request ${request.id} failed: ${e.message}")
        }
    }

    private fun failureReason(outcome: PushInstallOutcome.Failed): String {
        val base = when (outcome.failure) {
            PushInstallFailure.ROM_UNRESOLVED -> "rom could not be resolved"
            PushInstallFailure.NO_EMULATOR -> "no emulator for this platform"
            PushInstallFailure.INSUFFICIENT_STORAGE -> "insufficient storage"
            PushInstallFailure.NO_INSTALLABLE_FILES -> "no installable files requested"
            PushInstallFailure.DOWNLOAD_ERROR -> "download error"
        }
        val full = outcome.detail?.let { "$base: $it" } ?: base
        return full.take(REASON_MAX_LENGTH)
    }
}
