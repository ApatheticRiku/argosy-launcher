package com.nendo.argosy.data.repository

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiblingGroupDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val siblingGroupRepository: SiblingGroupRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "SiblingGroupDownloadObserver")

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                if (event.isDiscDownload) return@collect
                scope.launch { siblingGroupRepository.refreshRommMainSibling(event.gameId) }
            }
        }
    }
}
