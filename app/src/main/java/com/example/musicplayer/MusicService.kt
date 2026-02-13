package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val binder = MusicBinder()

    private var currentMusicFile: MusicFile? = null
    private var currentIndex: Int? = null
    private var playlist: List<MusicFile> = emptyList()
    private var isPlaying = false

    // Callbacks to notify UI of state changes
    private val stateListeners = mutableListOf<(Int?, Boolean) -> Unit>()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback"
        const val ACTION_PLAY = "com.example.musicplayer.PLAY"
        const val ACTION_PAUSE = "com.example.musicplayer.PAUSE"
        const val ACTION_STOP = "com.example.musicplayer.STOP"
        const val ACTION_NEXT = "com.example.musicplayer.NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayer.PREVIOUS"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> {
                if (!isPlaying && currentMusicFile != null) {
                    mediaPlayer?.start()
                    isPlaying = true
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    updateNotification()
                    notifyListeners()
                }
            }
            ACTION_PAUSE -> {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification()
                    notifyListeners()
                }
            }
            ACTION_STOP -> {
                stopPlayback()
            }
            ACTION_NEXT -> {
                playNext()
            }
            ACTION_PREVIOUS -> {
                playPrevious()
            }
        }

        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startService(Intent(this@MusicService, MusicService::class.java).apply {
                        action = ACTION_PLAY
                    })
                }

                override fun onPause() {
                    startService(Intent(this@MusicService, MusicService::class.java).apply {
                        action = ACTION_PAUSE
                    })
                }

                override fun onStop() {
                    startService(Intent(this@MusicService, MusicService::class.java).apply {
                        action = ACTION_STOP
                    })
                }

                override fun onSkipToNext() {
                    startService(Intent(this@MusicService, MusicService::class.java).apply {
                        action = ACTION_NEXT
                    })
                }

                override fun onSkipToPrevious() {
                    startService(Intent(this@MusicService, MusicService::class.java).apply {
                        action = ACTION_PREVIOUS
                    })
                }
            })

            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing music"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun playMusic(musicFile: MusicFile, index: Int, fullPlaylist: List<MusicFile>) {
        // Stop current playback
        mediaPlayer?.stop()
        mediaPlayer?.release()

        // Store playlist info
        playlist = fullPlaylist
        currentIndex = index

        try {
            val afd = assets.openFd(musicFile.path)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
                setOnCompletionListener {
                    // Auto-advance to next song
                    playNext()
                }
            }

            currentMusicFile = musicFile
            isPlaying = true

            // Update media session metadata
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, musicFile.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Unknown Artist")
                    .build()
            )

            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)

            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification())
            notifyListeners()

        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentMusicFile = null
        currentIndex = null
        isPlaying = false

        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        notifyListeners()
    }

    fun playNext() {
        val index = currentIndex
        if (index != null && playlist.isNotEmpty()) {
            val nextIndex = (index + 1) % playlist.size
            playMusic(playlist[nextIndex], nextIndex, playlist)
        }
    }

    fun playPrevious() {
        val index = currentIndex
        if (index != null && playlist.isNotEmpty()) {
            val previousIndex = if (index - 1 < 0) playlist.size - 1 else index - 1
            playMusic(playlist[previousIndex], previousIndex, playlist)
        }
    }

    fun getCurrentIndex(): Int? = currentIndex

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun registerStateListener(listener: (Int?, Boolean) -> Unit) {
        stateListeners.add(listener)
    }

    fun unregisterStateListener(listener: (Int?, Boolean) -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyListeners() {
        stateListeners.forEach { it(currentIndex, isPlaying) }
    }

    private fun updatePlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, 0, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicService::class.java).apply {
                action = ACTION_PREVIOUS
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicService::class.java).apply {
                action = ACTION_NEXT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMusicFile?.name ?: "Music Player")
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
    }
}