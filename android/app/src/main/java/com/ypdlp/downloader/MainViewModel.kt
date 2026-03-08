package com.ypdlp.downloader

import android.app.Application
import android.content.*
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val urlText: String = "",
    val videoInfo: VideoInfo? = null,
    val isLoadingInfo: Boolean = false,
    val infoError: String? = null,
    val selectedType: DownloadType = DownloadType.VIDEO,
    val selectedQuality: String = "1080p",
    val selectedContainer: String = "MP4",
    val outputDir: String = "",
)

enum class DownloadType { VIDEO, AUDIO }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("ypdlp_prefs", Context.MODE_PRIVATE)

    private val _ui   = MutableStateFlow(UiState(outputDir = defaultOutputDir()))
    val ui            = _ui.asStateFlow()

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue          = _queue.asStateFlow()

    // Service binding
    private var service: DownloadService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as DownloadService.LocalBinder).getService()
            viewModelScope.launch {
                service!!.items.collect { _queue.value = it }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }

    init {
        Intent(app, DownloadService::class.java).also { intent ->
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    // ── URL / Info ────────────────────────────────────────────────────────────

    fun onUrlChange(v: String) = _ui.update { it.copy(urlText = v, infoError = null) }

    fun fetchInfo() {
        val url = _ui.value.urlText.trim()
        if (url.isBlank()) return
        _ui.update { it.copy(isLoadingInfo = true, videoInfo = null, infoError = null) }

        viewModelScope.launch {
            try {
                val info = InfoFetcher.fetch(url)
                _ui.update { it.copy(videoInfo = info, isLoadingInfo = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoadingInfo = false, infoError = e.message) }
            }
        }
    }

    // ── Format selectors ─────────────────────────────────────────────────────

    fun setType(t: DownloadType) {
        val container = if (t == DownloadType.VIDEO) "MP4" else "MP3"
        _ui.update { it.copy(selectedType = t, selectedContainer = container) }
    }

    fun setQuality(q: String)   = _ui.update { it.copy(selectedQuality = q) }
    fun setContainer(c: String) = _ui.update { it.copy(selectedContainer = c) }

    // ── Queue ─────────────────────────────────────────────────────────────────

    fun addToQueue() {
        val info = _ui.value.videoInfo ?: return
        val req  = DownloadRequest(
            id          = UUID.randomUUID().toString(),
            url         = info.url,
            container   = _ui.value.selectedContainer,
            quality     = _ui.value.selectedQuality,
            outputDir   = _ui.value.outputDir,
            ffmpegPath  = prefs.getString("ffmpeg_path", "") ?: ""
        )
        service?.enqueue(req)
    }

    fun cancelItem(id: String) = service?.cancelItem(id)
    fun clearDone()            = service?.clearDone()

    private fun defaultOutputDir(): String {
        return prefs.getString("output_dir", "") ?: ""
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(connection)
        super.onCleared()
    }
}

// ─── Info fetcher (runs yt-dlp --dump-json via shell) ─────────────────────────

object InfoFetcher {
    suspend fun fetch(url: String): VideoInfo {
        val proc = ProcessBuilder("yt-dlp", "--dump-single-json", "--no-playlist", url)
            .redirectErrorStream(true)
            .start()
        val json = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // Parse minimal JSON without a library
        fun field(key: String): String? {
            val regex = Regex(""""$key"\s*:\s*"([^"]*?)"""")
            return regex.find(json)?.groupValues?.get(1)
        }
        fun longField(key: String): Long {
            val regex = Regex(""""$key"\s*:\s*(\d+)""")
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        }
        return VideoInfo(
            url              = url,
            title            = field("title") ?: "Unknown",
            channel          = field("uploader") ?: field("channel") ?: "",
            durationSeconds  = longField("duration"),
            thumbnailUrl     = field("thumbnail") ?: "",
            viewCount        = longField("view_count"),
        )
    }
}
