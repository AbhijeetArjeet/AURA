package com.ypdlp.downloader

import android.content.Context
import android.os.Build
import java.io.File

object FfmpegHelper {

    private const val FFMPEG_VERSION = "6.0"

    /**
     * Extracts the ffmpeg binary from assets to the internal files directory
     * and returns its absolute path.
     */
    fun getFfmpegPath(context: Context): String {
        val ffmpegFile = File(context.filesDir, "ffmpeg")

        // If it already exists, is executable, and version matches, just return path
        if (ffmpegFile.exists() && ffmpegFile.canExecute()) {
            val versionFile = File(context.filesDir, "ffmpeg_version")
            if (versionFile.exists() && versionFile.readText() == FFMPEG_VERSION) {
                return ffmpegFile.absolutePath
            }
        }

        // Otherwise, extract from assets
        val abi = getAbi()
        val assetPath = "ffmpeg/$abi/ffmpeg"
        
        try {
            context.assets.open(assetPath).use { input ->
                ffmpegFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to extract FFmpeg from assets ($assetPath): ${e.message}")
        }

        // Set permissions: Read + Execute
        ffmpegFile.setExecutable(true, false)
        ffmpegFile.setReadable(true, false)
        
        // Save version to avoid re-extracting every time
        File(context.filesDir, "ffmpeg_version").writeText(FFMPEG_VERSION)
        
        return ffmpegFile.absolutePath
    }

    private fun getAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("x86_64")    -> "x86_64"
            else -> throw Exception(
                "Unsupported device ABI: ${supportedAbis.joinToString()}. " +
                "Only arm64-v8a and x86_64 are supported."
            )
        }
    }
}
