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
    val albums: List<AuraAlbum> = emptyList(),
    val artists: List<AuraArtist> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    val activePlaylist: SmartPlaylist? = null,
    val statistics: ListeningStatistics = ListeningStatistics(),
    val aiDjCommentary: AiDjCommentary? = null,
    val searchQuery: String = "",
    val isScanningFiles: Boolean = false,
    val isHachimanMode: Boolean = false,
    val isOtakuMode: Boolean = false,
    val terminalInput: String = "",
    val isRunningCommand: Boolean = false,
    val autoMixSession: AutoMixSession = AutoMixSession(),
    val visualizerMode: VisualizerMode = VisualizerMode.SPECTRUM,
    val isVideoPlayerFullscreen: Boolean = false,
    val activeVideoFile: DownloadedFile? = null,
    val customMusicFolders: Set<String> = emptySet()
)

enum class DownloadType { VIDEO, AUDIO }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("ypdlp_prefs", Context.MODE_PRIVATE)

    private fun getSavedCustomFolders(): Set<String> {
        val raw = prefs.getString("aura_custom_folders_str", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split("|||").filter { it.isNotBlank() }.toSet()
    }

    private val _ui = MutableStateFlow(
        UiState(
            serverUrl = prefs.getString("server_url", "") ?: "",
            isHachimanMode = prefs.getBoolean("hachiman_mode", false),
            isOtakuMode = prefs.getBoolean("otaku_mode", false),
            favorites = prefs.getStringSet("aura_favorites", emptySet()) ?: emptySet(),
            customMusicFolders = getSavedCustomFolders()
        )
    )
    val ui = _ui.asStateFlow()

    val consoleLogs = AppLogger.logs

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
                val isReady = YPDlpApp.ensureInitialized(getApplication())
                if (isReady && _ui.value.serverUrl.isBlank()) {
                    val isPlaylistUrl = url.contains("list=") || url.contains("/playlist")
                    
                    if (isPlaylistUrl) {
                        // Pass playlist URL directly to on-device single-item query with flat playlist
                        val req = YoutubeDLRequest(url).apply {
                            addOption("--no-check-certificates")
                            addOption("--ignore-errors")
                            addOption("--no-warnings")
                            addOption("--flat-playlist")
                        }
                        val ytdlInfo = YoutubeDL.getInstance().getInfo(req)

                        val videoInfo = VideoInfo(
                            url = ytdlInfo.webpageUrl ?: url,
                            title = ytdlInfo.title ?: "YouTube Playlist",
                            channel = ytdlInfo.uploader ?: "",
                            durationSeconds = (ytdlInfo.duration?.toLong()) ?: 0L,
                            thumbnailUrl = ytdlInfo.thumbnail ?: "",
                            viewCount = (ytdlInfo.viewCount?.toLong()) ?: 0L
                        )

                        val playlist = PlaylistInfo(
                            title = ytdlInfo.title ?: "YouTube Playlist",
                            author = ytdlInfo.uploader ?: "",
                            itemCount = 1,
                            items = listOf(videoInfo),
                            url = url
                        )

                        _ui.update {
                            it.copy(
                                playlistInfo = playlist,
                                isPlaylistMode = true,
                                isLoadingInfo = false
                            )
                        }
                    } else {
                        val req = YoutubeDLRequest(url).apply {
                            addOption("--no-check-certificates")
                            addOption("--ignore-errors")
                            addOption("--no-warnings")
                        }
                        val ytdlInfo = YoutubeDL.getInstance().getInfo(req)

                        val durationSecs = (ytdlInfo.duration?.toLong()) ?: 0L
                        val viewCountLong = (ytdlInfo.viewCount?.toLong()) ?: 0L
                        val videoInfo = VideoInfo(
                            url = ytdlInfo.webpageUrl ?: url,
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
        val baseDir = DownloadService.getDownloadDirectory(getApplication())
        val cleanPlaylistName = playlist.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val playlistDir = File(baseDir, cleanPlaylistName)
        if (!playlistDir.exists()) playlistDir.mkdirs()

        if (playlist.items.size == 1 && playlist.items[0].url == playlist.url) {
            // Enqueue the whole playlist URL directly to yt-dlp to download all tracks automatically
            val req = DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = playlist.url,
                container = _ui.value.selectedContainer,
                quality = _ui.value.selectedQuality,
                outputDir = playlistDir.absolutePath,
                serverUrl = _ui.value.serverUrl
            )
            downloadService?.enqueue(req, videoTitle = playlist.title, thumbnailUrl = playlist.items.firstOrNull()?.thumbnailUrl ?: "")
        } else {
            playlist.items.forEach { item ->
                val req = DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = item.url,
                    container = _ui.value.selectedContainer,
                    quality = _ui.value.selectedQuality,
                    outputDir = playlistDir.absolutePath,
                    serverUrl = _ui.value.serverUrl
                )
                downloadService?.enqueue(req, videoTitle = item.title, thumbnailUrl = item.thumbnailUrl)
            }
        }
    }

    fun cancelItem(id: String) = downloadService?.cancelItem(id)
    fun clearDone()            = downloadService?.clearDone()

    // ── AURA Library & Intelligence ─────────────────────────────────────────

    fun loadDownloadedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isScanningFiles = true) }
            val files = com.ypdlp.downloader.aura.LibraryScanner.scanLocalMedia(getApplication(), _ui.value.customMusicFolders)
            val albums = com.ypdlp.downloader.aura.LibraryScanner.groupAlbums(files)
            val artists = com.ypdlp.downloader.aura.LibraryScanner.groupArtists(files)
            val stats = com.ypdlp.downloader.aura.LibraryScanner.computeStatistics(files, _ui.value.favorites)

            // Generate Mood Playlists
            val moodPlaylists = MoodType.values().map { mood ->
                com.ypdlp.downloader.aura.MagicPlaylistEngine.generateMoodPlaylist(mood, files)
            }

            _ui.update {
                it.copy(
                    downloadedFiles = files,
                    albums = albums,
                    artists = artists,
                    smartPlaylists = moodPlaylists,
                    statistics = stats,
                    isScanningFiles = false
                )
            }
        }
    }

    fun addCustomMusicFolder(folderPath: String) {
        val updated = _ui.value.customMusicFolders.toMutableSet()
        updated.add(folderPath)
        prefs.edit().putString("aura_custom_folders_str", updated.joinToString("|||")).apply()
        _ui.update { it.copy(customMusicFolders = updated) }
        loadDownloadedFiles()
    }

    fun removeCustomMusicFolder(folderPath: String) {
        val updated = _ui.value.customMusicFolders.toMutableSet()
        updated.remove(folderPath)
        prefs.edit().putString("aura_custom_folders_str", updated.joinToString("|||")).apply()
        _ui.update { it.copy(customMusicFolders = updated) }
        loadDownloadedFiles()
    }

    fun toggleFavorite(file: DownloadedFile) {
        val currentFavs = _ui.value.favorites.toMutableSet()
        if (currentFavs.contains(file.path)) {
            currentFavs.remove(file.path)
        } else {
            currentFavs.add(file.path)
        }
        prefs.edit().putStringSet("aura_favorites", currentFavs).apply()
        _ui.update { it.copy(favorites = currentFavs) }
    }

    fun setSearchQuery(q: String) {
        _ui.update { it.copy(searchQuery = q) }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        _ui.update { it.copy(visualizerMode = mode) }
    }

    fun toggleOtakuMode(): Boolean {
        val newMode = !_ui.value.isOtakuMode
        prefs.edit().putBoolean("otaku_mode", newMode).apply()
        _ui.update { it.copy(isOtakuMode = newMode) }
        return newMode
    }

    fun generateMagicPlaylist(prompt: String) {
        val playlist = com.ypdlp.downloader.aura.MagicPlaylistEngine.generateFromPrompt(prompt, _ui.value.downloadedFiles)
        _ui.update {
            it.copy(
                activePlaylist = playlist,
                smartPlaylists = listOf(playlist) + it.smartPlaylists
            )
        }
        playlist.tracks.firstOrNull()?.let { playMediaFile(it) }
    }

    fun startAiDjSession() {
        val (commentary, sessionTracks) = com.ypdlp.downloader.aura.AiDjService.generateDjSession(_ui.value.downloadedFiles)
        _ui.update { it.copy(aiDjCommentary = commentary) }
        sessionTracks.firstOrNull()?.let { playMediaFile(it) }
    }

    fun toggleAutoMix() {
        val cur = _ui.value.autoMixSession
        val updated = cur.copy(isEnabled = !cur.isEnabled)
        _ui.update { it.copy(autoMixSession = updated) }
    }

    fun setAutoMixTransitionMode(mode: AutoMixTransitionMode) {
        val dur = com.ypdlp.downloader.aura.AutoMixEngine.getOptimalTransitionDuration(mode)
        val updated = _ui.value.autoMixSession.copy(transitionMode = mode, transitionDurationSec = dur)
        _ui.update { it.copy(autoMixSession = updated) }
    }

    fun openVideoPlayer(file: DownloadedFile) {
        _ui.update { it.copy(isVideoPlayerFullscreen = true, activeVideoFile = file) }
    }

    fun closeVideoPlayer() {
        _ui.update { it.copy(isVideoPlayerFullscreen = false, activeVideoFile = null) }
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

    fun playNextInQueue() {
        val files = _ui.value.downloadedFiles
        val cur = playerState.value.currentFile
        if (files.isEmpty()) return
        val next = if (cur != null) {
            val idx = files.indexOfFirst { it.path == cur.path }
            if (idx in 0 until files.size - 1) files[idx + 1] else files.first()
        } else {
            files.first()
        }
        playMediaFile(next)
    }

    fun seekTo(posMs: Long) {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_PLAY
        }
        // Direct seek via binder if available or service
    }

    fun playMediaFile(file: DownloadedFile) {
        MediaPlaybackService.setPlaybackQueue(_ui.value.downloadedFiles)
        MediaPlaybackService.updateCurrentMediaFile(file)
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

    // ── 8MAN Dev Console & Maintenance ───────────────────────────────────────
    val logs = AppLogger.logs

    fun toggleHachimanMode(): Boolean {
        val newMode = !_ui.value.isHachimanMode
        prefs.edit().putBoolean("hachiman_mode", newMode).apply()
        _ui.update { it.copy(isHachimanMode = newMode) }
        if (newMode) {
            AppLogger.i("8MAN", "★ 8MAN Mode Activated: 'Youth is a lie. It is evil.' — Hachiman Hikigaya")
        } else {
            AppLogger.i("Yukino", "❄ Yukino Mode Restored: 'Being hated is not a virtue.'")
        }
        return newMode
    }

    fun setTerminalInput(v: String) {
        _ui.update { it.copy(terminalInput = v) }
    }

    fun clearConsoleLogs() {
        AppLogger.clear()
    }

    fun executeTerminalCommand() {
        val raw = _ui.value.terminalInput.trim()
        if (raw.isBlank()) return
        _ui.update { it.copy(isRunningCommand = true, terminalInput = "") }
        AppLogger.cmd("Shell", raw)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when {
                    raw.equals("clear", ignoreCase = true) || raw.equals("cls", ignoreCase = true) -> {
                        AppLogger.clear()
                    }
                    raw.equals("help", ignoreCase = true) -> {
                        AppLogger.i("Terminal", "═══ 8MAN TERMINAL COMMANDS ═══")
                        AppLogger.i("Terminal", "• scan                : Deep scan local audio and rebuild database")
                        AppLogger.i("Terminal", "• update              : Update yt-dlp core engine to latest version")
                        AppLogger.i("Terminal", "• clean / clearcache  : Wipe temporary cache & incomplete files")
                        AppLogger.i("Terminal", "• diag                : Print full system & storage diagnostic")
                        AppLogger.i("Terminal", "• ping                : Test YouTube CDN latency & DNS")
                        AppLogger.i("Terminal", "• reinit              : Force reload YoutubeDL & FFmpeg engine")
                        AppLogger.i("Terminal", "• dl <url>            : Direct download audio (highest quality 320k)")
                        AppLogger.i("Terminal", "• playlist <url>      : Batch download entire YouTube playlist")
                        AppLogger.i("Terminal", "• info <url>          : Inspect video metadata & formats")
                        AppLogger.i("Terminal", "• stats               : View system and playback statistics")
                        AppLogger.i("Terminal", "• 8man                : Philosophy of Hachiman Hikigaya")
                    }
                    raw.equals("scan", ignoreCase = true) -> {
                        AppLogger.i("Scanner", "🔍 Initiating deep filesystem scan...")
                        loadDownloadedFiles()
                        AppLogger.i("Scanner", "✔ Scan finished! Discovered ${_ui.value.downloadedFiles.size} audio tracks.")
                    }
                    raw.equals("stats", ignoreCase = true) -> {
                        AppLogger.i("Stats", "📊 Audio Tracks: ${_ui.value.downloadedFiles.size} | Favorites: ${_ui.value.favorites.size}")
                        AppLogger.i("Stats", "📁 Custom Folders: ${_ui.value.customMusicFolders.size}")
                    }
                    raw.startsWith("dl ", ignoreCase = true) -> {
                        val targetUrl = raw.removePrefix("dl ").trim()
                        AppLogger.i("Download", "⚡ Starting direct audio download for: $targetUrl")
                        val req = DownloadRequest(
                            id = UUID.randomUUID().toString(),
                            url = targetUrl,
                            container = "MP3",
                            quality = "Best",
                            outputDir = DownloadService.getDownloadDirectory(getApplication()).absolutePath
                        )
                        downloadService?.enqueue(req, videoTitle = "Direct DL", thumbnailUrl = "")
                    }
                    raw.startsWith("playlist ", ignoreCase = true) -> {
                        val targetUrl = raw.removePrefix("playlist ").trim()
                        AppLogger.i("Playlist", "⚡ Queuing entire playlist: $targetUrl")
                        val req = DownloadRequest(
                            id = UUID.randomUUID().toString(),
                            url = targetUrl,
                            container = "MP3",
                            quality = "Best",
                            outputDir = DownloadService.getDownloadDirectory(getApplication()).absolutePath
                        )
                        downloadService?.enqueue(req, videoTitle = "Playlist Batch", thumbnailUrl = "")
                    }
                    raw.equals("diag", ignoreCase = true) -> {
                        val report = AppLogger.getDiagnosticReport(getApplication())
                        report.lines().forEach { line ->
                            if (line.isNotBlank()) AppLogger.d("Diag", line)
                        }
                    }
                    raw.equals("ping", ignoreCase = true) -> {
                        pingYouTube()
                    }
                    raw.equals("reinit", ignoreCase = true) -> {
                        forceReinitEngine()
                    }
                    raw.equals("update", ignoreCase = true) || raw.equals("upgrade", ignoreCase = true) || raw.equals("yt-dlp -u", ignoreCase = true) -> {
                        updateEngine()
                    }
                    raw.equals("clean", ignoreCase = true) || raw.equals("prune", ignoreCase = true) || raw.equals("clearcache", ignoreCase = true) -> {
                        clearTempCache()
                    }
                    raw.equals("8man", ignoreCase = true) -> {
                        val quotes = listOf(
                            "Youth is a lie. It is evil. Those who glorify it are merely deluding themselves.",
                            "I hate nice girls. A casual exchange of greetings sets my mind racing. A text message makes my heart flutter.",
                            "There's no point in putting on an act to make someone like you. The real you will just suffer more later.",
                            "If you can't be loved, at least be feared. But if you can't even be feared, just be alone and comfortable.",
                            "I don't want something superficial. I want something genuine.",
                            "Hard work betrays none, but it betrays plenty of dreams.",
                            "Problem solved? No, problems aren't solved. They are just shoved onto someone else."
                        )
                        AppLogger.i("8MAN", "💬 ${quotes.random()}")
                    }
                    raw.startsWith("info ", ignoreCase = true) -> {
                        val targetUrl = raw.removePrefix("info ").trim()
                        AppLogger.i("yt-dlp", "Querying info for: $targetUrl")
                        YPDlpApp.ensureInitialized(getApplication())
                        val req = YoutubeDLRequest(targetUrl).apply {
                            addOption("--no-check-certificates")
                        }
                        val info = YoutubeDL.getInstance().getInfo(req)
                        AppLogger.i("yt-dlp", "Title: ${info.title}")
                        AppLogger.i("yt-dlp", "Uploader: ${info.uploader}")
                        AppLogger.i("yt-dlp", "Duration: ${info.duration}s | Views: ${info.viewCount}")
                    }
                    else -> {
                        AppLogger.i("yt-dlp", "Executing custom command: $raw")
                        YPDlpApp.ensureInitialized(getApplication())
                        val req = YoutubeDLRequest(raw)
                        YoutubeDL.getInstance().execute(req, UUID.randomUUID().toString()) { _, _, line ->
                            AppLogger.d("yt-dlp", line)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Shell", "Execution failed: ${e.message}")
            } finally {
                _ui.update { it.copy(isRunningCommand = false) }
            }
        }
    }

    fun updateEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("Engine", "🔄 Updating yt-dlp binary to latest YouTube fixes...")
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(getApplication())
                AppLogger.i("Engine", "✔ yt-dlp binary updated: $status")
            } catch (ue: Throwable) {
                AppLogger.e("Engine", "✘ yt-dlp update failed: ${ue.message}")
            }
        }
    }

    fun forceReinitEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("Engine", "🔄 Forcing manual re-initialization of YoutubeDL & FFmpeg...")
            try {
                YoutubeDL.getInstance().init(getApplication())
                try {
                    com.yausername.ffmpeg.FFmpeg.getInstance().init(getApplication())
                } catch (e: Exception) {}
                AppLogger.i("Engine", "✔ On-device engine re-initialized successfully!")
                updateEngine()
            } catch (e: Exception) {
                AppLogger.e("Engine", "✘ Re-init error: ${e.message}")
            }
        }
    }

    fun clearTempCache() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("System", "🧹 Scanning and cleaning cache & temp files...")
            var count = 0
            var freedBytes = 0L
            try {
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    freedBytes += file.length()
                    if (file.deleteRecursively()) count++
                }
                val downloadDir = DownloadService.getDownloadDirectory(getApplication())
                downloadDir.listFiles()?.filter { it.name.endsWith(".part") || it.name.endsWith(".ytdl") }?.forEach {
                    freedBytes += it.length()
                    if (it.delete()) count++
                }
                val mb = freedBytes / (1024.0 * 1024.0)
                AppLogger.i("System", "✔ Cleared $count files (freed %.2f MB)".format(mb))
            } catch (e: Exception) {
                AppLogger.e("System", "✘ Clear cache error: ${e.message}")
            }
        }
    }

    fun pingYouTube() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("Network", "🌐 Testing YouTube connectivity & DNS latency...")
            try {
                val start = System.currentTimeMillis()
                val url = java.net.URL("https://www.youtube.com/generate_204")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val latency = System.currentTimeMillis() - start
                AppLogger.i("Network", "✔ YouTube HTTP Response: $code (Latency: ${latency}ms)")
            } catch (e: Exception) {
                AppLogger.e("Network", "✘ YouTube unreachable: ${e.message}")
            }
        }
    }

    fun getDiagnosticReport(): String = AppLogger.getDiagnosticReport(getApplication())

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(connection)
        } catch (e: Exception) {
            // Ignored
        }
        super.onCleared()
    }
}
