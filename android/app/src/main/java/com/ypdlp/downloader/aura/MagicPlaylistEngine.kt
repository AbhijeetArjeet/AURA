package com.ypdlp.downloader.aura

import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.MoodType
import com.ypdlp.downloader.SmartPlaylist
import java.util.UUID

object MagicPlaylistEngine {

    fun generateFromPrompt(prompt: String, library: List<DownloadedFile>): SmartPlaylist {
        val clean = prompt.trim().lowercase()
        var selectedTracks = mutableListOf<DownloadedFile>()
        var playlistTitle = "✨ Custom Session"
        var emoji = "✨"
        var colors = listOf(0xFF9D4EDD, 0xFF00E5FF)

        when {
            clean.contains("anime") || clean.contains("protagonist") || clean.contains("training") || clean.contains("otaku") -> {
                playlistTitle = "🌸 Anime Protagonist Awakening"
                emoji = "🌸"
                colors = listOf(0xFFFF2A55, 0xFF9D4EDD)
                selectedTracks.addAll(library.filter { 
                    it.title.contains("anime", true) || it.title.contains("ost", true) || it.energyLevel > 0.65f 
                })
            }
            clean.contains("2 am") || clean.contains("late night") || clean.contains("alone") || clean.contains("midnight") -> {
                playlistTitle = "🌙 Walking Alone at 2 AM"
                emoji = "🌙"
                colors = listOf(0xFF0F172A, 0xFF38BDF8)
                selectedTracks.addAll(library.filter { it.energyLevel < 0.60f || it.title.contains("night", true) })
            }
            clean.contains("code") || clean.contains("coding") || clean.contains("focus") || clean.contains("study") -> {
                playlistTitle = "🧠 Deep Synthesis & Flow State"
                emoji = "🧠"
                colors = listOf(0xFF6366F1, 0xFF10B981)
                selectedTracks.addAll(library.filter { it.energyLevel in 0.4f..0.8f })
            }
            clean.contains("gym") || clean.contains("workout") || clean.contains("hype") || clean.contains("energy") -> {
                playlistTitle = "⚡ Adrenaline Overdrive"
                emoji = "⚡"
                colors = listOf(0xFFEF4444, 0xFFF59E0B)
                selectedTracks.addAll(library.filter { it.energyLevel > 0.70f || it.bpm > 125 })
            }
            clean.contains("chill") || clean.contains("relax") || clean.contains("cozy") -> {
                playlistTitle = "😌 Mellow Afternoon Breeze"
                emoji = "😌"
                colors = listOf(0xFF34D399, 0xFF06B6D4)
                selectedTracks.addAll(library.filter { it.energyLevel < 0.55f })
            }
            else -> {
                playlistTitle = "✨ $prompt"
                selectedTracks.addAll(library.shuffled())
            }
        }

        if (selectedTracks.isEmpty()) {
            selectedTracks = library.shuffled().take(15).toMutableList()
        }

        return SmartPlaylist(
            id = UUID.randomUUID().toString(),
            title = playlistTitle,
            description = "AI generated from prompt: \"$prompt\"",
            emoji = emoji,
            gradientColors = colors,
            tracks = selectedTracks.distinctBy { it.path }
        )
    }

    fun generateMoodPlaylist(mood: MoodType, library: List<DownloadedFile>): SmartPlaylist {
        val tracks = when (mood) {
            MoodType.MIDNIGHT -> library.filter { it.energyLevel < 0.6f || it.bpm < 120 }
            MoodType.RAINY -> library.filter { it.title.contains("slowed", true) || it.energyLevel < 0.5f }
            MoodType.HYPE -> library.filter { it.energyLevel > 0.75f || it.bpm > 130 }
            MoodType.ROMANTIC -> library.filter { it.energyLevel in 0.4f..0.7f }
            MoodType.CHILL -> library.filter { it.energyLevel < 0.55f }
            MoodType.FOCUS -> library.filter { !it.title.contains("vocal", true) && it.energyLevel in 0.4f..0.75f }
            MoodType.GYM -> library.filter { it.energyLevel > 0.8f || it.bpm > 128 }
            MoodType.COZY -> library.filter { it.title.contains("anime", true) || it.title.contains("ost", true) || it.energyLevel < 0.65f }
            MoodType.GAMING -> library.filter { it.energyLevel > 0.6f }
        }.ifEmpty { library.shuffled().take(12) }

        val colorLong = java.lang.Long.parseLong(mood.colorHex.removePrefix("#"), 16) or 0xFF000000

        return SmartPlaylist(
            id = mood.name,
            title = mood.title,
            description = mood.subtitle,
            emoji = mood.emoji,
            gradientColors = listOf(colorLong, 0xFF12141E),
            tracks = tracks
        )
    }
}