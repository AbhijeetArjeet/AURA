package com.ypdlp.downloader

import android.content.Context
import android.os.Build
import java.io.File

object FfmpegHelper {

    private const val FFMPEG_VERSION = "6.0"

    /**
     * Extracts the ffmpeg binary from assets to the internal files directory
     * and returns its absolute path, or null if not bundled.
     */
    fun getFfmpegPath(context: Context): String? {
        val ffmpegFile = File(context.filesDir, "ffmpeg")

        // If it already exists, is executable, and version matches, return path
        if (ffmpegFile.exists() && ffmpegFile.canExecute()) {
            val versionFile = File(context.filesDir, "ffmpeg_version")
            if (versionFile.exists() && versionFile.readText() == FFMPEG_VERSION) {
                return ffmpegFile.absolutePath
            }
        }

        // Try extracting from assets
        val abi = getAbi() ?: return null
        val assetPath = "ffmpeg/$abi/ffmpeg"

        return try {
            context.assets.open(assetPath).use { input ->
                ffmpegFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            ffmpegFile.setExecutable(true, false)
            ffmpegFile.setReadable(true, false)
            File(context.filesDir, "ffmpeg_version").writeText(FFMPEG_VERSION)
            ffmpegFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getAbi(): String? {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("x86_64")    -> "x86_64"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> null
        }
    }
}
