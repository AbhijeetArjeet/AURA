package com.ypdlp.downloader

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YPDlpApp : Application() {

    companion object {
        @Volatile
        var isStandaloneEngineReady: Boolean = false
            private set

        @Synchronized
        fun ensureInitialized(context: Context): Boolean {
            if (isStandaloneEngineReady) return true
            return try {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                try {
                    FFmpeg.getInstance().init(app)
                } catch (fe: Exception) {
                    Log.w("YPDlpApp", "FFmpeg init warning (non-fatal): ${fe.message}")
                }
                isStandaloneEngineReady = true
                Log.d("YPDlpApp", "YoutubeDL engine initialized successfully.")
                true
            } catch (e: Exception) {
                Log.e("YPDlpApp", "Failed to initialize YoutubeDL: ${e.message}", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureInitialized(this)
    }
}
