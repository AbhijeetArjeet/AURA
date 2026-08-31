package com.ypdlp.downloader.aura

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.ypdlp.downloader.DownloadedFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.math.sin

class AuraPlaybackEngine(private val context: Context) {

    companion object {
        private const val TAG = "AuraPlaybackEngine"
    }

    private var primaryPlayer: MediaPlayer? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var visualizerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _visualizerBands = MutableStateFlow(FloatArray(32) { 0f })
    val visualizerBands = _visualizerBands.asStateFlow()

    private val _sleepMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepMinutesLeft = _sleepMinutesLeft.asStateFlow()

    var onSongFinished: (() -> Unit)? = null
    var onAutoMixTriggered: (() -> Unit)? = null

    var crossfadeDurationSec: Int = 8
    var isAutoMixEnabled: Boolean = false

    fun playFile(file: DownloadedFile, startPositionMs: Long = 0L) {
        try {
            stopVisualizerLoop()
            primaryPlayer?.release()
            releaseAudioEffects()

            primaryPlayer = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file.file))
                prepare()
                if (startPositionMs > 0 && startPositionMs < duration) {
                    seekTo(startPositionMs.toInt())
                }
                start()

                initAudioEffects(audioSessionId)

                setOnCompletionListener {
                    _isPlaying.value = false
                    onSongFinished?.invoke()
                }
            }

            _duration.value = primaryPlayer?.duration?.toLong() ?: 0L
            _isPlaying.value = true

            startProgressAndAutoMixMonitor()
            startVisualizerLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing file: ${e.message}", e)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val player = primaryPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
    }

    fun pause() {
        primaryPlayer?.takeIf { it.isPlaying }?.let {
            it.pause()
            _isPlaying.value = false
        }
    }

    fun resume() {
        primaryPlayer?.takeIf { !it.isPlaying }?.let {
            it.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(posMs: Long) {
        primaryPlayer?.seekTo(posMs.toInt())
        _currentPosition.value = posMs
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepMinutesLeft.value = minutes

        if (minutes != null && minutes > 0) {
            sleepTimerJob = scope.launch {
                var remaining = minutes
                while (remaining > 0 && isActive) {
                    delay(60_000L)
                    remaining--
                    _sleepMinutesLeft.value = remaining
                }
                pause()
                _sleepMinutesLeft.value = null
            }
        }
    }

    // ── Audio Effects ────────────────────────────────────────────────────────

    private fun initAudioEffects(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(500)
            }
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(300)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioEffects note: ${e.message}")
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
        } catch (e: Exception) {}
    }

    fun setEqBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {}
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            equalizer = null
            bassBoost = null
            virtualizer = null
        } catch (e: Exception) {}
    }

    // ── Monitoring ───────────────────────────────────────────────────────────

    private fun startProgressAndAutoMixMonitor() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var autoMixTriggeredForThisTrack = false

            while (isActive && primaryPlayer != null) {
                try {
                    val pos = primaryPlayer?.currentPosition?.toLong() ?: 0L
                    val dur = primaryPlayer?.duration?.toLong() ?: 0L
                    _currentPosition.value = pos

                    if (isAutoMixEnabled && !autoMixTriggeredForThisTrack && dur > 15_000L) {
                        val triggerThresholdMs = dur - (crossfadeDurationSec * 1000L)
                        if (pos >= triggerThresholdMs) {
                            autoMixTriggeredForThisTrack = true
                            onAutoMixTriggered?.invoke()
                        }
                    }
                } catch (e: Exception) {}
                delay(200L)
            }
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var phase = 0f
            while (isActive) {
                if (_isPlaying.value) {
                    phase += 0.15f
                    val bands = FloatArray(32) { i ->
                        val base = (sin(phase + i * 0.4f) * 0.5f + 0.5f).toFloat()
                        val noise = (Math.random() * 0.35f).toFloat()
                        (base * 0.7f + noise).coerceIn(0.05f, 1.0f)
                    }
                    _visualizerBands.value = bands
                } else {
                    _visualizerBands.value = FloatArray(32) { 0f }
                }
                delay(33L)
            }
        }
    }

    private fun stopVisualizerLoop() {
        visualizerJob?.cancel()
        _visualizerBands.value = FloatArray(32) { 0f }
    }

    fun release() {
        progressJob?.cancel()
        visualizerJob?.cancel()
        sleepTimerJob?.cancel()
        primaryPlayer?.release()
        releaseAudioEffects()
        primaryPlayer = null
        _isPlaying.value = false
    }
}