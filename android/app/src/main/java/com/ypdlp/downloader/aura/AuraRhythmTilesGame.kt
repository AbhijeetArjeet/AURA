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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class RhythmGameMode { PIANO_TILES, OSU_CIRCLES }
enum class GameState { SONG_SELECT, PLAYING, GAME_OVER, VICTORY }
enum class GameDifficulty(val label: String, val speed: Float, val spawnIntervalMult: Float, val color: Color) {
    EASY("EASY", 0.013f, 1.3f, Color(0xFF00FF66)),
    NORMAL("NORMAL", 0.018f, 1.0f, Color(0xFF00E5FF)),
    HARD("HARD", 0.024f, 0.75f, Color(0xFFFF9E00)),
    INSANE("INSANE (OSU)", 0.032f, 0.55f, Color(0xFFFF2A55))
}

data class RhythmTileItem(
    val id: Long,
    val lane: Int, // 0, 1, 2, 3
    var yFrac: Float, // 0.0 to 1.0
    val isHold: Boolean = false,
    val lengthFrac: Float = 0.2f,
    var isHit: Boolean = false,
    var isMissed: Boolean = false
)

data class OsuCircleItem(
    val id: Long,
    val xFrac: Float, // 0.1 to 0.9
    val yFrac: Float, // 0.15 to 0.75
    val number: Int,
    var approachScale: Float = 2.5f, // Shrinks from 2.5 to 1.0
    var isHit: Boolean = false,
    var isMissed: Boolean = false
)

@Composable
fun AuraRhythmTilesGame(
    playerState: PlayerState,
    files: List<DownloadedFile>,
    vm: MainViewModel,
    onClose: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(RhythmGameMode.PIANO_TILES) }
    var selectedDifficulty by remember { mutableStateOf(GameDifficulty.NORMAL) }
    var gameState by remember { mutableStateOf(GameState.SONG_SELECT) }
    var selectedTrack by remember { mutableStateOf<DownloadedFile?>(playerState.currentFile ?: files.firstOrNull()) }

    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var perfectCount by remember { mutableIntStateOf(0) }
    var greatCount by remember { mutableIntStateOf(0) }
    var missCount by remember { mutableIntStateOf(0) }
    var hitFeedback by remember { mutableStateOf<String?>(null) }
    var hitFeedbackColor by remember { mutableStateOf(Color.White) }

    val tiles = remember { mutableStateListOf<RhythmTileItem>() }
    val osuCircles = remember { mutableStateListOf<OsuCircleItem>() }
    var nextItemId by remember { mutableLongStateOf(0L) }
    var osuNumberCounter by remember { mutableIntStateOf(1) }

    // Pulsing audio visualizer / beat pulse
    val visualizerBands = remember(playerState.currentPositionMs) {
        FloatArray(16) { Random.nextFloat() * 0.8f }
    }
    val bassEnergy = remember(playerState.currentPositionMs) {
        Random.nextFloat() * 0.6f + 0.4f
    }

    // High precision game loop (~60fps)
    LaunchedEffect(gameState, selectedDifficulty, selectedMode) {
        if (gameState == GameState.PLAYING) {
            val baseBpm = (selectedTrack?.bpm ?: 120).coerceIn(80, 200)
            val beatIntervalMs = ((60000f / baseBpm) * selectedDifficulty.spawnIntervalMult).toLong().coerceIn(240, 900)
            var lastSpawnTime = System.currentTimeMillis()

            while (isActive && gameState == GameState.PLAYING) {
                val now = System.currentTimeMillis()

                if (selectedMode == RhythmGameMode.PIANO_TILES) {
                    // Spawn Piano Tiles
                    if (now - lastSpawnTime >= beatIntervalMs) {
                        val lane = Random.nextInt(4)
                        val isHold = Random.nextFloat() < 0.2f // 20% chance for long hold tiles
                        tiles.add(
                            RhythmTileItem(
                                id = nextItemId++,
                                lane = lane,
                                yFrac = 0f,
                                isHold = isHold,
                                lengthFrac = if (isHold) 0.28f else 0.16f
                            )
                        )
                        lastSpawnTime = now
                    }

                    // Move tiles down
                    val iter = tiles.listIterator()
                    while (iter.hasNext()) {
                        val tile = iter.next()
                        tile.yFrac += selectedDifficulty.speed

                        if (tile.yFrac > 1.06f && !tile.isHit && !tile.isMissed) {
                            tile.isMissed = true
                            combo = 0
                            missCount++
                            hitFeedback = "MISS"
                            hitFeedbackColor = Color(0xFFFF2A55)
                        }
                    }
                    tiles.removeAll { it.yFrac > 1.3f }

                } else {
                    // Spawn OSU Hit Circles
                    if (now - lastSpawnTime >= beatIntervalMs) {
                        osuCircles.add(
                            OsuCircleItem(
                                id = nextItemId++,
                                xFrac = Random.nextFloat() * 0.7f + 0.15f,
                                yFrac = Random.nextFloat() * 0.55f + 0.18f,
                                number = osuNumberCounter++
                            )
                        )
                        if (osuNumberCounter > 9) osuNumberCounter = 1
                        lastSpawnTime = now
                    }

                    // Shrink approach circles
                    val iter = osuCircles.listIterator()
                    while (iter.hasNext()) {
                        val circle = iter.next()
                        circle.approachScale -= selectedDifficulty.speed * 1.6f

                        if (circle.approachScale < 0.85f && !circle.isHit && !circle.isMissed) {
                            circle.isMissed = true
                            combo = 0
                            missCount++
                            hitFeedback = "MISS"
                            hitFeedbackColor = Color(0xFFFF2A55)
                        }
                    }
                    osuCircles.removeAll { it.approachScale < 0.7f }
                }

                // Stage completion check
                if (playerState.durationMs > 0 && playerState.currentPositionMs >= playerState.durationMs - 1500) {
                    gameState = GameState.VICTORY
                }

                delay(16)
            }
        }
    }

    // Piano Tiles Tap
    fun onLaneTapped(lane: Int) {
        if (gameState != GameState.PLAYING) return
        val targetTile = tiles.filter { it.lane == lane && !it.isHit && !it.isMissed }
            .minByOrNull { abs(it.yFrac - 0.88f) }

        if (targetTile != null) {
            val dist = abs(targetTile.yFrac - 0.88f)
            if (dist < 0.11f) {
                targetTile.isHit = true
                score += (300 * selectedDifficulty.spawnIntervalMult.let { (2.0f - it) }).toInt() + (combo * 15)
                combo++
                if (combo > maxCombo) maxCombo = combo
                perfectCount++
                hitFeedback = "PERFECT 300!"
                hitFeedbackColor = Color(0xFF00E5FF)
            } else if (dist < 0.22f) {
                targetTile.isHit = true
                score += (100 * selectedDifficulty.spawnIntervalMult.let { (2.0f - it) }).toInt() + (combo * 8)
                combo++
                if (combo > maxCombo) maxCombo = combo
                greatCount++
                hitFeedback = "GREAT 100!"
                hitFeedbackColor = Color(0xFF00FF66)
            } else {
                targetTile.isMissed = true
                combo = 0
                missCount++
                hitFeedback = "MISS"
                hitFeedbackColor = Color(0xFFFF2A55)
            }
        }
    }

    // OSU Circle Tap
    fun onOsuCircleTapped(circle: OsuCircleItem) {
        if (gameState != GameState.PLAYING || circle.isHit || circle.isMissed) return
        val diff = abs(circle.approachScale - 1.0f)

        if (diff < 0.35f) {
            circle.isHit = true
            score += 300 + (combo * 20)
            combo++
            if (combo > maxCombo) maxCombo = combo
            perfectCount++
            hitFeedback = "OSU! 300"
            hitFeedbackColor = Color(0xFFFF2A55)
        } else if (diff < 0.65f) {
            circle.isHit = true
            score += 100 + (combo * 10)
            combo++
            if (combo > maxCombo) maxCombo = combo
            greatCount++
            hitFeedback = "100"
            hitFeedbackColor = Color(0xFF00FF66)
        } else {
            circle.isMissed = true
            combo = 0
            missCount++
            hitFeedback = "50 / MISS"
            hitFeedbackColor = Color(0xFFFF9E00)
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
                // ── AAA Modern Song & Mode Selection Screen ──
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
                        Text("🎮 RHYTHM ARCADE", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White, letterSpacing = 2.sp)
                        IconButton(onClick = { /* Help */ }) {
                            Icon(Icons.Filled.HelpOutline, null, tint = Color(0xFF00E5FF))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Mode Switcher (Piano Tiles vs OSU!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF101320))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedMode == RhythmGameMode.PIANO_TILES) Color(0xFFFF2A55) else Color.Transparent)
                                .clickable { selectedMode = RhythmGameMode.PIANO_TILES },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎹 PIANO TILES 3", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedMode == RhythmGameMode.OSU_CIRCLES) Color(0xFF9D4EDD) else Color.Transparent)
                                .clickable { selectedMode = RhythmGameMode.OSU_CIRCLES },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⭕ OSU! CIRCLES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Difficulty selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GameDifficulty.values().forEach { diff ->
                            FilterChip(
                                selected = selectedDifficulty == diff,
                                onClick = { selectedDifficulty = diff },
                                label = { Text(diff.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = diff.color,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Select Any Local / Downloaded Track:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                    .clickable { selectedTrack = file },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedTrack?.path == file.path) Color(0xFF1E2638) else Color(0xFF101320),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedTrack?.path == file.path) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.06f)
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
                                        Text(file.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${file.artist} • ${file.bpm} BPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

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
                                osuCircles.clear()
                                vm.playMediaFile(track)
                                gameState = GameState.PLAYING
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == RhythmGameMode.PIANO_TILES) Color(0xFFFF2A55) else Color(0xFF9D4EDD)
                        ),
                        enabled = selectedTrack != null
                    ) {
                        Text("START ${selectedMode.name.replace("_", " ")}", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp)
                    }
                }
            }

            GameState.PLAYING -> {
                // ── Active Rhythm Gameplay Engine ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top HUD Score & Combo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
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
                            Text("$score", fontWeight = FontWeight.Black, fontSize = 30.sp, color = Color.White)
                            if (combo > 1) {
                                Text("$combo COMBO 🔥", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFFF2A55))
                            }
                        }

                        hitFeedback?.let {
                            Text(
                                it,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = hitFeedbackColor
                            )
                        }
                    }

                    if (selectedMode == RhythmGameMode.PIANO_TILES) {
                        // ── Piano Tiles 3 4-Lane Canvas ──
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // 4 Lanes
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

                            // Glowing Hit Line with Bass Pulse
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((6 + (bassEnergy * 8)).dp)
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 75.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF00E5FF).copy(alpha = 0.3f))
                                        )
                                    )
                                    .border(1.5.dp, Color(0xFF00E5FF))
                            )

                            // Falling Piano Tiles
                            tiles.forEach { tile ->
                                val laneWidthFrac = 0.25f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(laneWidthFrac)
                                        .fillMaxHeight(tile.lengthFrac)
                                        .align(Alignment.TopStart)
                                        .offset(
                                            x = (tile.lane * 92).dp,
                                            y = (tile.yFrac * 580).dp
                                        )
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (tile.isHit) Brush.verticalGradient(listOf(Color(0xFF00FF66), Color(0xFF00FF66).copy(alpha = 0.3f)))
                                            else if (tile.isMissed) Brush.verticalGradient(listOf(Color(0xFFFF2A55), Color(0xFFFF2A55).copy(alpha = 0.3f)))
                                            else Brush.verticalGradient(
                                                listOf(
                                                    if (tile.lane % 2 == 0) Color(0xFFFF2A55) else Color(0xFF9D4EDD),
                                                    if (tile.lane % 2 == 0) Color(0xFFFF6B2B) else Color(0xFF00E5FF)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                )
                            }
                        }

                        // Bottom 4 tactile thumb keys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
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
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
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
                    } else {
                        // ── OSU! Circular Hit Engine ──
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF090A12))
                        ) {
                            osuCircles.forEach { circle ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(
                                            x = (circle.xFrac * 320).dp,
                                            y = (circle.yFrac * 500).dp
                                        )
                                        .size(76.dp)
                                        .clickable { onOsuCircleTapped(circle) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Approach Circle Ring (Shrinking to 1.0 scale)
                                    Box(
                                        modifier = Modifier
                                            .size((76 * circle.approachScale).dp)
                                            .border(2.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), CircleShape)
                                    )

                                    // Main Hit Circle
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (circle.isHit) Brush.radialGradient(listOf(Color(0xFF00FF66), Color(0xFF00FF66).copy(alpha = 0.4f)))
                                                else if (circle.isMissed) Brush.radialGradient(listOf(Color(0xFFFF2A55), Color(0xFFFF2A55).copy(alpha = 0.4f)))
                                                else Brush.radialGradient(
                                                    listOf(Color(0xFFFF2A55), Color(0xFF9D4EDD))
                                                )
                                            )
                                            .border(2.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${circle.number}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 22.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GameState.VICTORY, GameState.GAME_OVER -> {
                // ── Stage Clear Summary ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (gameState == GameState.VICTORY) "🏆 STAGE CLEAR!" else "GAME OVER",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = if (gameState == GameState.VICTORY) Color(0xFF00FF66) else Color(0xFFFF2A55),
                        letterSpacing = 2.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF101320),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Mode: ${selectedMode.name.replace("_", " ")} (${selectedDifficulty.label})", fontSize = 13.sp, color = Color(0xFF00E5FF))
                            Spacer(Modifier.height(6.dp))
                            Text("Final Score: $score", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                            Spacer(Modifier.height(10.dp))
                            Text("Max Combo: $maxCombo", fontSize = 15.sp, color = Color(0xFFFF2A55), fontWeight = FontWeight.Bold)
                            Text("Perfect 300: $perfectCount", fontSize = 14.sp, color = Color(0xFF00E5FF))
                            Text("Great 100: $greatCount", fontSize = 14.sp, color = Color(0xFF00FF66))
                            Text("Missed: $missCount", fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { gameState = GameState.SONG_SELECT },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("PLAY AGAIN", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
