package com.nendo.argosy.domain.model

private const val SERVER_PLAYLIST_PREFIX = "server_playlist:"
private const val SOUNDTRACK_ROM_PREFIX = "soundtrack_rom:"
private const val SOUNDTRACK_GAME_PREFIX = "soundtrack_game:"

sealed interface MusicTrackSource {
    data class Local(val path: String) : MusicTrackSource

    data class Remote(
        val url: String,
        val headers: Map<String, String>
    ) : MusicTrackSource
}

data class MusicQueueTrack(
    val id: String,
    val source: MusicTrackSource,
    val title: String,
    val gameTitle: String? = null,
    val coverPath: String? = null
) {
    val localPath: String? get() = (source as? MusicTrackSource.Local)?.path
}

/**
 * One playable collection the music player can make active. [id] is a stable token used for
 * comparison and never shown; labels come from the UI layer.
 */
sealed interface MusicSelection {
    val id: String

    data object Launcher : MusicSelection {
        override val id: String = MusicSelection.LAUNCHER_ID
    }

    data class ServerPlaylist(
        val playlistId: Long,
        val name: String
    ) : MusicSelection {
        override val id: String get() = "$SERVER_PLAYLIST_PREFIX$playlistId"
    }

    data class GameSoundtrack(
        val romId: Long?,
        val gameId: Long?,
        val title: String
    ) : MusicSelection {
        override val id: String
            get() = romId?.let { "$SOUNDTRACK_ROM_PREFIX$it" } ?: "$SOUNDTRACK_GAME_PREFIX$gameId"
    }

    companion object {
        const val LAUNCHER_ID = "launcher"

        fun isServerPlaylistId(id: String): Boolean = id.startsWith(SERVER_PLAYLIST_PREFIX)

        fun isSoundtrackId(id: String): Boolean =
            id.startsWith(SOUNDTRACK_ROM_PREFIX) || id.startsWith(SOUNDTRACK_GAME_PREFIX)
    }
}

enum class MusicServerStatus {
    AVAILABLE,
    OFFLINE,
    UNSUPPORTED,
    UNAUTHORIZED,
    FAILED
}

data class MusicPlaylistEntry(
    val selection: MusicSelection,
    val trackCount: Int?,
    val ownerName: String? = null
)

data class MusicPlaylistCatalog(
    val entries: List<MusicPlaylistEntry>,
    val serverStatus: MusicServerStatus
)

data class SoundtrackGameEntry(
    val selection: MusicSelection.GameSoundtrack,
    val platformName: String?,
    val coverPath: String?,
    val trackCount: Int
)

data class SoundtrackGamePage(
    val entries: List<SoundtrackGameEntry>,
    val hasMore: Boolean,
    val fromServer: Boolean,
    val serverStatus: MusicServerStatus
)
