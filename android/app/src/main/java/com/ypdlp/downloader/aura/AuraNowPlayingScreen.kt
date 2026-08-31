package com.ypdlp.downloader.aura

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.MainViewModel
import com.ypdlp.downloader.PlayerState
import com.ypdlp.downloader.VisualizerMode

@Composable
fun AuraNowPlayingScreen(
    playerState: PlayerState,
    vm: MainViewModel,
    onClose: () -> Unit
) {
    val currentFile = playerState.currentFile ?: return
    val primaryColor = remember(currentFile.dominantColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentFile.dominantColorHex))
        } catch (e: Exception) {
            Color(0xFFFF2A55)
        }
    }
    val secondaryColor = remember(currentFile.secondaryColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentFile.secondaryColorHex))
        } catch (e: Exception) {
            Color(0xFF00E5FF)
        }
    }

    var sliderPos by remember { mutableFloatStateOf(0f) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    LaunchedEffect(playerState.currentPositionMs) {
        if (!isDraggingSlider && playerState.durationMs > 0) {
            sliderPos = playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.45f),
                        secondaryColor.copy(alpha = 0.20f),
                        Color(0xFF080911)
                    )
                )
            )
    ) {
        // Atmospheric Visualizer in Background
        AuraVisualizerView(
            mode = playerState.visualizerMode,
            bands = playerState.visualizerData,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Navigation Bar ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM AURA LIBRARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        currentFile.album,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                IconButton(onClick = {
                    val modes = VisualizerMode.values()
                    val nextIdx = (modes.indexOf(playerState.visualizerMode) + 1) % modes.size
                    vm.setVisualizerMode(modes[nextIdx])
                }) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = "Visualizer Mode", tint = secondaryColor)
                }
            }

            // ── Artwork Hero Area with Glow ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(32.dp, RoundedCornerShape(28.dp), ambientColor = primaryColor, spotColor = secondaryColor)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141724))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = primaryColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(96.dp)
                )

                // Otaku floating quotes
                if (playerState.isOtakuMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.6f))
                    ) {
                        Text(
                            "「本物が欲しい」",
                            color = Color(0xFFF472B6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Title & Artist Metadata ─────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentFile.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${currentFile.artist} • ${currentFile.bpm} BPM",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.toggleFavorite(currentFile) }) {
                        val isFav = currentFile.path in vm.ui.value.favorites
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFFF2A55) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ── Scrubbing Slider & Timestamps ───────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPos,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderPos = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        vm.seekTo((sliderPos * playerState.durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = (sliderPos * playerState.durationMs / 1000).toLong()
                    val durSec = playerState.durationMs / 1000
                    Text("%02d:%02d".format(posSec / 60, posSec % 60), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("%02d:%02d".format(durSec / 60, durSec % 60), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            // ── Main DJ Playback Transport Controls ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AutoMix Toggle Chip
                IconButton(onClick = vm::toggleAutoMix) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AutoMix",
                        tint = if (vm.ui.value.autoMixSession.isEnabled) primaryColor else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Previous Track
                IconButton(onClick = { vm.seekTo(0L) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
                }

                // Play / Pause Master Orb
                Surface(
                    onClick = vm::togglePlayback,
                    shape = CircleShape,
                    color = primaryColor,
                    shadowElevation = 14.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Next Track / AutoMix Blend
                IconButton(onClick = {
                    vm.playNextInQueue()
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                }

                // Video Mode Switch (if file is a video)
                if (currentFile.isVideo) {
                    IconButton(onClick = { vm.openVideoPlayer(currentFile) }) {
                        Icon(Icons.Filled.Movie, contentDescription = "Watch Video", tint = secondaryColor)
                    }
                } else {
                    IconButton(onClick = { vm.toggleOtakuMode() }) {
                        Icon(Icons.Filled.Spa, contentDescription = "Otaku Mode", tint = if (vm.ui.value.isOtakuMode) Color(0xFFF472B6) else Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}