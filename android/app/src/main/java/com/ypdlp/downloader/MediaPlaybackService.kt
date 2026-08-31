package com.ypdlp.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ypdlp.downloader.aura.AuraPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "ypdlp_player_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_PLAY = "com.ypdlp.ACTION_PLAY"
        const val ACTION_PAUSE = "com.ypdlp.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.ypdlp.ACTION_TOGGLE"
        const val ACTION_STOP = "com.ypdlp.ACTION_STOP"
        const val ACTION_NEXT = "com.ypdlp.ACTION_NEXT"
        const val ACTION_PREV = "com.ypdlp.ACTION_PREV"
        const val EXTRA_FILE_PATH = "extra_file_path"

        private var activeQueue: List<DownloadedFile> = emptyList()

        fun setPlaybackQueue(queue: List<DownloadedFile>) {
            activeQueue = queue
        }

        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()

        fun updateCurrentMediaFile(file: DownloadedFile) {
            _playerState.update { it.copy(currentFile = file, isPlaying = true) }
        }
    }

    private val binder = LocalBinder()
    private var auraEngine: AuraPlaybackEngine? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
        fun getEngine(): AuraPlaybackEngine? = auraEngine
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        auraEngine = AuraPlaybackEngine(applicationContext)

        auraEngine?.onSongFinished = {
            playNext()
        }

        // Observe playback state & visualizer data
        scope.launch {
            auraEngine!!.isPlaying.collect { playing ->
                _playerState.update { it.copy(isPlaying = playing) }
                _playerState.value.currentFile?.let { file ->
                    updateNotification(file, playing)
                }
            }
        }
        scope.launch {
            auraEngine!!.currentPosition.collect { pos ->
                _playerState.update { it.copy(currentPositionMs = pos) }
            }
        }
        scope.launch {
            auraEngine!!.duration.collect { dur ->
                _playerState.update { it.copy(durationMs = dur) }
            }
        }
        scope.launch {
            auraEngine!!.visualizerBands.collect { bands ->
                _playerState.update { it.copy(visualizerData = bands) }
            }
        }
        scope.launch {
            auraEngine!!.sleepMinutesLeft.collect { mins ->
                _playerState.update { it.copy(sleepTimerMinutesLeft = mins) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val path = intent.getStringExtra(EXTRA_FILE_PATH)
                if (!path.isNullOrBlank()) {
                    playFile(path)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    fun playNext() {
        val cur = _playerState.value.currentFile ?: return
        if (activeQueue.isEmpty()) return
        val idx = activeQueue.indexOfFirst { it.path == cur.path }
        val next = if (idx in 0 until activeQueue.size - 1) activeQueue[idx + 1] else activeQueue.first()
        updateCurrentMediaFile(next)
        playFile(next.path)
    }

    fun playPrevious() {
        val cur = _playerState.value.currentFile ?: return
        if (activeQueue.isEmpty()) return
        val idx = activeQueue.indexOfFirst { it.path == cur.path }
        val prev = if (idx > 0) activeQueue[idx - 1] else activeQueue.last()
        updateCurrentMediaFile(prev)
        playFile(prev.path)
    }

    fun playFile(filePath: String) {
        val isUri = filePath.startsWith("content://")
        val file = File(filePath)
        if (!isUri && !file.exists()) return

        val existing = _playerState.value.currentFile
        val downloadedFile = if (existing != null && existing.path == filePath) {
            existing
        } else {
            val fileName = if (isUri) filePath.substringAfterLast('%').substringAfterLast('/') else file.name
            val cleanTitle = if (isUri) java.net.URLDecoder.decode(filePath.substringAfterLast('/'), "UTF-8").replace("_", " ") else file.nameWithoutExtension.replace("_", " ")
            DownloadedFile(
                file = file,
                name = fileName,
                title = cleanTitle,
                sizeBytes = if (isUri) 0L else file.length(),
                sizeFormatted = "",
                isVideo = filePath.lowercase().endsWith(".mp4") || filePath.lowercase().endsWith(".mkv"),
                path = filePath,
                lastModified = System.currentTimeMillis(),
                extension = filePath.substringAfterLast('.', "AUDIO").uppercase()
            )
        }

        _playerState.update {
            it.copy(
                currentFile = downloadedFile,
                isPlaying = true
            )
        }

        auraEngine?.playFile(downloadedFile)
        try {
            startForeground(NOTIFICATION_ID, buildNotification(downloadedFile, isPlaying = true))
        } catch (e: Exception) {
            // Foreground service start exception on modern Android without permission
        }
    }

    fun pause() {
        auraEngine?.pause()
    }

    fun resume() {
        auraEngine?.resume()
    }

    fun togglePlayPause() {
        auraEngine?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        auraEngine?.seekTo(positionMs)
    }

    fun stopPlayback() {
        auraEngine?.release()
        _playerState.update {
            it.copy(
                currentFile = null,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background video/audio playback"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(file: DownloadedFile, isPlaying: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val artBitmap: Bitmap? = file.artworkByteArray?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(file.title)
            .setContentText(if (file.artist.isNotBlank() && file.artist != "Unknown Artist") file.artist else "AURA Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                toggleIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)

        if (artBitmap != null) {
            builder.setLargeIcon(artBitmap)
        }

        return builder.build()
    }

    private fun updateNotification(file: DownloadedFile, isPlaying: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(file, isPlaying))
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
