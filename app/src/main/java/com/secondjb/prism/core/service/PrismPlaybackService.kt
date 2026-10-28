package com.secondjb.prism.core.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.secondjb.prism.MainActivity
import com.secondjb.prism.R

/**
 * Global bridge coordinating Android System Media Notification / Lock Screen controls
 * when playback is routed to a companion desktop device (Spotify Connect style).
 */
object MediaSessionBridge {
    var isRemoteActive: Boolean = false
    var remoteServerName: String = "Prism Desktop"
    var isRemotePlaying: Boolean = false
    var remotePositionMs: Long = 0L
    var remoteDurationMs: Long = 0L

    var onPlayPause: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null
    var onRemotePlay: (() -> Unit)? = null
    var onRemotePause: (() -> Unit)? = null
    var onRemoteSkipNext: (() -> Unit)? = null
    var onRemoteSkipPrevious: (() -> Unit)? = null
    var onRemoteSeek: ((Long) -> Unit)? = null
    var onLocalSkipNext: (() -> Unit)? = null
    var onLocalSkipPrevious: (() -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null
    var suppressRemoteCallback: Boolean = false
}

/**
 * Foreground MediaSessionService hosting the local ExoPlayer instance.
 * Automatically integrates with Android system media controls, lockscreen widgets,
 * and audio focus/headset disconnection listeners.
 * Seamlessly routes hardware media buttons to desktop companion when remote output is active.
 */
class PrismPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Do not let default ExoPlayer audio focus drop/duck volume on screen turn on / lock sounds
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer = player
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    player.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    player.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // DO NOT DUCK: Maintain 100% volume across screen wake, unlock, and key click sounds
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (player.playWhenReady) {
                        player.play()
                    }
                }
            }
        }
        focusListener = listener

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(listener)
                .build()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        focusRequest?.let { audioManager?.requestAudioFocus(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager?.requestAudioFocus(
                            listener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                    }
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> return true
                }
                return super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun isPlaying(): Boolean {
                return if (MediaSessionBridge.isRemoteActive) MediaSessionBridge.isRemotePlaying else super.isPlaying()
            }

            override fun getPlayWhenReady(): Boolean {
                return if (MediaSessionBridge.isRemoteActive) MediaSessionBridge.isRemotePlaying else super.getPlayWhenReady()
            }

            override fun getPlaybackState(): Int {
                return if (MediaSessionBridge.isRemoteActive) Player.STATE_READY else super.getPlaybackState()
            }

            override fun getCurrentPosition(): Long {
                return if (MediaSessionBridge.isRemoteActive) MediaSessionBridge.remotePositionMs else super.getCurrentPosition()
            }

            override fun getDuration(): Long {
                return if (MediaSessionBridge.isRemoteActive && MediaSessionBridge.remoteDurationMs > 0L) {
                    MediaSessionBridge.remoteDurationMs
                } else {
                    super.getDuration()
                }
            }

            override fun prepare() {
                if (MediaSessionBridge.isRemoteActive) {
                    player.pause()
                } else {
                    super.prepare()
                }
            }

            override fun play() {
                if (MediaSessionBridge.isRemoteActive) {
                    player.pause()
                } else {
                    super.play()
                }
            }

            override fun pause() {
                super.pause()
            }

            override fun seekToNext() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipNext?.invoke()
                } else {
                    MediaSessionBridge.onLocalSkipNext?.invoke() ?: super.seekToNext()
                }
            }

            override fun seekToNextMediaItem() {
                seekToNext()
            }

            override fun seekToPrevious() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipPrevious?.invoke()
                } else {
                    MediaSessionBridge.onLocalSkipPrevious?.invoke() ?: super.seekToPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }

            override fun seekTo(positionMs: Long) {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSeek?.invoke(positionMs)
                } else {
                    super.seekTo(positionMs)
                }
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSeek?.invoke(positionMs)
                } else {
                    super.seekTo(mediaItemIndex, positionMs)
                }
            }
        }

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                val desktopCommand = SessionCommand("OUTPUT_ROUTED_TO_DESKTOP", Bundle.EMPTY)
                sessionCommands.add(desktopCommand)
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands.build())
                    .build()
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                when (playerCommand) {
                    Player.COMMAND_PLAY_PAUSE -> {
                        MediaSessionBridge.onPlayPause?.invoke()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        MediaSessionBridge.onSkipNext?.invoke()
                            ?: if (MediaSessionBridge.isRemoteActive) {
                                MediaSessionBridge.onRemoteSkipNext?.invoke()
                            } else {
                                MediaSessionBridge.onLocalSkipNext?.invoke()
                            }
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        MediaSessionBridge.onSkipPrevious?.invoke()
                            ?: if (MediaSessionBridge.isRemoteActive) {
                                MediaSessionBridge.onRemoteSkipPrevious?.invoke()
                            } else {
                                MediaSessionBridge.onLocalSkipPrevious?.invoke()
                            }
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            MediaSessionBridge.onPlayPause?.invoke()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            MediaSessionBridge.onSkipNext?.invoke()
                                ?: if (MediaSessionBridge.isRemoteActive) {
                                    MediaSessionBridge.onRemoteSkipNext?.invoke()
                                } else {
                                    MediaSessionBridge.onLocalSkipNext?.invoke()
                                }
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            MediaSessionBridge.onSkipPrevious?.invoke()
                                ?: if (MediaSessionBridge.isRemoteActive) {
                                    MediaSessionBridge.onRemoteSkipPrevious?.invoke()
                                } else {
                                    MediaSessionBridge.onLocalSkipPrevious?.invoke()
                                }
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val session = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .build()

        mediaSession = session

        MediaSessionBridge.onStateChanged = {
            if (MediaSessionBridge.isRemoteActive) {
                player.pause()
                val desktopCmd = SessionCommand("OUTPUT_ROUTED_TO_DESKTOP", Bundle.EMPTY)
                val desktopBtn = CommandButton.Builder()
                    .setDisplayName(MediaSessionBridge.remoteServerName)
                    .setIconResId(R.drawable.ic_computer)
                    .setSessionCommand(desktopCmd)
                    .setEnabled(true)
                    .build()
                session.setCustomLayout(listOf(desktopBtn))
            } else {
                session.setCustomLayout(emptyList())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        MediaSessionBridge.onStateChanged = null
        MediaSessionBridge.onLocalSkipNext = null
        MediaSessionBridge.onLocalSkipPrevious = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            focusListener?.let {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(it)
            }
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
