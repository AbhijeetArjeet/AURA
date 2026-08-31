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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.regex.Pattern

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "ypdlp_download"
        const val NOTIF_ID = 1001
        const val ACTION_DOWNLOAD = "com.ypdlp.ACTION_DOWNLOAD"
        const val EXTRA_REQUEST = "download_request"
        private const val TAG = "DownloadService"

        fun getDownloadDirectory(context: Context): File {
            val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YPDlp")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            return appDir
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
        updateItem(item.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0, errorMessage = "") }
        updateNotification("Downloading: ${item.videoInfo.title}")

        val outDir = getDownloadDirectory(applicationContext)

        if (req.serverUrl.isNotBlank()) {
            runRemoteServerDownload(req, item, outDir)
        } else {
            runOnDeviceDownload(req, item, outDir)
        }
    }

    /**
     * 100% On-Device Standalone Download (Embedded yt-dlp + embedded FFmpeg)
     */
    private suspend fun runOnDeviceDownload(req: DownloadRequest, item: DownloadItem, outDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            updateItem(item.id) { it.copy(speed = "Starting on-device…", eta = "") }
            AppLogger.i("Download", "▶ Starting on-device download: ${req.url} [${req.quality} ${req.container}]")
            val isInit = YPDlpApp.ensureInitialized(applicationContext)
            if (!isInit) {
                AppLogger.e("Engine", "Engine initialization failed")
                throw IllegalStateException("Failed to initialize on-device YoutubeDL engine")
            }

            val isAudio = req.container.uppercase() in listOf("MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS")
            val qualityMap = mapOf(
                "Best"       to "bestvideo+bestaudio/best",
                "4K (2160p)" to "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best",
                "2K (1440p)" to "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best",
                "1080p"      to "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
                "720p"       to "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                "480p"       to "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
                "360p"       to "bestvideo[height<=360]+bestaudio/best[height<=360]/best",
            )

            val ytdlRequest = YoutubeDLRequest(req.url).apply {
                addOption("-o", "${outDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--newline")
                addOption("--no-mtime")
                addOption("--no-check-certificates")
                addOption("--ignore-errors")
                addOption("--no-warnings")

                if (isAudio) {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", req.container.lowercase())
                    addOption("--audio-quality", "0")
                } else {
                    val fmt = qualityMap[req.quality] ?: "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                    addOption("-f", fmt)
                    addOption("--merge-output-format", req.container.lowercase())
                }
            }

            val pctPattern = Pattern.compile("(\\d+\\.?\\d*)%")
            val speedPattern = Pattern.compile("(\\d+\\.?\\d*\\s*[KMG]iB/s)")
            val etaPattern = Pattern.compile("ETA\\s+([\\d:]+)")

            YoutubeDL.getInstance().execute(ytdlRequest, req.id) { progress, etaInSeconds, line ->
                AppLogger.d("yt-dlp", line)
                val mPct = pctPattern.matcher(line)
                val mSpeed = speedPattern.matcher(line)
                val mEta = etaPattern.matcher(line)

                val pct = if (mPct.find()) mPct.group(1)?.toFloatOrNull()?.toInt() ?: progress.toInt() else progress.toInt()
                val speed = if (mSpeed.find()) mSpeed.group(1) ?: "" else ""
                val eta = if (mEta.find()) mEta.group(1) ?: "" else if (etaInSeconds > 0) "${etaInSeconds}s" else ""

                if (line.contains("Destination:") || line.contains("Merging") || line.contains("ExtractAudio")) {
                    updateItem(item.id) { it.copy(status = DownloadStatus.POST_PROCESSING, speed = "Merging streams…") }
                } else {
                    updateItem(item.id) {
                        it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = pct.coerceIn(0, 100),
                            speed = speed,
                            eta = eta
                        )
                    }
                }
                updateNotification("Downloading: $pct% ($speed)")
            }

            // Scan directory for new file
            val latestFile = outDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
            if (latestFile != null) {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(latestFile.absolutePath),
                    null
                ) { _, _ -> }
            }

            AppLogger.i("Download", "✔ Download complete: ${latestFile?.name ?: "Finished"}")
            updateItem(item.id) {
                it.copy(
                    status = DownloadStatus.DONE,
                    progress = 100,
                    speed = "",
                    eta = "",
                    localFilePath = latestFile?.absolutePath
                )
            }
            updateNotification("Downloaded: ${latestFile?.name ?: "Complete"}")
            true
        } catch (e: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(req.id)
            AppLogger.w("Download", "Download cancelled: ${req.id}")
            updateItem(item.id) { it.copy(status = DownloadStatus.CANCELLED) }
            false
        } catch (e: Exception) {
            Log.e(TAG, "On-device download error: ${e.message}", e)
            AppLogger.e("Download", "✘ Download error: ${e.message}")
            updateItem(item.id) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
            }
            updateNotification("Download Error: ${e.message?.take(30)}")
            false
        } finally {
            if (activeJobs[req.id]?.isCompleted == true) {
                activeJobs.remove(req.id)
            }
            if (activeJobs.isEmpty()) {
                updateNotification("Downloads completed")
            }
        }
    }

    /**
     * Remote / Custom Server Download (if user enabled in settings)
     */
    private suspend fun runRemoteServerDownload(req: DownloadRequest, item: DownloadItem, outDir: File) {
        try {
            updateItem(item.id) { it.copy(speed = "Connecting server…", eta = "") }
            val jobId = ApiService.startDownload(
                videoUrl = req.url,
                container = req.container,
                quality = req.quality,
                customServerUrl = req.serverUrl
            )

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

                    if (filename.isNotBlank()) serverFilename = filename

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
                        }
                        "processing" -> {
                            updateItem(item.id) {
                                it.copy(
                                    status = DownloadStatus.POST_PROCESSING,
                                    progress = 95,
                                    speed = "Merging streams…",
                                    eta = ""
                                )
                            }
                        }
                        "done" -> isFinished = true
                        "error" -> {
                            val serverErr = statusJson.optString("error", "Server download failed")
                            throw IllegalStateException(serverErr)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException || e is IllegalStateException) throw e
                }
            }

            val cleanFilename = serverFilename.replace("[/\\\\?%*:|\"<>]".toRegex(), "_")
            val targetFile = File(outDir, cleanFilename)

            ApiService.downloadFile(jobId, targetFile, req.serverUrl) { pct, spd ->
                updateItem(item.id) { it.copy(progress = pct, speed = spd) }
            }

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
        } catch (e: CancellationException) {
            updateItem(item.id) { it.copy(status = DownloadStatus.CANCELLED) }
        } catch (e: Exception) {
            updateItem(item.id) {
                it.copy(status = DownloadStatus.ERROR, errorMessage = e.message ?: "Download failed")
            }
        } finally {
            activeJobs.remove(req.id)
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
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            // ignore
        }
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
