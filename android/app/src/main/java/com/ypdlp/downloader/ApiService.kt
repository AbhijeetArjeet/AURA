package com.ypdlp.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApiService {

    const val DEFAULT_SERVER_URL = "https://yt-downloader-ccm6.onrender.com"
    private const val TAG = "ApiService"

    private fun normalizeServerUrl(serverUrl: String?): String {
        val base = if (serverUrl.isNullOrBlank()) DEFAULT_SERVER_URL else serverUrl.trim()
        return base.removeSuffix("/")
    }

    /**
     * Fetch video info from the backend server
     */
    suspend fun fetchVideoInfo(videoUrl: String, customServerUrl: String = ""): VideoInfo = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(customServerUrl)
        val endpoint = "$server/api/info"
        Log.d(TAG, "Fetching video info from $endpoint for $videoUrl")

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "YPDlp-Android/1.0")
        }

        val jsonInput = JSONObject().put("url", videoUrl).toString()
        conn.outputStream.use { os ->
            os.write(jsonInput.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP error $code"
            val errMsg = try {
                JSONObject(err).optString("error", err)
            } catch (e: Exception) {
                err
            }
            throw Exception(errMsg)
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)

        val durationStr = json.optString("duration", "0:00")
        val durationParts = durationStr.split(":").mapNotNull { it.toLongOrNull() }
        val durationSecs = when (durationParts.size) {
            3 -> durationParts[0] * 3600 + durationParts[1] * 60 + durationParts[2]
            2 -> durationParts[0] * 60 + durationParts[1]
            1 -> durationParts[0]
            else -> 0L
        }

        val rawViews = json.optString("views", "0").replace(",", "").replace(" views", "").trim()
        val viewCount = rawViews.toLongOrNull() ?: 0L

        VideoInfo(
            url = json.optString("url", videoUrl),
            title = json.optString("title", "Unknown Title"),
            channel = json.optString("channel", ""),
            durationSeconds = durationSecs,
            thumbnailUrl = json.optString("thumbnail", ""),
            viewCount = viewCount
        )
    }

    /**
     * Fetch playlist info from the backend server
     */
    suspend fun fetchPlaylistInfo(playlistUrl: String, customServerUrl: String = ""): PlaylistInfo = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(customServerUrl)
        val endpoint = "$server/api/playlist"
        Log.d(TAG, "Fetching playlist info from $endpoint for $playlistUrl")

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "YPDlp-Android/1.0")
        }

        val jsonInput = JSONObject().put("url", playlistUrl).toString()
        conn.outputStream.use { os ->
            os.write(jsonInput.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP error $code"
            val errMsg = try {
                JSONObject(err).optString("error", err)
            } catch (e: Exception) {
                err
            }
            throw Exception(errMsg)
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)

        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<VideoInfo>()

        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            items.add(
                VideoInfo(
                    url = obj.optString("url", ""),
                    title = obj.optString("title", "Item #${i + 1}"),
                    channel = obj.optString("channel", ""),
                    durationSeconds = obj.optLong("duration_seconds", 0L),
                    thumbnailUrl = obj.optString("thumbnail", "")
                )
            )
        }

        PlaylistInfo(
            title = json.optString("title", "YouTube Playlist"),
            author = json.optString("author", ""),
            itemCount = json.optInt("item_count", items.size),
            items = items,
            url = json.optString("url", playlistUrl)
        )
    }

    /**
     * Start a download job on the backend
     */
    suspend fun startDownload(
        videoUrl: String,
        container: String,
        quality: String,
        customServerUrl: String = ""
    ): String = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(customServerUrl)
        val endpoint = "$server/api/download"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "YPDlp-Android/1.0")
        }

        val qualityParam = when (quality) {
            "4K (2160p)" -> "2160p"
            "2K (1440p)" -> "1440p"
            "1080p" -> "1080p"
            "720p"  -> "720p"
            "480p"  -> "480p"
            "360p"  -> "360p"
            else    -> "best"
        }

        val jsonInput = JSONObject().apply {
            put("url", videoUrl)
            put("format", container.lowercase())
            put("quality", qualityParam)
        }.toString()

        conn.outputStream.use { os ->
            os.write(jsonInput.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP error $code"
            throw Exception(err)
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)
        json.getString("job_id")
    }

    /**
     * Poll status of a download job
     */
    suspend fun getStatus(jobId: String, customServerUrl: String = ""): JSONObject = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(customServerUrl)
        val endpoint = "$server/api/status/$jobId"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "YPDlp-Android/1.0")
        }

        if (conn.responseCode !in 200..299) {
            throw Exception("Failed to check status: HTTP ${conn.responseCode}")
        }

        val text = conn.inputStream.bufferedReader().readText()
        JSONObject(text)
    }

    /**
     * Download completed file from server into local file
     */
    suspend fun downloadFile(
        jobId: String,
        destinationFile: File,
        customServerUrl: String = "",
        onProgress: (percent: Int, speed: String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(customServerUrl)
        val endpoint = "$server/api/file/$jobId"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "YPDlp-Android/1.0")
        }

        if (conn.responseCode !in 200..299) {
            throw Exception("Download file error: HTTP ${conn.responseCode}")
        }

        val totalBytes = conn.contentLengthLong
        var downloadedBytes = 0L
        val startTime = System.currentTimeMillis()

        destinationFile.parentFile?.mkdirs()
        conn.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val speedStr = if (elapsedSec > 0) {
                        val speedBps = downloadedBytes / elapsedSec
                        String.format("%.1f MB/s", speedBps / (1024 * 1024))
                    } else ""

                    val pct = if (totalBytes > 0) {
                        ((downloadedBytes.toDouble() / totalBytes) * 100).toInt()
                    } else 0

                    onProgress(pct.coerceIn(0, 100), speedStr)
                }
            }
        }
    }
}
