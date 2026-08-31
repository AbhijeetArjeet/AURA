package com.ypdlp.downloader

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

// ─── Data Models ──────────────────────────────────────────────────────────────

@Parcelize
data class VideoInfo(
    val url: String = "",
    val title: String = "",
    val channel: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = "",
    val viewCount: Long = 0L,
    val availableFormats: List<FormatOption> = emptyList()
) : Parcelable

@Parcelize
data class PlaylistInfo(
    val title: String = "",
    val author: String = "",
    val itemCount: Int = 0,
    val items: List<VideoInfo> = emptyList(),
    val url: String = ""
) : Parcelable

@Parcelize
data class FormatOption(
    val formatId: String,
    val label: String,        // e.g. "1080p MP4"
    val ext: String,
    val resolution: String?,
    val fileSize: Long?
) : Parcelable

data class DownloadItem(
    val id: String,
    val videoInfo: VideoInfo,
    val container: String,    // "MP4" | "MKV" | "MP3" | "M4A" | ...
    val quality: String,      // "1080p" | "4K (2160p)" | "Best" | ...
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var speed: String = "",
    var eta: String = "",
    var errorMessage: String = "",
    var localFilePath: String? = null
)

enum class DownloadStatus { QUEUED, DOWNLOADING, POST_PROCESSING, DONE, ERROR, CANCELLED }

// ─── Download Request passed from UI to service ───────────────────────────────

@Parcelize
data class DownloadRequest(
    val id: String,
    val url: String,
    val container: String,
    val quality: String,
    val outputDir: String,
    val ffmpegPath: String = "",
    val serverUrl: String = ""
) : Parcelable

// ─── Downloaded Media in Library ──────────────────────────────────────────────

data class DownloadedFile(
    val file: File,
    val name: String,
    val title: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val isVideo: Boolean,
    val path: String,
    val lastModified: Long,
    val extension: String
)

// ─── Player State ─────────────────────────────────────────────────────────────

data class PlayerState(
    val currentFile: DownloadedFile? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)
