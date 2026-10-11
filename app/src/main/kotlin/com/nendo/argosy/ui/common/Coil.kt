package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import com.nendo.argosy.data.model.ArtSlot
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.model.ResolvedGameArt
import java.io.File

val LocalImageCacheManager = staticCompositionLocalOf<ImageCacheManager?> { null }

/**
 * Resolves a path string into a Coil-compatible image model. Absolute filesystem
 * paths (those starting with "/") become a [File] so Coil treats them as local
 * disk reads; everything else is passed through verbatim, leaving Coil to
 * interpret it as a URL or other supported model.
 */
@Composable
fun rememberFileImageModel(path: String?): Any? = remember(path) {
    when {
        path == null -> null
        path.startsWith("/") -> File(path)
        else -> path
    }
}

/**
 * Non-composable variant for call sites that build models off the composition,
 * e.g. inside lambdas passed to image prefetch helpers.
 */
fun fileImageModel(path: String?): Any? = when {
    path == null -> null
    path.startsWith("/") -> File(path)
    else -> path
}

/**
 * [gameId]'s live art from the image cache's art model, or null before the model knows the game.
 * Updates as the art is cached or overridden without touching any `games` query.
 */
@Composable
fun rememberResolvedArt(gameId: Long): ResolvedGameArt? {
    val manager = LocalImageCacheManager.current ?: return null
    val flow = remember(manager, gameId) { manager.observeArt(gameId) }
    val art by flow.collectAsState(initial = manager.artFor(gameId))
    return art
}

/**
 * A callback that asks the image cache to download a game's art slot again, for art a screen
 * failed to draw. Does nothing where no image cache is provided.
 */
@Composable
fun rememberArtRepair(): (gameId: Long, slot: ArtSlot) -> Unit {
    val manager = LocalImageCacheManager.current
    return remember(manager) { { gameId, slot -> manager?.repairMissingArt(gameId, slot) } }
}

@Composable
fun rememberResolvedCoverPath(gameId: Long, fallback: String?): String? =
    rememberResolvedArt(gameId)?.coverPath ?: fallback

@Composable
fun rememberResolvedBackgroundPath(gameId: Long, fallback: String?): String? =
    rememberResolvedArt(gameId)?.backgroundPath ?: fallback
