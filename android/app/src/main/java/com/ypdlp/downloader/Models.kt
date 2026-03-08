package com.ypdlp.downloader

// ─── Data Models ──────────────────────────────────────────────────────────────

data class VideoInfo(
    val url: String = "",
    val title: String = "",
    val channel: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = "",
    val viewCount: Long = 0L,
    val availableFormats: List<FormatOption> = emptyList()
)

data class FormatOption(
    val formatId: String,
    val label: String,        // e.g. "1080p MP4"
    val ext: String,
    val resolution: String?,
    val fileSize: Long?
)

data class DownloadItem(
    val id: String,
    val videoInfo: VideoInfo,
    val container: String,    // "MP4" | "MKV" | "MP3" | "M4A" | ...
    val quality: String,      // "1080p" | "4K (2160p)" | "Best" | ...
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var speed: String = "",
    var eta: String = "",
    var errorMessage: String = ""
)

enum class DownloadStatus { QUEUED, DOWNLOADING, POST_PROCESSING, DONE, ERROR, CANCELLED }

// ─── Download Request passed from UI to service ───────────────────────────────

data class DownloadRequest(
    val id: String,
    val url: String,
    val container: String,
    val quality: String,
    val outputDir: String,
    val ffmpegPath: String
)
