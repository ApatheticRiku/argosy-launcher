package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.model.ArtSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ArtworkDelegate @Inject constructor(
    private val imageCacheManager: ImageCacheManager,
    private val notificationManager: NotificationManager
) {

    /**
     * Stores [source] as [slot]'s override. [source] is an absolute local path or a remote url.
     * A failure raises an error notice; [onFinished] runs either way.
     */
    fun applyOverride(
        scope: CoroutineScope,
        gameId: Long,
        slot: ArtSlot,
        source: String,
        onFinished: () -> Unit
    ) {
        scope.launch {
            val applied = if (source.startsWith("/")) {
                imageCacheManager.applyArtOverrideFromFile(gameId, slot, source)
            } else {
                imageCacheManager.applyArtOverride(gameId, slot, source)
            }
            if (!applied) {
                notificationManager.showError(NotificationText.Res(R.string.gamedetail_notice_artwork_failed))
            }
            onFinished()
        }
    }

    fun revertOverride(scope: CoroutineScope, gameId: Long, slot: ArtSlot, onFinished: () -> Unit) {
        scope.launch {
            imageCacheManager.clearArtOverride(gameId, slot)
            onFinished()
        }
    }
}
