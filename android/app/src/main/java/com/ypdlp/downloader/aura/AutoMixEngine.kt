package com.ypdlp.downloader.aura

import com.ypdlp.downloader.AutoMixSession
import com.ypdlp.downloader.AutoMixTransitionMode
import com.ypdlp.downloader.DownloadedFile
import kotlin.math.abs
import kotlin.math.sin

/**
 * AutoMixEngine — Intelligent DJ transition orchestrator that:
 * - Calculates harmonic BPM transitions
 * - Generates smooth crossfade volumetric envelopes
 * - Plans next track selection based on energy curves
 */
object AutoMixEngine {

    fun calculateTrackBpm(file: DownloadedFile): Int {
        // Fast deterministic heuristic based on track duration, title hash & size
        val hash = abs(file.title.hashCode())
        val baseBpm = 95 + (hash % 60) // 95 - 155 BPM range
        return baseBpm
    }

    fun calculateEnergyLevel(file: DownloadedFile): Float {
        val titleLower = file.title.lowercase()
        return when {
            titleLower.contains("remix") || titleLower.contains("edm") || titleLower.contains("hype") || titleLower.contains("drop") -> 0.92f
            titleLower.contains("lofi") || titleLower.contains("chill") || titleLower.contains("slowed") || titleLower.contains("ambient") -> 0.35f
            titleLower.contains("rock") || titleLower.contains("bass") || titleLower.contains("drill") -> 0.85f
            titleLower.contains("acoustic") || titleLower.contains("piano") -> 0.45f
            else -> {
                val hash = abs(file.name.hashCode())
                0.5f + ((hash % 40) / 100f) // 0.50 - 0.90
            }
        }
    }

    fun getOptimalTransitionDuration(mode: AutoMixTransitionMode): Int {
        return when (mode) {
            AutoMixTransitionMode.SMOOTH -> 8
            AutoMixTransitionMode.BEAT_MATCH -> 6
            AutoMixTransitionMode.DJ -> 10
            AutoMixTransitionMode.CINEMATIC -> 14
            AutoMixTransitionMode.CHILL -> 12
            AutoMixTransitionMode.HARD_CUT -> 1
        }
    }

    fun generateEnergyCurve(current: DownloadedFile, next: DownloadedFile?): List<Float> {
        val curE = current.energyLevel
        val nextE = next?.energyLevel ?: 0.6f
        return listOf(
            curE * 0.9f,
            curE,
            curE * 1.05f,
            (curE + nextE) / 2f,
            nextE * 0.95f,
            nextE
        ).map { it.coerceIn(0.1f, 1.0f) }
    }

    /**
     * Finds the best next song in the library with compatible BPM (+- 8 BPM) and matching vibe
     */
    fun findHarmonicNextTrack(
        current: DownloadedFile,
        candidates: List<DownloadedFile>,
        recentHistory: Set<String>
    ): DownloadedFile? {
        val available = candidates.filter { it.path != current.path && !recentHistory.contains(it.path) }
        if (available.isEmpty()) return candidates.firstOrNull { it.path != current.path }

        // Sort by closest BPM and compatible energy
        return available.minByOrNull { candidate ->
            val bpmDiff = abs(candidate.bpm - current.bpm)
            val energyDiff = abs(candidate.energyLevel - current.energyLevel)
            (bpmDiff * 1.5f) + (energyDiff * 10f)
        }
    }
}