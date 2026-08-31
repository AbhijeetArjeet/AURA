package com.ypdlp.downloader.aura

import com.ypdlp.downloader.AiDjCommentary
import com.ypdlp.downloader.DownloadedFile
import java.util.Calendar

object AiDjService {

    fun generateDjSession(library: List<DownloadedFile>): Pair<AiDjCommentary, List<DownloadedFile>> {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val (commentary, filteredTracks) = when (hour) {
            in 5..11 -> {
                AiDjCommentary(
                    text = "Good morning. Starting your day with crisp acoustic melodies and rising energy.",
                    mood = "Morning Awakening"
                ) to library.sortedByDescending { it.energyLevel }
            }
            in 12..17 -> {
                AiDjCommentary(
                    text = "Good afternoon. Keeping the momentum going with punchy beats and focused rhythms.",
                    mood = "Afternoon Flow"
                ) to library.filter { it.energyLevel in 0.5f..0.85f }
            }
            in 18..22 -> {
                AiDjCommentary(
                    text = "Good evening. Unwinding from the day with atmospheric synthwave and smooth transitions.",
                    mood = "Evening Sunset"
                ) to library.filter { it.energyLevel in 0.4f..0.7f }
            }
            else -> {
                AiDjCommentary(
                    text = "You're listening late tonight. I'm keeping things deep, atmospheric and cinematic.",
                    mood = "2 AM Midnight Atmosphere"
                ) to library.filter { it.energyLevel < 0.6f || it.bpm < 120 }
            }
        }

        val queue = filteredTracks.ifEmpty { library }.shuffled()
        return commentary to queue
    }

    fun getOtakuDjLine(trackTitle: String): String {
        val lines = listOf(
            "「本物が欲しい」— Playing $trackTitle. Let the world fade away.",
            "Youth is a lie, but this beat is absolute. Spinning $trackTitle.",
            "Service club standby. $trackTitle queued up for genuine listeners.",
            "Shizuka-sensei would approve of this track: $trackTitle.",
            "Stepping into the anime realm. Immerse yourself in $trackTitle."
        )
        return lines.random()
    }
}