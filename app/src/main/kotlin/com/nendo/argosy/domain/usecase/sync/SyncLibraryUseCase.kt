package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.ui.screens.common.LibrarySyncBus
import com.nendo.argosy.util.Logger
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SyncLibraryUseCase"
private const val NOTIFICATION_KEY = SyncNotificationKeys.LIBRARY

/**
 * Games between notification refreshes. Progress advances once per rom, which is the right
 * granularity for a bar on screen and far too fine for a system notification.
 */
private const val PROGRESS_NOTIFICATION_STEP = 25

sealed class SyncLibraryResult {
    data class Success(val result: SyncResult) : SyncLibraryResult()
    data class Error(val reason: SyncLibraryFailureReason) : SyncLibraryResult()
    data object AlreadyInProgress : SyncLibraryResult()
}

/**
 * Why a library sync could not run or did not finish. [PlatformCountFailed] and [Unexpected]
 * keep the server or exception text as-is because it is not this app's sentence to translate.
 */
sealed class SyncLibraryFailureReason {
    data object NotConnected : SyncLibraryFailureReason()
    data class PlatformCountFailed(val serverMessage: String) : SyncLibraryFailureReason()
    data class Unexpected(val message: String?) : SyncLibraryFailureReason()
}

class SyncLibraryUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val librarySyncBus: LibrarySyncBus,
    private val copy: SyncNotificationCopy
) {
    internal var progressDispatcher: CoroutineDispatcher = Dispatchers.IO
    /**
     * Runs a library pass. With [changesSince] it syncs only what the server changed after that
     * time, and falls back to a full pass if that fails.
     */
    suspend operator fun invoke(
        initializeFirst: Boolean = false,
        changesSince: java.time.Instant? = null,
        onProgress: ((current: Int, total: Int, platform: String) -> Unit)? = null
    ): SyncLibraryResult {
        Logger.info(TAG, "invoke: starting, initializeFirst=$initializeFirst, changesSince=$changesSince")

        if (initializeFirst) {
            romMRepository.initialize()
        }

        if (!romMRepository.isConnected()) {
            Logger.info(TAG, "invoke: not connected")
            return SyncLibraryResult.Error(SyncLibraryFailureReason.NotConnected)
        }

        Logger.info(TAG, "invoke: fetching platform count")
        return when (val summary = romMRepository.getPlatformCount()) {
            is RomMResult.Error -> {
                Logger.error(TAG, "invoke: platform count error: ${summary.message}")
                notificationManager.show(
                    title = copy.libraryStartFailedTitle(),
                    subtitle = copy.libraryFailureDetail(summary.message),
                    type = NotificationType.ERROR
                )
                SyncLibraryResult.Error(SyncLibraryFailureReason.PlatformCountFailed(summary.message))
            }
            is RomMResult.Success -> {
                val platformCount = summary.data
                Logger.info(TAG, "invoke: got $platformCount platforms, showing persistent")

                notificationManager.showPersistent(
                    title = copy.libraryProgressTitle(),
                    subtitle = copy.libraryProgressStarting(),
                    key = NOTIFICATION_KEY,
                    progress = NotificationProgress(0, platformCount)
                )

                try {
                    withContext(NonCancellable) {
                        Logger.info(TAG, "invoke: calling syncLibrary")
                        val progressJob = CoroutineScope(progressDispatcher).launch {
                            romMRepository.syncProgress.distinctUntilChanged { old, new ->
                                old.isSyncing == new.isSyncing &&
                                    old.currentPlatform == new.currentPlatform &&
                                    old.platformsDone == new.platformsDone &&
                                    old.gamesDone / PROGRESS_NOTIFICATION_STEP ==
                                    new.gamesDone / PROGRESS_NOTIFICATION_STEP
                            }.collect { sp ->
                                if (sp.isSyncing && sp.currentPlatform.isNotEmpty()) {
                                    val bar = if (sp.gamesTotal > 0) {
                                        NotificationProgress(sp.gamesDone, sp.gamesTotal)
                                    } else {
                                        NotificationProgress(sp.platformsDone + 1, sp.platformsTotal)
                                    }
                                    notificationManager.updatePersistent(
                                        key = NOTIFICATION_KEY,
                                        subtitle = copy.libraryProgressPlatform(
                                            sp.currentPlatform,
                                            sp.gamesDone,
                                            sp.gamesTotal
                                        ),
                                        progress = bar,
                                        platformSlug = sp.currentPlatformSlug.takeIf { it.isNotBlank() }
                                    )
                                }
                            }
                        }
                        val changes = changesSince?.let { romMRepository.syncLibraryChanges(it) }
                        if (changes != null && changes.errors.isNotEmpty()) {
                            Logger.warn(TAG, "invoke: changes pass failed (${changes.errors}), running a full pass")
                        }
                        val result = changes?.takeIf { it.errors.isEmpty() || it.alreadyInProgress }
                            ?: romMRepository.syncLibrary { current, total, platform ->
                                Logger.info(TAG, "invoke: progress $current/$total - $platform")
                                onProgress?.invoke(current, total, platform)
                            }
                        progressJob.cancel()

                        Logger.info(TAG, "invoke: syncLibrary returned - added=${result.gamesAdded}, updated=${result.gamesUpdated}, deleted=${result.gamesDeleted}, errors=${result.errors}")

                        if (result.alreadyInProgress) {
                            Logger.info(TAG, "invoke: sync already in progress, returning silently")
                            notificationManager.dismissByKey(NOTIFICATION_KEY)
                            return@withContext SyncLibraryResult.AlreadyInProgress
                        }

                        Logger.info(TAG, "invoke: syncing favorites")
                        romMRepository.syncFavorites()

                        if (result.errors.isEmpty()) {
                            Logger.info(TAG, "invoke: completing with success")
                            notificationManager.completePersistent(
                                key = NOTIFICATION_KEY,
                                title = copy.libraryCompleteTitle(),
                                subtitle = copy.libraryCompleteCounts(
                                    result.gamesAdded,
                                    result.gamesUpdated,
                                    result.gamesDeleted
                                ),
                                type = NotificationType.SUCCESS
                            )
                        } else {
                            Logger.info(TAG, "invoke: completing with errors")
                            notificationManager.completePersistent(
                                key = NOTIFICATION_KEY,
                                title = copy.libraryCompletedWithErrorsTitle(),
                                subtitle = copy.libraryFailedPlatforms(result.errors.size),
                                type = NotificationType.ERROR
                            )
                        }

                        librarySyncBus.emitSyncCompleted()
                        SyncLibraryResult.Success(result)
                    }
                } catch (e: Exception) {
                    Logger.error(TAG, "invoke: exception", e)
                    withContext(NonCancellable) {
                        notificationManager.completePersistent(
                            key = NOTIFICATION_KEY,
                            title = copy.libraryFailedTitle(),
                            subtitle = copy.libraryFailureDetail(e.message),
                            type = NotificationType.ERROR
                        )
                    }
                    SyncLibraryResult.Error(SyncLibraryFailureReason.Unexpected(e.message))
                }
            }
        }
    }
}
