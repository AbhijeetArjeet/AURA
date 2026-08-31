package com.ypdlp.downloader.aura

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.MainViewModel
import com.ypdlp.downloader.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// 🎮 All Supported Mini-Game Types
enum class ArcadeGameMode(val title: String, val subtitle: String, val icon: String, val color: Color) {
    HIGHWAY_3D("🚀 3D Highway (Cytus/Deemo)", "3D Perspective, hit sparks & vibration", "3D", Color(0xFF00E5FF)),
    PIANO_TILES_CLASSIC("🎹 Magic Piano Tiles 3", "4 Classic falling vertical lanes", "2D", Color(0xFFFF2A55)),
    OSU_CYBERPUNK("⭕ OSU! Cyberpunk", "Numbered targets with shrinking rings", "OSU", Color(0xFF9D4EDD)),
    RETRO_SNAKE_BEAT("🐍 Rhythm Snake", "Snake grows with song bass drops", "RETRO", Color(0xFF00FF66)),
    CYBER_PONG("🏓 Audio Cyber-Pong", "Ball accelerates to the song beat", "PONG", Color(0xFFFF9E00)),
    NEON_SPACE_DODGE("🛸 Neon Space Dodger", "Dodge audio asteroid waves", "SPACE", Color(0xFFFF007F))
}

enum class GameDifficulty(val label: String, val speed: Float, val spawnIntervalMult: Float, val color: Color) {
    EASY("EASY", 0.012f, 1.3f, Color(0xFF00FF66)),
    NORMAL("NORMAL", 0.018f, 1.0f, Color(0xFF00E5FF)),
    HARD("HARD", 0.025f, 0.75f, Color(0xFFFF9E00)),
    INSANE("INSANE", 0.033f, 0.55f, Color(0xFFFF2A55))
}

enum class ArcadeGameState { SONG_SELECT, PLAYING, VICTORY, GAME_OVER }

data class Tile3D(
    val id: Long,
    val lane: Int,
    var z: Float,
    val isHold: Boolean = false,
    val lengthZ: Float = 0.2f,
    var isHit: Boolean = false,
    var isMissed: Boolean = false
)

data class ParticleSpark(
    val id: Long,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    var life: Float = 1.0f,
    val size: Float = 7f
)

data class OsuTarget(
    val id: Long,
    val xFrac: Float,
    val yFrac: Float,
    val number: Int,
    var approachScale: Float = 2.5f,
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
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(ArcadeGameMode.HIGHWAY_3D) }
    var selectedDifficulty by remember { mutableStateOf(GameDifficulty.NORMAL) }
    var gameState by remember { mutableStateOf(ArcadeGameState.SONG_SELECT) }
    var selectedTrack by remember { mutableStateOf<DownloadedFile?>(playerState.currentFile ?: files.firstOrNull()) }

    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var perfectCount by remember { mutableIntStateOf(0) }
    var greatCount by remember { mutableIntStateOf(0) }
    var missCount by remember { mutableIntStateOf(0) }
    var hitFeedback by remember { mutableStateOf<String?>(null) }
    var hitFeedbackColor by remember { mutableStateOf(Color.White) }

    // 3D / 2D Tiles & Entities
    val tiles3D = remember { mutableStateListOf<Tile3D>() }
    val osuTargets = remember { mutableStateListOf<OsuTarget>() }
    val particles = remember { mutableStateListOf<ParticleSpark>() }
    var nextItemId by remember { mutableLongStateOf(0L) }
    var osuNumberCounter by remember { mutableIntStateOf(1) }

    // 🐍 Retro Snake State
    val snakeBody = remember { mutableStateListOf(Offset(5f, 5f), Offset(5f, 6f), Offset(5f, 7f)) }
    var snakeDir by remember { mutableStateOf(Offset(0f, -1f)) }
    var foodPos by remember { mutableStateOf(Offset(8f, 8f)) }

    // 🏓 Cyber Pong State
    var paddleX by remember { mutableFloatStateOf(0.5f) }
    var ballPos by remember { mutableStateOf(Offset(0.5f, 0.4f)) }
    var ballVel by remember { mutableStateOf(Offset(0.012f, 0.015f)) }

    // 🛸 Space Dodger State
    var playerShipX by remember { mutableFloatStateOf(0.5f) }
    val asteroids = remember { mutableStateListOf<Offset>() }

    // Haptic vibration feedback
    fun triggerHaptic(durationMs: Long = 25) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // Spark Particles Burst
    fun spawnSparks(x: Float, y: Float, count: Int = 20, color: Color = Color(0xFF00E5FF)) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 14f + 4f
            particles.add(
                ParticleSpark(
                    id = nextItemId++,
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = if (i % 2 == 0) color else Color.White,
                    life = 1.0f,
                    size = Random.nextFloat() * 7f + 3f
                )
            )
        }
    }

    // Master 60FPS Arcade Game Loop
    LaunchedEffect(gameState, selectedDifficulty, selectedMode) {
        if (gameState == ArcadeGameState.PLAYING) {
            val baseBpm = (selectedTrack?.bpm ?: 120).coerceIn(80, 220)
            val beatIntervalMs = ((60000f / baseBpm) * selectedDifficulty.spawnIntervalMult).toLong().coerceIn(180, 850)
            var lastSpawnTime = System.currentTimeMillis()

            while (isActive && gameState == ArcadeGameState.PLAYING) {
                val now = System.currentTimeMillis()

                // Update particles
                val pIter = particles.listIterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.life -= 0.048f
                    if (p.life <= 0f) pIter.remove()
                }

                when (selectedMode) {
                    ArcadeGameMode.HIGHWAY_3D, ArcadeGameMode.PIANO_TILES_CLASSIC -> {
                        if (now - lastSpawnTime >= beatIntervalMs) {
                            val lane = Random.nextInt(4)
                            val isHold = Random.nextFloat() < 0.2f
                            tiles3D.add(Tile3D(id = nextItemId++, lane = lane, z = 0f, isHold = isHold))
                            lastSpawnTime = now
                        }

                        val iter = tiles3D.listIterator()
                        while (iter.hasNext()) {
                            val tile = iter.next()
                            tile.z += selectedDifficulty.speed

                            if (tile.z > 1.05f && !tile.isHit && !tile.isMissed) {
                                tile.isMissed = true
                                combo = 0
                                missCount++
                                hitFeedback = "MISS"
                                hitFeedbackColor = Color(0xFFFF2A55)
                            }
                        }
                        tiles3D.removeAll { it.z > 1.3f }
                    }

                    ArcadeGameMode.OSU_CYBERPUNK -> {
                        if (now - lastSpawnTime >= beatIntervalMs) {
                            osuTargets.add(
                                OsuTarget(
                                    id = nextItemId++,
                                    xFrac = Random.nextFloat() * 0.65f + 0.18f,
                                    yFrac = Random.nextFloat() * 0.48f + 0.22f,
                                    number = osuNumberCounter++
                                )
                            )
                            if (osuNumberCounter > 9) osuNumberCounter = 1
                            lastSpawnTime = now
                        }

                        val iter = osuTargets.listIterator()
                        while (iter.hasNext()) {
                            val target = iter.next()
                            target.approachScale -= selectedDifficulty.speed * 1.55f
                            if (target.approachScale < 0.85f && !target.isHit && !target.isMissed) {
                                target.isMissed = true
                                combo = 0
                                missCount++
                                hitFeedback = "MISS"
                                hitFeedbackColor = Color(0xFFFF2A55)
                            }
                        }
                        osuTargets.removeAll { it.approachScale < 0.7f }
                    }

                    ArcadeGameMode.RETRO_SNAKE_BEAT -> {
                        if (now - lastSpawnTime >= (beatIntervalMs * 0.7f).toLong()) {
                            val head = snakeBody.first()
                            val nextHead = Offset((head.x + snakeDir.x + 20) % 20, (head.y + snakeDir.y + 20) % 20)
                            snakeBody.add(0, nextHead)

                            if ((nextHead.x.toInt() == foodPos.x.toInt()) && (nextHead.y.toInt() == foodPos.y.toInt())) {
                                score += 200 + (combo * 10)
                                combo++
                                foodPos = Offset(Random.nextInt(18).toFloat(), Random.nextInt(18).toFloat())
                                triggerHaptic(30)
                            } else {
                                snakeBody.removeAt(snakeBody.size - 1)
                            }
                            lastSpawnTime = now
                        }
                    }

                    ArcadeGameMode.CYBER_PONG -> {
                        ballPos = Offset(ballPos.x + ballVel.x, ballPos.y + ballVel.y)
                        if (ballPos.x <= 0.05f || ballPos.x >= 0.95f) {
                            ballVel = Offset(-ballVel.x, ballVel.y)
                            triggerHaptic(10)
                        }
                        if (ballPos.y <= 0.1f) {
                            ballVel = Offset(ballVel.x, abs(ballVel.y))
                            triggerHaptic(10)
                        }
                        if (ballPos.y >= 0.85f) {
                            if (abs(ballPos.x - paddleX) < 0.18f) {
                                ballVel = Offset(ballVel.x * 1.02f, -abs(ballVel.y) * 1.02f)
                                score += 150 + (combo * 10)
                                combo++
                                triggerHaptic(35)
                                spawnSparks(paddleX * 1000f, 1500f, count = 20, color = Color(0xFFFF9E00))
                            } else if (ballPos.y > 0.95f) {
                                combo = 0
                                missCount++
                                ballPos = Offset(0.5f, 0.4f)
                                ballVel = Offset(0.012f, 0.015f)
                            }
                        }
                    }

                    ArcadeGameMode.NEON_SPACE_DODGE -> {
                        if (now - lastSpawnTime >= (beatIntervalMs * 0.8f).toLong()) {
                            asteroids.add(Offset(Random.nextFloat() * 0.85f + 0.08f, 0.1f))
                            lastSpawnTime = now
                        }

                        var i = asteroids.size - 1
                        while (i >= 0) {
                            val a = asteroids[i]
                            val newY = a.y + (selectedDifficulty.speed * 1.2f)
                            if (abs(a.x - playerShipX) < 0.1f && abs(newY - 0.85f) < 0.06f) {
                                combo = 0
                                missCount++
                                triggerHaptic(50)
                                spawnSparks(playerShipX * 1000f, 1500f, count = 25, color = Color(0xFFFF007F))
                                asteroids.removeAt(i)
                            } else if (newY > 1.05f) {
                                score += 50 + (combo * 5)
                                combo++
                                asteroids.removeAt(i)
                            } else {
                                asteroids[i] = Offset(a.x, newY)
                            }
                            i--
                        }
                    }
                }

                if (playerState.durationMs > 0 && playerState.currentPositionMs >= playerState.durationMs - 1200) {
                    gameState = ArcadeGameState.VICTORY
                }

                delay(16)
            }
        }
    }

    // Tap lane handler
    fun onLaneTapped(lane: Int, screenWidthPx: Float = 1000f, screenHeightPx: Float = 1800f) {
        if (gameState != ArcadeGameState.PLAYING) return
        val targetTile = tiles3D.filter { it.lane == lane && !it.isHit && !it.isMissed }
            .minByOrNull { abs(it.z - 0.88f) }

        if (targetTile != null) {
            val dist = abs(targetTile.z - 0.88f)
            val tapX = (screenWidthPx * 0.125f) + (lane * (screenWidthPx * 0.25f))
            val tapY = screenHeightPx * 0.78f

            if (dist < 0.11f) {
                targetTile.isHit = true
                val mult = 2.0f - selectedDifficulty.spawnIntervalMult
                score += (300f * mult).toInt() + (combo * 15)
                combo++
                if (combo > maxCombo) maxCombo = combo
                perfectCount++
                hitFeedback = "PERFECT 300!"
                hitFeedbackColor = Color(0xFF00E5FF)
                triggerHaptic(30)
                spawnSparks(tapX, tapY, count = 24, color = Color(0xFF00E5FF))
            } else if (dist < 0.22f) {
                targetTile.isHit = true
                val mult = 2.0f - selectedDifficulty.spawnIntervalMult
                score += (100f * mult).toInt() + (combo * 8)
                combo++
                if (combo > maxCombo) maxCombo = combo
                greatCount++
                hitFeedback = "GREAT 100!"
                hitFeedbackColor = Color(0xFF00FF66)
                triggerHaptic(15)
                spawnSparks(tapX, tapY, count = 14, color = Color(0xFF00FF66))
            } else {
                targetTile.isMissed = true
                combo = 0
                missCount++
                hitFeedback = "MISS"
                hitFeedbackColor = Color(0xFFFF2A55)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (gameState) {
            ArcadeGameState.SONG_SELECT -> {
                // ── AAA Modern Arcade Hub & Mini-Game Picker ──
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
                        Text("🎮 AURA RETRO & 3D ARCADE", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White, letterSpacing = 1.sp)
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.SportsEsports, null, tint = Color(0xFF00E5FF))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Select Game Engine:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))

                    // Horizontal Scrollable Arcade Game Cards
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ArcadeGameMode.values()) { mode ->
                            Surface(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { selectedMode = mode },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selectedMode == mode) Color(0xFF1B2438) else Color(0xFF0E111D),
                                border = BorderStroke(
                                    if (selectedMode == mode) 2.dp else 1.dp,
                                    if (selectedMode == mode) mode.color else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(mode.title, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White, maxLines = 1)
                                    Text(mode.subtitle, fontSize = 10.sp, color = mode.color, maxLines = 2)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

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
                    Text("Choose Any Audio Track to Play:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))

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
                                tiles3D.clear()
                                osuTargets.clear()
                                particles.clear()
                                asteroids.clear()
                                vm.playMediaFile(track)
                                gameState = ArcadeGameState.PLAYING
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = selectedMode.color),
                        enabled = selectedTrack != null
                    ) {
                        Text("LAUNCH ${selectedMode.title.take(18)}", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp, color = Color.Black)
                    }
                }
            }

            ArcadeGameState.PLAYING -> {
                // ── Active Multi-Engine Game Stage ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top HUD Score
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            gameState = ArcadeGameState.SONG_SELECT
                            vm.stopPlayback()
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$score", fontWeight = FontWeight.Black, fontSize = 32.sp, color = Color.White)
                            if (combo > 1) {
                                Text("$combo COMBO 🔥", fontWeight = FontWeight.Black, fontSize = 14.sp, color = selectedMode.color)
                            }
                        }

                        hitFeedback?.let {
                            Text(it, fontWeight = FontWeight.Black, fontSize = 16.sp, color = hitFeedbackColor)
                        }
                    }

                    when (selectedMode) {
                        ArcadeGameMode.HIGHWAY_3D, ArcadeGameMode.PIANO_TILES_CLASSIC -> {
                            // ── 3D Highway / Classic Piano Tiles ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val lane = when {
                                            offset.x < size.width * 0.25f -> 0
                                            offset.x < size.width * 0.50f -> 1
                                            offset.x < size.width * 0.75f -> 2
                                            else -> 3
                                        }
                                        onLaneTapped(lane, size.width.toFloat(), size.height.toFloat())
                                    }
                                }) {
                                    val w = size.width
                                    val h = size.height

                                    val is3D = selectedMode == ArcadeGameMode.HIGHWAY_3D
                                    val horizonY = if (is3D) h * 0.12f else 0f
                                    val horizonW = if (is3D) w * 0.35f else w
                                    val horizonLeft = if (is3D) (w - horizonW) / 2f else 0f
                                    val horizonRight = horizonLeft + horizonW

                                    val bottomY = h * 0.88f
                                    val bottomLeft = if (is3D) w * 0.04f else 0f
                                    val bottomRight = if (is3D) w * 0.96f else w

                                    val roadPath = Path().apply {
                                        moveTo(horizonLeft, horizonY)
                                        lineTo(horizonRight, horizonY)
                                        lineTo(bottomRight, bottomY)
                                        lineTo(bottomLeft, bottomY)
                                        close()
                                    }
                                    drawPath(path = roadPath, brush = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF05060A))))

                                    for (i in 0..4) {
                                        val topX = horizonLeft + (i * (horizonW / 4f))
                                        val botX = bottomLeft + (i * ((bottomRight - bottomLeft) / 4f))
                                        drawLine(
                                            color = Color(0xFF00E5FF).copy(alpha = if (i == 0 || i == 4) 0.8f else 0.25f),
                                            start = Offset(topX, horizonY),
                                            end = Offset(botX, bottomY),
                                            strokeWidth = if (i == 0 || i == 4) 4f else 2f
                                        )
                                    }

                                    val hitZ = 0.88f
                                    val hitTopLeftX = horizonLeft + hitZ * (bottomLeft - horizonLeft)
                                    val hitTopRightX = horizonRight + hitZ * (bottomRight - horizonRight)
                                    val hitY = horizonY + hitZ * (bottomY - horizonY)

                                    drawLine(
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFFF2A55), Color(0xFF00E5FF), Color(0xFFFF2A55))),
                                        start = Offset(hitTopLeftX, hitY),
                                        end = Offset(hitTopRightX, hitY),
                                        strokeWidth = 10f
                                    )

                                    tiles3D.forEach { tile ->
                                        val z0 = tile.z.coerceIn(0f, 1.2f)
                                        val z1 = (tile.z + tile.lengthZ).coerceIn(0f, 1.2f)

                                        val y0 = horizonY + z0 * (bottomY - horizonY)
                                        val y1 = horizonY + z1 * (bottomY - horizonY)

                                        val curHL = horizonLeft + z0 * (bottomLeft - horizonLeft)
                                        val curHR = horizonRight + z0 * (bottomRight - horizonRight)
                                        val laneW0 = (curHR - curHL) / 4f

                                        val nextHL = horizonLeft + z1 * (bottomLeft - horizonLeft)
                                        val nextHR = horizonRight + z1 * (bottomRight - horizonRight)
                                        val laneW1 = (nextHR - nextHL) / 4f

                                        val x0Left = curHL + (tile.lane * laneW0) + 4f
                                        val x0Right = x0Left + laneW0 - 8f

                                        val x1Left = nextHL + (tile.lane * laneW1) + 4f
                                        val x1Right = x1Left + laneW1 - 8f

                                        val tilePath = Path().apply {
                                            moveTo(x0Left, y0)
                                            lineTo(x0Right, y0)
                                            lineTo(x1Right, y1)
                                            lineTo(x1Left, y1)
                                            close()
                                        }

                                        drawPath(
                                            path = tilePath,
                                            brush = Brush.verticalGradient(
                                                if (tile.isHit) listOf(Color(0xFF00FF66), Color(0xFF00FF66).copy(alpha = 0.3f))
                                                else if (tile.isMissed) listOf(Color(0xFFFF2A55), Color(0xFFFF2A55).copy(alpha = 0.3f))
                                                else if (tile.lane % 2 == 0) listOf(Color(0xFFFF2A55), Color(0xFFFF6B2B))
                                                else listOf(Color(0xFF9D4EDD), Color(0xFF00E5FF))
                                            )
                                        )
                                        drawPath(path = tilePath, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 2.5f))
                                    }

                                    particles.forEach { p ->
                                        drawCircle(color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)), radius = p.size * p.life, center = Offset(p.x, p.y))
                                    }
                                }
                            }

                            // Tactile Keys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp)
                                    .background(Color(0xFF080A12))
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
                                        colors = ButtonDefaults.buttonColors(containerColor = if (lane % 2 == 0) Color(0xFF1E293B) else Color(0xFF171E2E)),
                                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                                    ) {
                                        Text(when (lane) { 0 -> "D"; 1 -> "F"; 2 -> "J"; else -> "K" }, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        ArcadeGameMode.OSU_CYBERPUNK -> {
                            // ── OSU Cyberpunk Engine ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF080911))
                            ) {
                                osuTargets.forEach { circle ->
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (circle.xFrac * 320).dp, y = (circle.yFrac * 500).dp)
                                            .size(80.dp)
                                            .clickable {
                                                circle.isHit = true
                                                score += 300 + (combo * 20)
                                                combo++
                                                triggerHaptic(35)
                                                spawnSparks(circle.xFrac * 1000f, circle.yFrac * 1800f, count = 25, color = Color(0xFFFF2A55))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.size((80 * circle.approachScale).dp).border(3.dp, Color(0xFF00E5FF).copy(alpha = 0.85f), CircleShape))
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(Brush.radialGradient(listOf(Color(0xFFFF2A55), Color(0xFF9D4EDD))))
                                                .border(2.5.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${circle.number}", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        ArcadeGameMode.RETRO_SNAKE_BEAT -> {
                            // ── Rhythm Snake Canvas ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF08120B))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cellW = size.width / 20f
                                    val cellH = size.height / 20f

                                    drawCircle(color = Color(0xFFFF2A55), radius = cellW * 0.45f, center = Offset((foodPos.x + 0.5f) * cellW, (foodPos.y + 0.5f) * cellH))

                                    snakeBody.forEachIndexed { idx, segment ->
                                        drawRect(
                                            color = if (idx == 0) Color(0xFF00FF66) else Color(0xFF00B044),
                                            topLeft = Offset(segment.x * cellW + 2f, segment.y * cellH + 2f),
                                            size = Size(cellW - 4f, cellH - 4f)
                                        )
                                    }
                                }
                            }

                            // Snake D-Pad
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp)
                                    .background(Color(0xFF080A12))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { snakeDir = Offset(-1f, 0f) }) { Icon(Icons.Filled.ArrowBack, null, tint = Color(0xFF00FF66)) }
                                IconButton(onClick = { snakeDir = Offset(0f, -1f) }) { Icon(Icons.Filled.ArrowUpward, null, tint = Color(0xFF00FF66)) }
                                IconButton(onClick = { snakeDir = Offset(0f, 1f) }) { Icon(Icons.Filled.ArrowDownward, null, tint = Color(0xFF00FF66)) }
                                IconButton(onClick = { snakeDir = Offset(1f, 0f) }) { Icon(Icons.Filled.ArrowForward, null, tint = Color(0xFF00FF66)) }
                            }
                        }

                        ArcadeGameMode.CYBER_PONG -> {
                            // ── Cyber Pong Canvas ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0816))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        paddleX = (offset.x / size.width).coerceIn(0.15f, 0.85f)
                                    }
                                }) {
                                    val w = size.width
                                    val h = size.height

                                    drawCircle(color = Color(0xFFFF9E00), radius = 18f, center = Offset(ballPos.x * w, ballPos.y * h))
                                    drawRoundRect(
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFFF9E00), Color(0xFFFF2A55))),
                                        topLeft = Offset((paddleX - 0.15f) * w, h * 0.85f),
                                        size = Size(w * 0.3f, 22f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                                    )
                                }
                            }
                        }

                        ArcadeGameMode.NEON_SPACE_DODGE -> {
                            // ── Neon Space Dodge Canvas ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0A0512))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        playerShipX = (offset.x / size.width).coerceIn(0.1f, 0.9f)
                                    }
                                }) {
                                    val w = size.width
                                    val h = size.height

                                    drawCircle(color = Color(0xFF00E5FF), radius = 24f, center = Offset(playerShipX * w, h * 0.85f))

                                    asteroids.forEach { a ->
                                        drawCircle(color = Color(0xFFFF007F), radius = 20f, center = Offset(a.x * w, a.y * h))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ArcadeGameState.VICTORY, ArcadeGameState.GAME_OVER -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🏆 STAGE CLEAR!", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color(0xFF00FF66), letterSpacing = 2.sp)
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF101320),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Game: ${selectedMode.title}", fontSize = 14.sp, color = selectedMode.color, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Final Score: $score", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Max Combo: $maxCombo 🔥", fontSize = 15.sp, color = Color(0xFFFF2A55), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { gameState = ArcadeGameState.SONG_SELECT },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("CHOOSE ANOTHER GAME", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
