package com.ypdlp.downloader

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Foreground service that runs yt-dlp via a bundled Python environment or
 * via an adb subprocess. On real devices, yt-dlp is invoked through the
 * embedded Python interpreter shipped with the APK via Chaquopy.
 *
 * ── For the initial release the service communicates with a local PC server
 *    (same Wi-Fi) that wraps yt-dlp. The UI lets the user set the server URL
 *    in Settings. This is the easiest portable architecture that mirrors the
 *    desktop app.
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID   = "ypdlp_download"
        const val NOTIF_ID     = 1001
        const val ACTION_DOWNLOAD = "com.ypdlp.ACTION_DOWNLOAD"
        const val EXTRA_REQUEST   = "download_request"
    }

    private val binder  = LocalBinder()
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Exposed to ViewModel via binding
    private val _items  = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items           = _items.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService() = this@DownloadService
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

    // ─── Queue ───────────────────────────────────────────────────────────────

    fun enqueue(req: DownloadRequest) {
        val item = DownloadItem(
            id          = req.id,
            videoInfo   = VideoInfo(url = req.url),
            container   = req.container,
            quality     = req.quality,
        )
        _items.value = _items.value + item
        scope.launch { runDownload(req, item) }
    }

    private suspend fun runDownload(req: DownloadRequest, item: DownloadItem) {
        updateItem(item.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
        updateNotification("Downloading: ${req.url.takeLast(40)}")

        try {
            val args = buildYtdlpArgs(req)
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { parseLine(it, item.id) }
            }
            val code = process.waitFor()
            if (code == 0) {
                updateItem(item.id) { it.copy(status = DownloadStatus.DONE, progress = 100) }
            } else {
                updateItem(item.id) {
                    it.copy(status = DownloadStatus.ERROR, errorMessage = "Exit code $code")
                }
            }
        } catch (e: Exception) {
            updateItem(item.id) {
                it.copy(status = DownloadStatus.ERROR, errorMessage = e.message ?: "Unknown error")
            }
        }
        updateNotification("Idle")
    }

    // ─── yt-dlp argument builder ─────────────────────────────────────────────

    private fun buildYtdlpArgs(req: DownloadRequest): List<String> {
        val audioFormats = setOf("MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS")
        val qualityMap = mapOf(
            "Best"       to "bestvideo+bestaudio/best",
            "4K (2160p)" to "bestvideo[height<=2160]+bestaudio/best[height<=2160]",
            "2K (1440p)" to "bestvideo[height<=1440]+bestaudio/best[height<=1440]",
            "1080p"      to "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "720p"       to "bestvideo[height<=720]+bestaudio/best[height<=720]",
            "480p"       to "bestvideo[height<=480]+bestaudio/best[height<=480]",
            "360p"       to "bestvideo[height<=360]+bestaudio/best[height<=360]",
        )
        val isAudio = req.container.uppercase() in audioFormats
        val out = "${req.outputDir}/%(title)s.%(ext)s"

        return buildList {
            add("yt-dlp")
            add("--newline")
            add("-o"); add(out)
            if (isAudio) {
                add("-f"); add("bestaudio/best")
                add("-x"); add("--audio-format"); add(req.container.lowercase())
                add("--audio-quality"); add("0")
            } else {
                val fmt = qualityMap[req.quality] ?: qualityMap["Best"]!!
                add("-f"); add(fmt)
                add("--merge-output-format"); add(req.container.lowercase())
            }
            if (req.ffmpegPath.isNotBlank()) {
                add("--ffmpeg-location"); add(req.ffmpegPath)
            }
            add(req.url)
        }
    }

    // ─── Progress parser ─────────────────────────────────────────────────────

    private val pctRegex   = Regex("""(\d+\.?\d*)%""")
    private val speedRegex = Regex("""(\d+\.?\d*\s*[KMG]iB/s)""")
    private val etaRegex   = Regex("""ETA\s+([\d:]+)""")

    private fun parseLine(line: String, id: String) {
        if (line.contains("[download]")) {
            val pct   = pctRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: return
            val speed = speedRegex.find(line)?.groupValues?.get(1) ?: ""
            val eta   = etaRegex.find(line)?.groupValues?.get(1) ?: ""
            updateItem(id) { it.copy(progress = pct, speed = speed, eta = eta) }
        } else if (line.contains("Destination:") || line.contains("Merging")) {
            updateItem(id) { it.copy(status = DownloadStatus.POST_PROCESSING) }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    fun cancelItem(id: String) {
        updateItem(id) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    fun clearDone() {
        _items.value = _items.value.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Downloads",
                NotificationManager.IMPORTANCE_LOW)
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
