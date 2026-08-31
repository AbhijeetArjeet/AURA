package com.ypdlp.downloader.aura

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.MainViewModel
import com.ypdlp.downloader.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

data class RhythmTile(
    val id: Long,
    val lane: Int, // 0, 1, 2, 3
    var yFrac: Float, // 0.0 (top) to 1.0 (hit line)
    var isHit: Boolean = false,
    var isMissed: Boolean = false
)

enum class GameState { SONG_SELECT, PLAYING, GAME_OVER, VICTORY }

@Composable
fun AuraRhythmTilesGame(
    playerState: PlayerState,
    files: List<DownloadedFile>,
    vm: MainViewModel,
    onClose: () -> Unit
) {
    var gameState by remember { mutableStateOf(GameState.SONG_SELECT) }
    var selectedTrack by remember { mutableStateOf<DownloadedFile?>(files.firstOrNull()) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var perfectCount by remember { mutableIntStateOf(0) }
    var greatCount by remember { mutableIntStateOf(0) }
    var missCount by remember { mutableIntStateOf(0) }
    var hitFeedback by remember { mutableStateOf<String?>(null) }

    val tiles = remember { mutableStateListOf<RhythmTile>() }
    var nextTileId by remember { mutableLongStateOf(0L) }

    // Game loop running at 60 FPS
    LaunchedEffect(gameState, playerState.isPlaying) {
        if (gameState == GameState.PLAYING) {
            val spawnRateMs = ((60000 / (selectedTrack?.bpm ?: 120))).coerceIn(300, 800).toLong()
            var lastSpawnTime = System.currentTimeMillis()

            while (isActive && gameState == GameState.PLAYING) {
                val now = System.currentTimeMillis()

                // Spawn new falling tile
                if (now - lastSpawnTime >= spawnRateMs) {
                    val lane = Random.nextInt(4)
                    tiles.add(RhythmTile(id = nextTileId++, lane = lane, yFrac = 0f))
                    lastSpawnTime = now
                }

                // Update falling tiles
                val speed = 0.018f
                val iterator = tiles.listIterator()
                while (iterator.hasNext()) {
                    val tile = iterator.next()
                    tile.yFrac += speed

                    // Miss check (passed bottom without tap)
                    if (tile.yFrac > 1.05f && !tile.isHit && !tile.isMissed) {
                        tile.isMissed = true
                        combo = 0
                        missCount++
                        hitFeedback = "MISS"
                    }
                }

                // Remove tiles off-screen
                tiles.removeAll { it.yFrac > 1.25f }

                // Check victory / song ended
                if (playerState.durationMs > 0 && playerState.currentPositionMs >= playerState.durationMs - 1000) {
                    gameState = GameState.VICTORY
                }

                delay(16) // ~60fps
            }
        }
    }

    // Lane tap handler
    fun onLaneTapped(lane: Int) {
        if (gameState != GameState.PLAYING) return

        // Find closest tile in this lane near the hit zone (0.75f - 1.05f)
        val targetTile = tiles.filter { it.lane == lane && !it.isHit && !it.isMissed }
            .minByOrNull { kotlin.math.abs(it.yFrac - 0.88f) }

        if (targetTile != null) {
            val dist = kotlin.math.abs(targetTile.yFrac - 0.88f)
            if (dist < 0.12f) { // PERFECT
                targetTile.isHit = true
                score += 300 + (combo * 10)
                combo++
                if (combo > maxCombo) maxCombo = combo
                perfectCount++
                hitFeedback = "PERFECT!"
            } else if (dist < 0.22f) { // GREAT
                targetTile.isHit = true
                score += 100 + (combo * 5)
                combo++
                if (combo > maxCombo) maxCombo = combo
                greatCount++
                hitFeedback = "GREAT!"
            } else { // EARLY / LATE MISS
                targetTile.isMissed = true
                combo = 0
                missCount++
                hitFeedback = "MISS"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (gameState) {
            GameState.SONG_SELECT -> {
                // ── Song Selection Screen ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text("🎮 AURA PIANO TILES", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        IconButton(onClick = { /* Help / OSU mode */ }) {
                            Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color(0xFF00E5FF))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Select a track to play with:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files) { file ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedTrack = file
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedTrack?.path == file.path) Color(0xFF1F293D) else Color(0xFF101320),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedTrack?.path == file.path) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E2235)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (file.artworkByteArray != null) {
                                            AsyncImage(
                                                model = file.artworkByteArray,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF00E5FF))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                                        Text("${file.artist} • ${file.bpm} BPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            selectedTrack?.let { track ->
                                score = 0
                                combo = 0
                                maxCombo = 0
                                perfectCount = 0
                                greatCount = 0
                                missCount = 0
                                tiles.clear()
                                vm.playMediaFile(track)
                                gameState = GameState.PLAYING
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A55)),
                        enabled = selectedTrack != null
                    ) {
                        Text("START GAME", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
                    }
                }
            }

            GameState.PLAYING -> {
                // ── Active Piano Tiles 4-Lane Canvas ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top HUD Score & Combo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            gameState = GameState.SONG_SELECT
                            vm.stopPlayback()
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$score", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                            if (combo > 1) {
                                Text("$combo COMBO!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF2A55))
                            }
                        }

                        hitFeedback?.let {
                            Text(
                                it,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (it == "PERFECT!") Color(0xFF00E5FF) else if (it == "GREAT!") Color(0xFF00FF66) else Color(0xFFFF3366)
                            )
                        }
                    }

                    // 4 Vertical Piano Lanes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 4 Lane Grid Background
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (laneIdx in 0..3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, Color.White.copy(alpha = 0.08f))
                                        .pointerInput(laneIdx) {
                                            detectTapGestures(onPress = { onLaneTapped(laneIdx) })
                                        }
                                )
                            }
                        }

                        // Target Hit Line (at 88% screen height)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.04f)
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 80.dp)
                                .background(Brush.verticalGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color.Transparent)))
                                .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f))
                        )

                        // Render Active Falling Tiles
                        tiles.forEach { tile ->
                            val laneWidthFrac = 0.25f
                            val laneOffsetFrac = tile.lane * laneWidthFrac

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(laneWidthFrac)
                                    .fillMaxHeight(0.18f)
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = (tile.lane * 92).dp, // Dynamic lane positioning
                                        y = (tile.yFrac * 580).dp
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (tile.isHit) Brush.verticalGradient(listOf(Color(0xFF00FF66).copy(alpha = 0.5f), Color(0xFF00FF66).copy(alpha = 0.2f)))
                                        else if (tile.isMissed) Brush.verticalGradient(listOf(Color(0xFFFF3366).copy(alpha = 0.6f), Color(0xFFFF3366).copy(alpha = 0.2f)))
                                        else Brush.verticalGradient(
                                            listOf(
                                                if (tile.lane % 2 == 0) Color(0xFFFF2A55) else Color(0xFF9D4EDD),
                                                if (tile.lane % 2 == 0) Color(0xFFFF6B2B) else Color(0xFF00E5FF)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    // Bottom 4 Touch Buttons for One-Hand / Two-Thumb play
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFF0B0D18))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (lane in 0..3) {
                            Button(
                                onClick = { onLaneTapped(lane) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (lane % 2 == 0) Color(0xFF1E293B) else Color(0xFF171E2E)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    when (lane) {
                                        0 -> "D"
                                        1 -> "F"
                                        2 -> "J"
                                        else -> "K"
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            GameState.VICTORY, GameState.GAME_OVER -> {
                // ── Game Results Screen (OSU / Magic Tiles style) ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (gameState == GameState.VICTORY) "STAGE CLEAR!" else "GAME OVER",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = if (gameState == GameState.VICTORY) Color(0xFF00FF66) else Color(0xFFFF2A55),
                        letterSpacing = 2.sp
                    )

                    Spacer(Modifier.height(24.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF101320),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Final Score: $score", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Max Combo: $maxCombo", fontSize = 15.sp, color = Color(0xFFFF2A55))
                            Text("Perfect: $perfectCount", fontSize = 14.sp, color = Color(0xFF00E5FF))
                            Text("Great: $greatCount", fontSize = 14.sp, color = Color(0xFF00FF66))
                            Text("Missed: $missCount", fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick = { gameState = GameState.SONG_SELECT },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("PLAY ANOTHER TRACK", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
