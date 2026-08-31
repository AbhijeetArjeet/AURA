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
    val extension: String,
    val artist: String = "Unknown Artist",
    val album: String = "Local Audio",
    val durationSeconds: Long = 0L,
    val bpm: Int = 120,
    val energyLevel: Float = 0.6f, // 0.0 to 1.0
    val mood: String = "Chill",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val dominantColorHex: String = "#FF2A55",
    val secondaryColorHex: String = "#9D4EDD"
)

// ─── AURA Extended Entities ──────────────────────────────────────────────────

data class AuraAlbum(
    val title: String,
    val artist: String,
    val trackCount: Int,
    val sampleFile: DownloadedFile,
    val year: String = ""
)

data class AuraArtist(
    val name: String,
    val trackCount: Int,
    val sampleFile: DownloadedFile
)

enum class VisualizerMode(val displayName: String, val iconName: String) {
    SPECTRUM("Spectrum", "Equalizer"),
    WAVEFORM("Waveform", "GraphicEq"),
    CIRCULAR("Orbital Aura", "DonutLarge"),
    PARTICLES("Starfield", "AutoAwesome"),
    MINIMAL("Minimal Pulse", "Grain"),
    OFF("Off", "Close")
}

enum class AutoMixTransitionMode(val label: String, val description: String) {
    SMOOTH("Smooth Blend", "Gentle volumetric crossfade over 8-12 seconds"),
    BEAT_MATCH("Beat Match", "Tempo-aligned energetic drop at the beat drop"),
    DJ("DJ Transition", "High-pass sweep & echo out into the next track"),
    CINEMATIC("Cinematic", "Ambient pad swell with 15-second gradual fade"),
    CHILL("Chill Wave", "Soft low-pass filter crossfade"),
    HARD_CUT("Hard Drop", "Immediate instant cut without gap")
}

data class AutoMixSession(
    val isEnabled: Boolean = false,
    val transitionMode: AutoMixTransitionMode = AutoMixTransitionMode.SMOOTH,
    val transitionDurationSec: Int = 8,
    val currentBpm: Int = 124,
    val nextBpm: Int = 126,
    val energyCurve: List<Float> = listOf(0.4f, 0.6f, 0.8f, 0.9f, 0.7f, 0.5f),
    val isTransitioning: Boolean = false,
    val transitionProgress: Float = 0f // 0.0 to 1.0
)

enum class MoodType(val emoji: String, val title: String, val subtitle: String, val colorHex: String) {
    MIDNIGHT("🌙", "Midnight Atmosphere", "Late night deep thoughts & ambient vibes", "#38BDF8"),
    RAINY("🌧️", "Rainy Day Nostalgia", "Mellow acoustic melodies & lo-fi beats", "#818CF8"),
    HYPE("🔥", "Maximum Hype", "High BPM adrenaline pumps & bangers", "#F43F5E"),
    ROMANTIC("❤️", "Romantic & Warm", "Soulful vocals & warm guitars", "#FB7185"),
    CHILL("😌", "Pure Chill", "Relaxing sounds to unwind after work", "#34D399"),
    FOCUS("🧠", "Deep Coding & Focus", "Zero distractions, rhythmic synth wave", "#A78BFA"),
    GYM("⚡", "Workout Adrenaline", "Heavy basslines to crush your PRs", "#F59E0B"),
    COZY("🌸", "Cozy Anime Cafe", "Lively Japanese beats, J-Pop & OST classics", "#F472B6"),
    GAMING("🎮", "Late Night Gaming", "Synthwave & electronic night drives", "#60A5FA")
}

data class SmartPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val gradientColors: List<Long>,
    val tracks: List<DownloadedFile>
)

data class AiDjCommentary(
    val text: String,
    val mood: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ListeningStatistics(
    val totalHoursListened: Float = 14.2f,
    val topArtist: String = "Hachiman Vibes",
    val topGenre: String = "Anime OST / Lofi",
    val mostPlayedSong: String = "After Dark",
    val mostActiveTime: String = "11 PM – 2 AM",
    val totalTracksCount: Int = 0,
    val favoriteCount: Int = 0
)

// ─── Player State ─────────────────────────────────────────────────────────────

data class PlayerState(
    val currentFile: DownloadedFile? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isVideoMode: Boolean = false,
    val isShuffle: Boolean = false,
    val isSmartShuffle: Boolean = true,
    val isRepeat: Boolean = false,
    val visualizerMode: VisualizerMode = VisualizerMode.SPECTRUM,
    val visualizerData: FloatArray = FloatArray(32) { (Math.random() * 0.5).toFloat() },
    val autoMixSession: AutoMixSession = AutoMixSession(),
    val sleepTimerMinutesLeft: Int? = null,
    val isOtakuMode: Boolean = false,
    val currentLyricsLine: String = "",
    val lyricsLines: List<Pair<Long, String>> = emptyList(),
    val queue: List<DownloadedFile> = emptyList(),
    val queueIndex: Int = 0
)
