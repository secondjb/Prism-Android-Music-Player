package com.secondjb.prism.core.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.secondjb.prism.MainActivity

/**
 * Global bridge coordinating Android System Media Notification / Lock Screen controls
 * when playback is routed to a companion desktop device (Spotify Connect style).
 */
object MediaSessionBridge {
    var isRemoteActive: Boolean = false
    var remoteServerName: String = "Prism Desktop"
    var onRemotePlay: (() -> Unit)? = null
    var onRemotePause: (() -> Unit)? = null
    var onRemoteSkipNext: (() -> Unit)? = null
    var onRemoteSkipPrevious: (() -> Unit)? = null
    var onRemoteSeek: ((Long) -> Unit)? = null
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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer = player

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
            override fun play() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemotePlay?.invoke()
                } else {
                    super.play()
                }
            }

            override fun pause() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemotePause?.invoke()
                } else {
                    super.pause()
                }
            }

            override fun seekToNext() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipNext?.invoke()
                } else {
                    super.seekToNext()
                }
            }

            override fun seekToNextMediaItem() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipNext?.invoke()
                } else {
                    super.seekToNextMediaItem()
                }
            }

            override fun seekToPrevious() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipPrevious?.invoke()
                } else {
                    super.seekToPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                if (MediaSessionBridge.isRemoteActive) {
                    MediaSessionBridge.onRemoteSkipPrevious?.invoke()
                } else {
                    super.seekToPreviousMediaItem()
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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .build()
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
