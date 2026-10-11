package com.nendo.argosy.ui.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.nendo.argosy.data.music.AudioLoudnessRepository
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAG = "AmbientAudio"
private const val LAUNCHER_FOCUS_HANDOFF_MS = 300L
private const val TARGET_LOUDNESS_DB = -14.0
private const val GAIN_MIN_DB = -12.0
private const val GAIN_MAX_DB = 10.0

sealed interface AmbientOverrideSource {
    val displayName: String

    data class Local(
        val path: String,
        override val displayName: String
    ) : AmbientOverrideSource

    data class Remote(
        val url: String,
        val headers: Map<String, String>,
        override val displayName: String
    ) : AmbientOverrideSource
}

private class PlayerSlot(
    val player: MediaPlayer,
    val autoStart: Boolean,
    var prepared: Boolean = false,
    var startWhenPrepared: Boolean = false
)

@Singleton
class AmbientAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loudnessRepository: AudioLoudnessRepository
) {
    companion object {
        const val AMBIENT_SOURCE_PLAYLIST = "playlist:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val gate = AmbientPlaybackGate()
    private val queue = PlaybackQueue()

    private var slot: PlayerSlot? = null
    private var playing = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var fadeOutCancelled = false
    private var fadingOut = false
    private var consecutiveFailures = 0

    private var launcherSet = false
    private var launcherTracks: List<MusicQueueTrack> = emptyList()
    private var launcherRefresh: (suspend () -> List<MusicQueueTrack>)? = null
    private var activeSourceId: String = MusicSelection.LAUNCHER_ID
    private var activeSourceLabel: String? = null
    private var refreshJob: Job? = null
    private var generation = 0

    private var overrideActive = false
    private var overrideToken = 0
    private var overrideTitle: String? = null
    private var stashedSlot: PlayerSlot? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var trackGainDb = 0f
    private var logicalVolume = 0f

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _currentTrackName = MutableStateFlow<String?>(null)
    val currentTrackName: StateFlow<String?> = _currentTrackName.asStateFlow()

    private val _playback = MutableStateFlow(AmbientPlaybackState())
    val playback: StateFlow<AmbientPlaybackState> = _playback.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        val wasEnabled = gate.enabled
        gate.enabled = enabled
        Logger.verbose(TAG) { "setEnabled=$enabled" }
        if (!enabled) {
            stopAndRelease()
        } else if (!wasEnabled) {
            gate.userPaused = false
            publish()
        }
    }

    val currentVolume: Float get() = targetVolume

    fun setVolume(volume: Int) {
        this.targetVolume = (volume / 100f).coerceIn(0f, 1f)
        Logger.verbose(TAG) { "setVolume=$volume ($targetVolume)" }
        slot?.let { applyPlayerVolume(it.player, targetVolume) }
    }

    private fun applyPlayerVolume(player: MediaPlayer, volume: Float) {
        logicalVolume = volume
        val attenuation = if (trackGainDb < 0f) 10.0.pow(trackGainDb / 20.0).toFloat() else 1f
        val level = (volume * attenuation).coerceIn(0f, 1f)
        runCatching { player.setVolume(level, level) }
    }

    private fun attachLeveling(player: MediaPlayer, localPath: String?) {
        releaseEnhancer()
        trackGainDb = 0f
        loudnessEnhancer = try {
            LoudnessEnhancer(player.audioSessionId)
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable, attenuation-only leveling: ${e.message}")
            null
        }
        if (localPath == null) {
            applyLeveling(player)
            return
        }
        scope.launch {
            val meanDb = loudnessRepository.playbackMeanDb(localPath)
            if (slot?.player !== player) return@launch
            trackGainDb = computeGainDb(meanDb)
            applyLeveling(player)
        }
    }

    private fun computeGainDb(meanDb: Double?): Float =
        if (meanDb == null) 0f
        else (TARGET_LOUDNESS_DB - meanDb).coerceIn(GAIN_MIN_DB, GAIN_MAX_DB).toFloat()

    private fun applyLeveling(player: MediaPlayer) {
        val boostMb = (trackGainDb.coerceAtLeast(0f) * 100).roundToInt()
        loudnessEnhancer?.let { enhancer ->
            try {
                enhancer.setTargetGain(boostMb)
                enhancer.setEnabled(boostMb > 0)
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer apply failed, attenuation-only leveling: ${e.message}")
                releaseEnhancer()
            }
        }
        applyPlayerVolume(player, logicalVolume)
    }

    private fun releaseEnhancer() {
        loudnessEnhancer?.let { runCatching { it.release() } }
        loudnessEnhancer = null
    }

    fun setShuffle(shuffle: Boolean) {
        if (!queue.setShuffle(shuffle)) return
        Logger.verbose(TAG) { "setShuffle=$shuffle" }
        publish()
    }

    private var playInBackground = false

    fun setPlayInBackground(enabled: Boolean) {
        playInBackground = enabled
    }

    fun keepsPlayingAsleep(): Boolean =
        playInBackground && context.getSystemService(PowerManager::class.java)?.isInteractive == false

    private val focusedLauncherWindows = mutableSetOf<String>()
    private var focusLossFade: Job? = null

    fun onLauncherWindowFocused(window: String) {
        focusedLauncherWindows += window
        focusLossFade?.cancel()
        focusLossFade = null
    }

    /**
     * Fades the music once no launcher window holds focus, after a short wait so focus moving
     * between the launcher's own screens never silences it.
     */
    fun onLauncherWindowUnfocused(window: String) {
        focusedLauncherWindows -= window
        if (focusedLauncherWindows.isNotEmpty()) return
        focusLossFade?.cancel()
        focusLossFade = scope.launch {
            delay(LAUNCHER_FOCUS_HANDOFF_MS)
            if (focusedLauncherWindows.isEmpty() && !keepsPlayingAsleep()) fadeOut()
        }
    }

    /**
     * Supplies the launcher playlist and the refresh that re-expands it at each full loop. The
     * queue only follows it while the launcher playlist is the active selection.
     */
    fun setLauncherSource(
        tracks: List<MusicQueueTrack>,
        refresh: (suspend () -> List<MusicQueueTrack>)? = null
    ) {
        launcherRefresh = refresh
        if (launcherSet && tracks == launcherTracks) return
        val firstLoad = !launcherSet
        launcherSet = true
        launcherTracks = tracks
        if (activeSourceId != MusicSelection.LAUNCHER_ID) return
        Log.d(TAG, "setLauncherSource: ${tracks.size} tracks")

        generation++
        refreshJob?.cancel()

        if (firstLoad) {
            if (!overrideActive) stopAndRelease()
            queue.load(tracks)
            if (gate.enabled && !queue.isEmpty && !overrideActive) {
                queue.current?.let { preparePlayer(it, autoStart = false) }
            }
            publish()
            return
        }
        applyQueueUpdate(tracks)
    }

    fun playSelection(sourceId: String, sourceLabel: String?, tracks: List<MusicQueueTrack>) {
        generation++
        refreshJob?.cancel()
        activeSourceId = sourceId
        activeSourceLabel = sourceLabel
        gate.userPaused = false
        gate.suspended = false
        consecutiveFailures = 0
        dropOverride()
        releasePlayer()
        queue.load(tracks)
        Log.d(TAG, "playSelection: $sourceId, ${tracks.size} tracks")
        queue.current?.let { preparePlayer(it, autoStart = true) }
        publish()
    }

    fun activateLauncher() {
        if (activeSourceId == MusicSelection.LAUNCHER_ID && !queue.isEmpty) {
            play()
            return
        }
        playSelection(MusicSelection.LAUNCHER_ID, null, launcherTracks)
    }

    fun play() {
        gate.userPaused = false
        gate.suspended = false
        Log.d(TAG, "user play")
        fadeIn()
        publish()
    }

    fun pause() {
        gate.userPaused = true
        Log.d(TAG, "user pause")
        fadeOut()
        publish()
    }

    fun skipNext() {
        if (queue.isEmpty) return
        gate.suspended = false
        consecutiveFailures = 0
        dropOverride()
        advanceQueue(resume = true)
    }

    fun skipPrevious() {
        if (queue.isEmpty) return
        gate.suspended = false
        consecutiveFailures = 0
        if (overrideActive) {
            dropOverride()
            restartAtCurrentIndex(resume = true)
            return
        }
        when (queue.previousAction(positionSnapshot()?.positionMs ?: 0L)) {
            PreviousAction.RESTART -> {
                val current = slot
                if (current != null && current.prepared) {
                    runCatching { current.player.seekTo(0) }
                } else {
                    restartAtCurrentIndex(resume = true)
                }
            }
            PreviousAction.STEP_BACK -> {
                queue.stepBack()
                restartAtCurrentIndex(resume = true)
            }
        }
    }

    fun playAt(position: Int) {
        if (!queue.jumpTo(position)) return
        consecutiveFailures = 0
        gate.userPaused = false
        gate.suspended = false
        dropOverride()
        restartAtCurrentIndex(resume = true)
    }

    fun positionSnapshot(): PlaybackPosition? {
        val current = slot ?: return null
        if (!current.prepared) return null
        return runCatching {
            PlaybackPosition(
                positionMs = current.player.currentPosition.toLong().coerceAtLeast(0L),
                durationMs = current.player.duration.toLong().coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    private fun applyQueueUpdate(tracks: List<MusicQueueTrack>) {
        val survived = queue.replaceKeepingCurrent(tracks)

        if (queue.isEmpty) {
            Log.w(TAG, "Playlist emptied while active")
            if (overrideActive) {
                releaseStash()
            } else {
                stopAndRelease()
            }
            publish()
            return
        }

        if (!survived && gate.enabled) {
            restartAtCurrentIndex(resume = playing)
        } else {
            publish()
        }
    }

    private fun preparePlayer(track: MusicQueueTrack, autoStart: Boolean) {
        val localPath = track.localPath
        if (localPath != null && !validatePath(localPath)) {
            Log.w(TAG, "Audio file not accessible: $localPath")
            onTrackFailed(resume = autoStart)
            return
        }

        val player = MediaPlayer()
        val newSlot = PlayerSlot(player, autoStart)
        try {
            player.setAudioAttributes(audioAttributes)
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            when (val source = track.source) {
                is MusicTrackSource.Local -> player.setDataSource(source.path)
                is MusicTrackSource.Remote ->
                    player.setDataSource(context, Uri.parse(source.url), source.headers)
            }
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                newSlot.prepared = true
                if (slot !== newSlot) return@setOnPreparedListener
                Log.d(TAG, "MediaPlayer prepared: ${track.title}")
                if (newSlot.autoStart || newSlot.startWhenPrepared) fadeIn()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                if (slot === newSlot) {
                    onTrackFailed(resume = playing || newSlot.autoStart || newSlot.startWhenPrepared)
                }
                true
            }
            player.setOnCompletionListener {
                if (slot !== newSlot) return@setOnCompletionListener
                Log.d(TAG, "Track completed")
                consecutiveFailures = 0
                advanceQueue(resume = true)
            }
            slot = newSlot
            player.prepareAsync()
            attachLeveling(player, localPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            runCatching { player.release() }
            if (slot === newSlot) slot = null
            onTrackFailed(resume = autoStart)
        }
    }

    private fun onTrackFailed(resume: Boolean) {
        consecutiveFailures++
        if (consecutiveFailures >= queue.size.coerceAtLeast(1)) {
            Log.w(TAG, "Every track in the queue failed; stopping")
            consecutiveFailures = 0
            releasePlayer()
            publish()
            return
        }
        advanceQueue(resume)
    }

    private fun advanceQueue(resume: Boolean) {
        if (overrideActive) {
            releaseStash()
            return
        }
        if (queue.isEmpty) return
        releasePlayer()
        if (!queue.advance()) {
            refreshAndRestart(resume)
            return
        }
        Log.d(TAG, "Playing next track: ${queue.current?.title}")
        restartAtCurrentIndex(resume)
    }

    private fun refreshAndRestart(resume: Boolean) {
        val gen = generation
        val launcherActive = activeSourceId == MusicSelection.LAUNCHER_ID
        val refresh = if (launcherActive) launcherRefresh else null
        val snapshot = queue.sourceTracks
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                refresh?.invoke() ?: snapshot.filter { track ->
                    track.localPath?.let { File(it).canRead() } ?: true
                }
            }
            if (gen != generation) return@launch
            if (launcherActive) launcherTracks = fresh
            queue.load(fresh)
            if (queue.isEmpty) {
                Log.w(TAG, "No more tracks in playlist")
                setPlaying(false)
                return@launch
            }
            Log.d(TAG, "Playlist loop refreshed: ${queue.size} tracks")
            restartAtCurrentIndex(resume)
        }
    }

    private fun restartAtCurrentIndex(resume: Boolean) {
        if (overrideActive) {
            releaseStash()
            publish()
            return
        }
        releasePlayer()
        val track = queue.current
        if (track == null) {
            publish()
            return
        }
        preparePlayer(track, autoStart = resume)
        publish()
    }

    private fun releasePlayer() {
        val outgoing = slot ?: return
        slot = null
        fadeAnimator?.cancel()
        fadeAnimator = null
        fadingOut = false
        releaseEnhancer()
        runCatching { outgoing.player.release() }
        setPlaying(false)
    }

    private fun validatePath(path: String): Boolean {
        return try {
            File(path).canRead()
        } catch (e: Exception) {
            Log.w(TAG, "Path validation failed: ${e.message}")
            false
        }
    }

    /**
     * Fades out current playback and loops the given track on top of the playlist,
     * leaving playlist position untouched until [clearOverride].
     */
    suspend fun playOverride(source: AmbientOverrideSource) = withContext(Dispatchers.Main.immediate) {
        if (!gate.enabled) {
            Log.d(TAG, "playOverride skipped: disabled")
            return@withContext
        }
        if (source is AmbientOverrideSource.Local && !validatePath(source.path)) {
            Log.w(TAG, "playOverride skipped: unreadable path ${source.path}")
            return@withContext
        }
        overrideToken++
        val token = overrideToken
        overrideActive = true
        Log.d(TAG, "playOverride: ${source.displayName}")
        fadeOut {
            if (token != overrideToken) return@fadeOut
            detachCurrentPlayerForOverride()
            prepareOverride(source, token)
        }
    }

    /** Fades out an active override and resumes the underlying playlist where it left off. */
    fun clearOverride() {
        if (!overrideActive) return
        overrideToken++
        val token = overrideToken
        overrideActive = false
        overrideTitle = null
        Log.d(TAG, "clearOverride")
        fadeOut {
            if (token != overrideToken || overrideActive) return@fadeOut
            val outgoing = slot
            slot = null
            releaseEnhancer()
            runCatching { outgoing?.player?.release() }
            resumePlaylistAfterOverride()
        }
    }

    private fun dropOverride() {
        if (!overrideActive) return
        overrideToken++
        overrideActive = false
        overrideTitle = null
        releasePlayer()
        releaseStash()
        Log.d(TAG, "override dropped for user playback")
    }

    private fun detachCurrentPlayerForOverride() {
        val outgoing = slot ?: return
        slot = null
        if (stashedSlot == null) {
            stashedSlot = outgoing
        } else {
            runCatching { outgoing.player.release() }
        }
    }

    private fun prepareOverride(source: AmbientOverrideSource, token: Int) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            when (source) {
                is AmbientOverrideSource.Local -> player.setDataSource(source.path)
                is AmbientOverrideSource.Remote ->
                    player.setDataSource(context, Uri.parse(source.url), source.headers)
            }
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                if (token != overrideToken || !overrideActive) {
                    runCatching { it.release() }
                    return@setOnPreparedListener
                }
                slot = PlayerSlot(player, autoStart = true, prepared = true)
                attachLeveling(player, (source as? AmbientOverrideSource.Local)?.path)
                overrideTitle = source.displayName
                _currentTrackName.value = source.displayName
                publish()
                fadeIn()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Override error: what=$what extra=$extra")
                if (token == overrideToken && overrideActive) {
                    clearOverride()
                } else {
                    runCatching { player.release() }
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare override", e)
            if (token == overrideToken && overrideActive) clearOverride()
        }
    }

    private fun resumePlaylistAfterOverride() {
        val stash = stashedSlot
        stashedSlot = null
        publish()
        if (!gate.enabled) {
            runCatching { stash?.player?.release() }
            return
        }
        if (stash != null) {
            slot = stash
            attachLeveling(stash.player, queue.current?.localPath)
            fadeIn()
        } else {
            restartAtCurrentIndex(resume = true)
        }
    }

    private fun releaseStash() {
        val stash = stashedSlot ?: return
        stashedSlot = null
        runCatching { stash.player.release() }
    }

    fun suspend() {
        gate.suspended = true
        fadeOut()
        Log.d(TAG, "suspended - awaiting user input to resume")
    }

    fun resumeFromSuspend() {
        if (gate.suspended) {
            gate.suspended = false
            Logger.verbose(TAG) { "resumed from suspend" }
            fadeIn()
        }
    }

    fun holdSilence() {
        gate.silenceHolds++
        Log.d(TAG, "silence hold acquired (${gate.silenceHolds})")
        fadeOut()
    }

    fun releaseSilence() {
        gate.silenceHolds = (gate.silenceHolds - 1).coerceAtLeast(0)
        Log.d(TAG, "silence hold released (${gate.silenceHolds})")
        if (gate.silenceHolds == 0) fadeIn()
    }

    private var videoSilenceHeld = false

    /**
     * Yields the audio output to the video player until [releaseVideoSilence]. This holds silence
     * rather than stopping: [stopAndRelease] destroys the MediaPlayer along with any active
     * override and its stashed playlist player, so music could only come back from the top of the
     * current track and a game override would be lost, whereas a silence hold fades out a prepared
     * player that keeps its position and fades back in where it left off.
     *
     * Idempotent in both directions, so the player can call it from every path that starts or ends
     * playback without unbalancing the underlying hold count.
     */
    fun holdVideoSilence() {
        scope.launch(Dispatchers.Main.immediate) {
            if (videoSilenceHeld) return@launch
            videoSilenceHeld = true
            holdSilence()
        }
    }

    fun releaseVideoSilence() {
        scope.launch(Dispatchers.Main.immediate) {
            if (!videoSilenceHeld) return@launch
            videoSilenceHeld = false
            releaseSilence()
        }
    }

    fun fadeIn(durationMs: Long = 500) {
        gate.fadeInBlock()?.let { block ->
            Logger.verbose(TAG) { "fadeIn skipped: $block" }
            return
        }

        if (slot == null && !overrideActive) {
            queue.current?.let { preparePlayer(it, autoStart = true) }
            return
        }

        val current = slot ?: return
        if (!current.prepared) {
            current.startWhenPrepared = true
            return
        }
        val player = current.player
        if (isPlayerPlaying(player) && !fadingOut) return

        fadeOutCancelled = true
        fadingOut = false
        fadeAnimator?.cancel()

        try {
            applyPlayerVolume(player, 0f)
            if (!player.isPlaying) {
                player.start()
            }
            setPlaying(true)

            fadeAnimator = ValueAnimator.ofFloat(0f, targetVolume).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    val vol = animator.animatedValue as Float
                    slot?.let { applyPlayerVolume(it.player, vol) }
                }
                start()
            }
            Log.d(TAG, "fadeIn started")
        } catch (e: Exception) {
            Log.e(TAG, "fadeIn failed", e)
        }
    }

    fun fadeOut(durationMs: Long = 500, onComplete: () -> Unit = {}) {
        val current = slot
        if (current == null || !current.prepared || !isPlayerPlaying(current.player)) {
            onComplete()
            return
        }

        fadeOutCancelled = false
        fadingOut = true
        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(targetVolume, 0f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                slot?.let { applyPlayerVolume(it.player, vol) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!fadeOutCancelled) {
                        fadingOut = false
                        pauseInternal()
                    }
                    onComplete()
                }
            })
            start()
        }
        Log.d(TAG, "fadeOut started")
    }

    private fun isPlayerPlaying(player: MediaPlayer): Boolean =
        runCatching { player.isPlaying }.getOrDefault(false)

    private fun pauseInternal() {
        val current = slot
        try {
            if (current != null && current.prepared) current.player.pause()
            setPlaying(false)
            Log.d(TAG, "paused")
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    private fun stopAndRelease() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        fadingOut = false
        overrideToken++
        overrideActive = false
        overrideTitle = null
        releaseStash()
        releaseEnhancer()

        val outgoing = slot
        slot = null
        try {
            if (outgoing?.prepared == true) outgoing.player.stop()
            outgoing?.player?.release()
        } catch (e: Exception) {
            Log.e(TAG, "stopAndRelease error", e)
        }
        setPlaying(false)
        Logger.verbose(TAG) { "stopped and released" }
    }

    private fun setPlaying(value: Boolean) {
        playing = value
        publish()
    }

    private fun publish() {
        val track = queue.current
        _playback.value = AmbientPlaybackState(
            trackTitle = track?.title,
            gameTitle = track?.gameTitle,
            coverPath = track?.coverPath,
            overrideTitle = if (overrideActive) overrideTitle else null,
            index = queue.index,
            count = queue.size,
            tracks = queue.order,
            isPlaying = playing,
            userPaused = gate.userPaused,
            shuffle = queue.shuffle,
            sourceId = activeSourceId,
            sourceLabel = activeSourceLabel
        )
        if (!overrideActive) _currentTrackName.value = track?.title
    }

    fun release() {
        generation++
        refreshJob?.cancel()
        refreshJob = null
        stopAndRelease()
        gate.enabled = false
        launcherSet = false
        launcherTracks = emptyList()
        launcherRefresh = null
        activeSourceId = MusicSelection.LAUNCHER_ID
        activeSourceLabel = null
        queue.clear()
        _currentTrackName.value = null
        publish()
    }
}
