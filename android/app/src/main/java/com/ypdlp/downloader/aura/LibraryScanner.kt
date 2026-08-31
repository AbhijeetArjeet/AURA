package com.ypdlp.downloader.aura

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.ypdlp.downloader.AuraAlbum
import com.ypdlp.downloader.AuraArtist
import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.ListeningStatistics
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object LibraryScanner {

    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi")

    private val IGNORED_DIRECTORY_KEYWORDS = setOf(
        "record", "recording", "voice", "call", "whatsapp", "telegram",
        "audio_record", "sound_recorder", "callrecord", "voicenote", "notifications", "ringtones", "alarms"
    )

    suspend fun scanLocalMedia(context: Context, customFolderPaths: Set<String> = emptySet()): List<DownloadedFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DownloadedFile>()

        // 1. App YPDlp Download directory (for on-device downloads)
        val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YPDlp")
        scanFolder(appDir, results)

        // 2. Specific Public Downloads/YPDlp directory
        val publicYpdlp = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YPDlp")
        if (publicYpdlp.exists() && publicYpdlp.canRead()) {
            scanFolder(publicYpdlp, results)
        }

        // 3. Standard Public Music Directory
        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (publicMusic.exists() && publicMusic.canRead()) {
            scanFolder(publicMusic, results)
        }

        // 4. User-Selected Custom Folders ONLY (Never blind system scans)
        for (customPath in customFolderPaths) {
            try {
                if (customPath.startsWith("content://")) {
                    val treeUri = android.net.Uri.parse(customPath)
                    val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                    if (documentFile != null) {
                        scanDocumentFolder(context, documentFile, results)
                    }
                } else {
                    val customFolder = File(customPath)
                    if (customFolder.exists() && customFolder.canRead()) {
                        scanFolder(customFolder, results)
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible individual folders
            }
        }

        // Deduplicate by canonical path, filter out non-music voice notes / call directories, sort newest first
        results.distinctBy { it.path }
            .filter { file ->
                val lowerPath = file.path.lowercase()
                !IGNORED_DIRECTORY_KEYWORDS.any { lowerPath.contains(it) }
            }
            .sortedByDescending { it.lastModified }
    }

    private fun scanDocumentFolder(context: Context, dir: DocumentFile, results: MutableList<DownloadedFile>) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles().forEach { doc ->
            if (doc.isFile) {
                val name = doc.name ?: ""
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS) {
                    val isVideo = ext in VIDEO_EXTENSIONS
                    val item = parseDocumentMetadata(context, doc, isVideo)
                    if (item != null) {
                        results.add(item)
                    }
                }
            } else if (doc.isDirectory && !(doc.name ?: "").startsWith(".")) {
                scanDocumentFolder(context, doc, results)
            }
        }
    }

    private fun parseDocumentMetadata(context: Context, doc: DocumentFile, isVideo: Boolean): DownloadedFile? {
        val rawName = doc.name ?: return null
        val decodedName = try {
            java.net.URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            rawName
        }
        var cleanTitle = decodedName.substringBeforeLast('.').replace("_", " ").trim()
        var artist = "Unknown Artist"
        val parentFolder = doc.parentFile?.name
        var album = if (!parentFolder.isNullOrBlank()) {
            try { java.net.URLDecoder.decode(parentFolder, "UTF-8") } catch (e: Exception) { parentFolder }
        } else if (isVideo) {
            "Music Video"
        } else {
            "Local Audio"
        }
        var durationSecs = 0L
        var embeddedArt: ByteArray? = null

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, doc.uri)

            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) cleanTitle = metaTitle.trim()
            if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum.trim()
            if (!metaDur.isNullOrBlank()) durationSecs = (metaDur.toLongOrNull() ?: 0L) / 1000L
            embeddedArt = retriever.embeddedPicture

            retriever.release()
        } catch (e: Exception) {
            if (cleanTitle.contains(" - ")) {
                val parts = cleanTitle.split(" - ")
                if (parts.size >= 2) artist = parts[0].trim()
            }
        }

        val (dom, sec) = generatePaletteColors(cleanTitle, artist)
        val mb = doc.length() / (1024.0 * 1024.0)

        return DownloadedFile(
            file = File(doc.uri.toString()),
            name = decodedName,
            title = cleanTitle,
            sizeBytes = doc.length(),
            sizeFormatted = "%.1f MB".format(mb),
            isVideo = isVideo,
            path = doc.uri.toString(),
            lastModified = doc.lastModified(),
            extension = decodedName.substringAfterLast('.', "").uppercase(),
            artist = artist,
            album = album,
            durationSeconds = durationSecs,
            bpm = 120,
            energyLevel = 0.6f,
            dominantColorHex = dom,
            secondaryColorHex = sec,
            artworkByteArray = embeddedArt
        )
    }

    private fun scanFolder(folder: File, results: MutableList<DownloadedFile>) {
        if (!folder.exists() || !folder.isDirectory) return

        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS) {
                    val isVideo = ext in VIDEO_EXTENSIONS
                    val parsed = parseMetadata(file, isVideo)
                    results.add(parsed)
                }
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                scanFolder(file, results)
            }
        }
    }

    private fun parseMetadata(file: File, isVideo: Boolean): DownloadedFile {
        var artist = "Unknown Artist"
        val parentDirName = file.parentFile?.name ?: ""
        var album = if (parentDirName.isNotBlank() && parentDirName != "YPDlp" && parentDirName != "Download" && parentDirName != "Music") {
            parentDirName
        } else if (isVideo) {
            "Music Video"
        } else {
            "Local Track"
        }
        var durationSecs = 0L
        val cleanTitle = file.nameWithoutExtension.replace("_", " ").trim()

        var embeddedArt: ByteArray? = null
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            if (!metaDur.isNullOrBlank()) durationSecs = (metaDur.toLongOrNull() ?: 0L) / 1000L

            embeddedArt = retriever.embeddedPicture

            retriever.release()
        } catch (e: Exception) {
            // Fallback parsing from filename if "Artist - Title"
            if (cleanTitle.contains(" - ")) {
                val parts = cleanTitle.split(" - ")
                if (parts.size >= 2) {
                    artist = parts[0].trim()
                }
            }
        }

        val bpm = AutoMixEngine.calculateTrackBpm(
            DownloadedFile(
                file = file,
                name = file.name,
                title = cleanTitle,
                sizeBytes = file.length(),
                sizeFormatted = "",
                isVideo = isVideo,
                path = file.absolutePath,
                lastModified = file.lastModified(),
                extension = file.extension.uppercase()
            )
        )

        val energy = AutoMixEngine.calculateEnergyLevel(
            DownloadedFile(
                file = file,
                name = file.name,
                title = cleanTitle,
                sizeBytes = file.length(),
                sizeFormatted = "",
                isVideo = isVideo,
                path = file.absolutePath,
                lastModified = file.lastModified(),
                extension = file.extension.uppercase()
            )
        )

        // Generate dynamic atmospheric colors based on artist & title hash
        val (dom, sec) = generatePaletteColors(cleanTitle, artist)

        val mb = file.length() / (1024.0 * 1024.0)

        return DownloadedFile(
            file = file,
            name = file.name,
            title = cleanTitle,
            sizeBytes = file.length(),
            sizeFormatted = "%.1f MB".format(mb),
            isVideo = isVideo,
            path = file.absolutePath,
            lastModified = file.lastModified(),
            extension = file.extension.uppercase(),
            artist = artist,
            album = album,
            durationSeconds = durationSecs,
            bpm = bpm,
            energyLevel = energy,
            dominantColorHex = dom,
            secondaryColorHex = sec,
            artworkByteArray = embeddedArt
        )
    }

    private fun generatePaletteColors(title: String, artist: String): Pair<String, String> {
        val colorPairs = listOf(
            "#FF2A55" to "#9D4EDD", // Neon Red & Violet
            "#00E5FF" to "#00FF66", // Cyber Cyan & Emerald
            "#F59E0B" to "#EF4444", // Sunset Gold & Crimson
            "#8B5CF6" to "#EC4899", // Purple Aura & Rose
            "#10B981" to "#06B6D4", // Mint & Ocean
            "#3B82F6" to "#6366F1", // Electric Blue & Indigo
            "#F43F5E" to "#FB7185", // Sakura Pink & Rouge
            "#F97316" to "#EAB308"  // Cyberpunk Orange & Gold
        )
        val idx = abs((title + artist).hashCode()) % colorPairs.size
        return colorPairs[idx]
    }

    fun groupAlbums(files: List<DownloadedFile>): List<AuraAlbum> {
        return files.groupBy { it.album.ifBlank { "Local Tracks" } }.map { (albumTitle, tracks) ->
            AuraAlbum(
                title = albumTitle,
                artist = tracks.firstOrNull()?.artist ?: "Various Artists",
                trackCount = tracks.size,
                sampleFile = tracks.first()
            )
        }.sortedByDescending { it.trackCount }
    }

    fun groupArtists(files: List<DownloadedFile>): List<AuraArtist> {
        return files.groupBy { it.artist.ifBlank { "Unknown Artist" } }.map { (artistName, tracks) ->
            AuraArtist(
                name = artistName,
                trackCount = tracks.size,
                sampleFile = tracks.first()
            )
        }.sortedByDescending { it.trackCount }
    }

    fun computeStatistics(files: List<DownloadedFile>, favorites: Set<String>): ListeningStatistics {
        if (files.isEmpty()) return ListeningStatistics()

        val topArt = files.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: "Local Library"
        val topTrack = files.firstOrNull()?.title ?: "After Dark"
        val totalSecs = files.sumOf { it.durationSeconds }
        val hours = if (totalSecs > 0) totalSecs / 3600f else (files.size * 3.5f) / 60f

        return ListeningStatistics(
            totalHoursListened = "%.1f".format(hours).toFloatOrNull() ?: 12.5f,
            topArtist = topArt,
            topGenre = if (files.any { it.title.contains("anime", true) || it.title.contains("ost", true) }) "Anime OST / J-Pop" else "Alternative / Synthwave",
            mostPlayedSong = topTrack,
            mostActiveTime = "11 PM – 2 AM",
            totalTracksCount = files.size,
            favoriteCount = favorites.size
        )
    }
}