package com.ypdlp.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "ypdlp_download"
        const val NOTIF_ID = 1001
        const val ACTION_DOWNLOAD = "com.ypdlp.ACTION_DOWNLOAD"
        const val EXTRA_REQUEST = "download_request"

        fun getDownloadDirectory(context: Context): File {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(publicDir, "YPDlp")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            return if (appDir.exists() && appDir.canWrite()) appDir else File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YPDlp").apply { mkdirs() }
        }
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active & Queued downloads
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items = _items.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Ready to download"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DOWNLOAD) {
            val req = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_REQUEST, DownloadRequest::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_REQUEST)
            }
            req?.let { enqueue(it) }
        }
        return START_NOT_STICKY
    }

    fun enqueue(req: DownloadRequest, videoTitle: String = "", thumbnailUrl: String = "") {
        val item = DownloadItem(
            id = req.id,
            videoInfo = VideoInfo(
                url = req.url,
                title = videoTitle.ifBlank { req.url.takeLast(40) },
                thumbnailUrl = thumbnailUrl
            ),
            container = req.container,
            quality = req.quality,
            status = DownloadStatus.QUEUED
        )

        _items.update { it + item }

        val job = scope.launch {
            runDownload(req, item)
        }
        activeJobs[req.id] = job
    }

    private suspend fun runDownload(req: DownloadRequest, item: DownloadItem) {
        updateItem(item.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0) }
        updateNotification("Downloading: ${item.videoInfo.title}")

        val outDir = getDownloadDirectory(applicationContext)

        try {
            // 1. Initiate download job on backend server
            updateItem(item.id) { it.copy(speed = "Starting…", eta = "") }
            val jobId = ApiService.startDownload(
                videoUrl = req.url,
                container = req.container,
                quality = req.quality,
                customServerUrl = req.serverUrl
            )

            // 2. Poll progress from server
            var isFinished = false
            var serverFilename = "download_${req.id}.${req.container.lowercase()}"

            while (!isFinished && currentCoroutineContext().isActive) {
                delay(1200)
                try {
                    val statusJson = ApiService.getStatus(jobId, req.serverUrl)
                    val status = statusJson.optString("status", "")
                    val progress = statusJson.optInt("progress", 0)
                    val speed = statusJson.optString("speed", "")
                    val eta = statusJson.optString("eta", "")
                    val title = statusJson.optString("title", "")
                    val filename = statusJson.optString("filename", "")

                    if (filename.isNotBlank()) {
                        serverFilename = filename
                    }

                    if (title.isNotBlank() && item.videoInfo.title.isBlank()) {
                        updateItem(item.id) {
                            it.copy(videoInfo = it.videoInfo.copy(title = title))
                        }
                    }

                    when (status) {
                        "downloading" -> {
                            updateItem(item.id) {
                                it.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress,
                                    speed = speed,
                                    eta = eta
                                )
                            }
                            updateNotification("Downloading: $progress% ($speed)")
                        }
                        "processing" -> {
                            updateItem(item.id) {
                                it.copy(
                                    status = DownloadStatus.POST_PROCESSING,
                                    progress = 95,
                                    speed = "Merging audio/video…",
                                    eta = ""
                                )
                            }
                            updateNotification("Processing high quality merge…")
                        }
                        "done" -> {
                            isFinished = true
                        }
                        "error" -> {
                            val err = statusJson.optString("error", "Server download failed")
                            throw Exception(err)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Continue polling unless error state
                }
            }

            // 3. Download the merged file directly to local storage
            val cleanFilename = serverFilename.replace("[/\\\\?%*:|\"<>]".toRegex(), "_")
            val targetFile = File(outDir, cleanFilename)

            updateItem(item.id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = 98,
                    speed = "Saving to phone…",
                    eta = ""
                )
            }

            ApiService.downloadFile(jobId, targetFile, req.serverUrl) { pct, spd ->
                updateItem(item.id) {
                    it.copy(progress = pct, speed = spd)
                }
            }

            // Scan file with MediaStore so it appears in Gallery/Videos/Music
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(targetFile.absolutePath),
                null
            ) { _, _ -> }

            updateItem(item.id) {
                it.copy(
                    status = DownloadStatus.DONE,
                    progress = 100,
                    speed = "",
                    eta = "",
                    localFilePath = targetFile.absolutePath
                )
            }
            updateNotification("Downloaded: ${targetFile.name}")

        } catch (e: CancellationException) {
            updateItem(item.id) { it.copy(status = DownloadStatus.CANCELLED) }
        } catch (e: Exception) {
            updateItem(item.id) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
            }
            updateNotification("Download Error: ${e.message?.take(30)}")
        } finally {
            activeJobs.remove(req.id)
            if (activeJobs.isEmpty()) {
                updateNotification("Downloads completed")
            }
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    fun cancelItem(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        updateItem(id) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    fun clearDone() {
        _items.update { list ->
            list.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YPDlp Downloader")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
