package com.ypdlp.downloader

import android.app.Application
import android.content.*
import android.media.MediaScannerConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class UiState(
    val urlText: String = "",
    val videoInfo: VideoInfo? = null,
    val playlistInfo: PlaylistInfo? = null,
    val isPlaylistMode: Boolean = false,
    val isLoadingInfo: Boolean = false,
    val infoError: String? = null,
    val selectedType: DownloadType = DownloadType.VIDEO,
    val selectedQuality: String = "1080p",
    val selectedContainer: String = "MP4",
    val serverUrl: String = "", // Empty means 100% On-Device Standalone Engine
    val downloadedFiles: List<DownloadedFile> = emptyList(),
    val isScanningFiles: Boolean = false,
)

enum class DownloadType { VIDEO, AUDIO }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("ypdlp_prefs", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        UiState(
            serverUrl = prefs.getString("server_url", "") ?: ""
        )
    )
    val ui = _ui.asStateFlow()

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue = _queue.asStateFlow()

    val playerState = MediaPlaybackService.playerState

    private var downloadService: DownloadService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            downloadService = (binder as DownloadService.LocalBinder).getService()
            viewModelScope.launch {
                downloadService!!.items.collect { items ->
                    _queue.value = items
                    if (items.any { it.status == DownloadStatus.DONE }) {
                        loadDownloadedFiles()
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { downloadService = null }
    }

    init {
        Intent(app, DownloadService::class.java).also { intent ->
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        loadDownloadedFiles()
    }

    // ── URL & Info Fetching ───────────────────────────────────────────────────

    fun onUrlChange(v: String) {
        val trimmed = v.trim()
        val isPlaylist = trimmed.contains("list=") || trimmed.contains("/playlist")
        _ui.update {
            it.copy(
                urlText = v,
                isPlaylistMode = isPlaylist,
                infoError = null
            )
        }
    }

    fun setPlaylistMode(isPlaylist: Boolean) {
        _ui.update { it.copy(isPlaylistMode = isPlaylist) }
    }

    fun fetchInfo() {
        val url = _ui.value.urlText.trim()
        if (url.isBlank()) return

        _ui.update {
            it.copy(
                isLoadingInfo = true,
                videoInfo = null,
                playlistInfo = null,
                infoError = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If on-device standalone engine is ready and no custom server is specified:
                val isReady = YPDlpApp.ensureInitialized(getApplication())
                if (isReady && _ui.value.serverUrl.isBlank()) {
                    val req = YoutubeDLRequest(url).apply {
                        addOption("--no-check-certificates")
                        addOption("--extractor-args", "youtube:player_client=android,ios")
                        addOption("--add-header", "User-Agent:com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
                    }
                    val ytdlInfo = YoutubeDL.getInstance().getInfo(req)

                    val durationSecs = (ytdlInfo.duration?.toLong()) ?: 0L
                    val viewCountLong = (ytdlInfo.viewCount?.toLong()) ?: 0L
                    val videoInfo = VideoInfo(
                        url = ytdlInfo.url ?: url,
                        title = ytdlInfo.title ?: "Unknown Title",
                        channel = ytdlInfo.uploader ?: "",
                        durationSeconds = durationSecs,
                        thumbnailUrl = ytdlInfo.thumbnail ?: "",
                        viewCount = viewCountLong
                    )

                    _ui.update {
                        it.copy(
                            videoInfo = videoInfo,
                            isPlaylistMode = false,
                            isLoadingInfo = false
                        )
                    }
                } else {
                    // Use backend server (local PC or remote)
                    val server = _ui.value.serverUrl.ifBlank { ApiService.DEFAULT_SERVER_URL }
                    if (_ui.value.isPlaylistMode || url.contains("list=") || url.contains("/playlist")) {
                        val playlist = ApiService.fetchPlaylistInfo(url, server)
                        _ui.update {
                            it.copy(
                                playlistInfo = playlist,
                                isPlaylistMode = true,
                                isLoadingInfo = false
                            )
                        }
                    } else {
                        val video = ApiService.fetchVideoInfo(url, server)
                        _ui.update {
                            it.copy(
                                videoInfo = video,
                                isPlaylistMode = false,
                                isLoadingInfo = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback attempt via API service if on-device threw an error
                try {
                    val server = _ui.value.serverUrl.ifBlank { ApiService.DEFAULT_SERVER_URL }
                    val video = ApiService.fetchVideoInfo(url, server)
                    _ui.update {
                        it.copy(
                            videoInfo = video,
                            isPlaylistMode = false,
                            isLoadingInfo = false
                        )
                    }
                } catch (fallbackEx: Exception) {
                    _ui.update {
                        it.copy(
                            isLoadingInfo = false,
                            infoError = e.message ?: fallbackEx.message ?: "Failed to fetch video details"
                        )
                    }
                }
            }
        }
    }

    // ── Selectors ─────────────────────────────────────────────────────────────

    fun setType(t: DownloadType) {
        val container = if (t == DownloadType.VIDEO) "MP4" else "MP3"
        _ui.update { it.copy(selectedType = t, selectedContainer = container) }
    }

    fun setQuality(q: String)   = _ui.update { it.copy(selectedQuality = q) }
    fun setContainer(c: String) = _ui.update { it.copy(selectedContainer = c) }

    fun setServerUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString("server_url", clean).apply()
        _ui.update { it.copy(serverUrl = clean) }
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    fun addToQueue() {
        val info = _ui.value.videoInfo ?: return
        val req = DownloadRequest(
            id = UUID.randomUUID().toString(),
            url = info.url,
            container = _ui.value.selectedContainer,
            quality = _ui.value.selectedQuality,
            outputDir = DownloadService.getDownloadDirectory(getApplication()).absolutePath,
            serverUrl = _ui.value.serverUrl
        )
        downloadService?.enqueue(req, videoTitle = info.title, thumbnailUrl = info.thumbnailUrl)
    }

    fun addPlaylistToQueue() {
        val playlist = _ui.value.playlistInfo ?: return
        val outDir = DownloadService.getDownloadDirectory(getApplication()).absolutePath

        playlist.items.forEach { item ->
            val req = DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = item.url,
                container = _ui.value.selectedContainer,
                quality = _ui.value.selectedQuality,
                outputDir = outDir,
                serverUrl = _ui.value.serverUrl
            )
            downloadService?.enqueue(req, videoTitle = item.title, thumbnailUrl = item.thumbnailUrl)
        }
    }

    fun cancelItem(id: String) = downloadService?.cancelItem(id)
    fun clearDone()            = downloadService?.clearDone()

    // ── Library ───────────────────────────────────────────────────────────────

    fun loadDownloadedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isScanningFiles = true) }
            val downloadDir = DownloadService.getDownloadDirectory(getApplication())
            val validExts = setOf("mp4", "mkv", "webm", "avi", "mp3", "m4a", "flac", "wav", "ogg", "opus")

            val filesList = mutableListOf<DownloadedFile>()
            if (downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in validExts }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        val isVid = file.extension.lowercase() in setOf("mp4", "mkv", "webm", "avi")
                        val sizeMb = file.length() / (1024.0 * 1024.0)
                        val sizeFormatted = if (sizeMb >= 1024) "%.2f GB".format(sizeMb / 1024.0) else "%.1f MB".format(sizeMb)

                        filesList.add(
                            DownloadedFile(
                                file = file,
                                name = file.name,
                                title = file.nameWithoutExtension,
                                sizeBytes = file.length(),
                                sizeFormatted = sizeFormatted,
                                isVideo = isVid,
                                path = file.absolutePath,
                                lastModified = file.lastModified(),
                                extension = file.extension.uppercase()
                            )
                        )
                    }
            }
            _ui.update { it.copy(downloadedFiles = filesList, isScanningFiles = false) }
        }
    }

    fun deleteDownloadedFile(file: DownloadedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.file.exists()) {
                    file.file.delete()
                    MediaScannerConnection.scanFile(
                        getApplication(),
                        arrayOf(file.path),
                        null
                    ) { _, _ -> }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadDownloadedFiles()
        }
    }

    fun playMediaFile(file: DownloadedFile) {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_PLAY
            putExtra(MediaPlaybackService.EXTRA_FILE_PATH, file.path)
        }
        context.startService(intent)
    }

    fun togglePlayback() {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_TOGGLE
        }
        context.startService(intent)
    }

    fun stopPlayback() {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(connection)
        } catch (e: Exception) {
            // Ignored
        }
        super.onCleared()
    }
}
