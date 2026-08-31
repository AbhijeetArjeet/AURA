package com.ypdlp.downloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YPDlpApp : Application() {

    companion object {
        var isStandaloneEngineReady: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize 100% on-device standalone engine (yt-dlp + ffmpeg)
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            isStandaloneEngineReady = true
            Log.d("YPDlpApp", "Standalone on-device YoutubeDL and FFmpeg initialized successfully.")

            // Check for yt-dlp updates in background to keep extractor working forever
            Thread {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this)
                    Log.d("YPDlpApp", "yt-dlp binary updated to latest release.")
                } catch (e: Exception) {
                    Log.d("YPDlpApp", "yt-dlp background update skipped/failed: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e("YPDlpApp", "Failed to initialize embedded on-device YoutubeDL/FFmpeg: ${e.message}")
            isStandaloneEngineReady = false
        }
    }
}
