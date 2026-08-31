package com.ypdlp.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File

// ────────────────────────────────────────────────────────────────────
//  Liquid Crystal & iOS Glassmorphic Palette
// ────────────────────────────────────────────────────────────────────
private val VoidDark       = Color(0xFF090A0F)
private val GlassDeep      = Color(0xFF12141E)
private val GlassCard      = Color(0xFF1A1D2B).copy(alpha = 0.70f)
private val GlassBorder    = Color.White.copy(alpha = 0.12f)
private val GlassHighlight = Color.White.copy(alpha = 0.22f)

private val LiquidNeonRed  = Color(0xFFFF2A55)
private val LiquidNeonOrange = Color(0xFFFF6B2B)
private val LiquidCyan     = Color(0xFF00E5FF)
private val LiquidPurple   = Color(0xFF9D4EDD)

// 8MAN Hachiman Aesthetic (Dead-Fish Eyes & Service Club Matrix)
private val HachimanGreen  = Color(0xFF00FF66)
private val HachimanCyan   = Color(0xFF00E5FF)
private val HachimanPurple = Color(0xFF9D4EDD)
private val HachimanDark   = Color(0xFF070913)

private val TextPure       = Color(0xFFFFFFFF)
private val TextMuted      = Color(0xFFA0A5B8)
private val TextDim        = Color(0xFF6B7280)

private val RedGradient = Brush.horizontalGradient(
    listOf(LiquidNeonRed, LiquidNeonOrange)
)
private val HachimanGradient = Brush.horizontalGradient(
    listOf(Color(0xFF00E5FF), Color(0xFF00FF66))
)
private val GlassGradient = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))
)
private val MeshBackgroundGradient = Brush.verticalGradient(
    listOf(Color(0xFF0B0D17), Color(0xFF080910), Color(0xFF040508))
)

// ────────────────────────────────────────────────────────────────────
//  Main Activity
// ────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = intent
            .takeIf { it?.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = LiquidNeonRed,
                    background = VoidDark,
                    surface = GlassDeep,
                    onPrimary = Color.White,
                    onBackground = TextPure,
                    onSurface = TextPure
                )
            ) {
                YPDlpApp(prefilledUrl = sharedUrl)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Root App Scaffold with Liquid Crystal Navigation & Floating Mini-Player
// ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YPDlpApp(
    prefilledUrl: String? = null,
    vm: MainViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNowPlayingScreen by remember { mutableStateOf(false) }
    var activeVideoFile by remember { mutableStateOf<DownloadedFile?>(null) }

    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastLogoTapTime by remember { mutableLongStateOf(0L) }

    var isDownloaderUnlocked by remember { mutableStateOf(false) }
    var libraryTapCount by remember { mutableIntStateOf(0) }
    var lastLibraryTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(prefilledUrl) {
        prefilledUrl?.let {
            isDownloaderUnlocked = true
            selectedTab = 2
            vm.onUrlChange(it)
            vm.fetchInfo()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (ui.isHachimanMode) Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF04060A))) else MeshBackgroundGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    isHachimanMode = ui.isHachimanMode,
                    onLogoClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastLogoTapTime > 2500) {
                            logoTapCount = 1
                        } else {
                            logoTapCount++
                        }
                        lastLogoTapTime = now

                        if (logoTapCount >= 5) {
                            logoTapCount = 0
                            val enabled = vm.toggleHachimanMode()
                            if (enabled) {
                                selectedTab = 4 // Switch directly to 8MAN Console
                                Toast.makeText(context, "★ 8MAN Mode Activated! 'Youth is a lie. It is evil.'", Toast.LENGTH_LONG).show()
                            } else {
                                if (selectedTab == 4) selectedTab = 0
                                Toast.makeText(context, "❄ Yukino Mode Restored: 'Being hated is not a virtue.'", Toast.LENGTH_LONG).show()
                            }
                        } else if (logoTapCount in 2..4) {
                            val remaining = 5 - logoTapCount
                            Toast.makeText(context, "Tap $remaining more times for 8MAN Mode...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                Column {
                    // Floating Liquid Glass Mini-Player (if playing audio/video in background)
                    AnimatedVisibility(
                        visible = playerState.currentFile != null && activeVideoFile == null && !showNowPlayingScreen,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        playerState.currentFile?.let { current ->
                            FloatingMiniPlayer(
                                file = current,
                                isPlaying = playerState.isPlaying,
                                positionMs = playerState.currentPositionMs,
                                durationMs = playerState.durationMs,
                                onToggle = vm::togglePlayback,
                                onClose = vm::stopPlayback,
                                onExpand = { showNowPlayingScreen = true }
                            )
                        }
                    }

                    // iOS Glass Capsule Bottom Navigation (with hidden Download tab unlocked via 5 taps)
                    GlassBottomNav(
                        selected = selectedTab,
                        onSelect = { tab ->
                            if (tab == 3) { // Library Tab
                                val now = System.currentTimeMillis()
                                if (now - lastLibraryTapTime > 2500) {
                                    libraryTapCount = 1
                                } else {
                                    libraryTapCount++
                                }
                                lastLibraryTapTime = now

                                if (libraryTapCount >= 5) {
                                    libraryTapCount = 0
                                    isDownloaderUnlocked = !isDownloaderUnlocked
                                    if (isDownloaderUnlocked) {
                                        Toast.makeText(context, "🔓 Downloader Tab Unlocked!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "🔒 Downloader Tab Hidden", Toast.LENGTH_SHORT).show()
                                        if (selectedTab == 2) selectedTab = 0
                                    }
                                }
                            }
                            selectedTab = tab
                        },
                        queueBadgeCount = queue.count {
                            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                        },
                        downloadsCount = ui.downloadedFiles.size,
                        isHachimanMode = ui.isHachimanMode,
                        showDownloaderTab = isDownloaderUnlocked
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(180))
                    },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        0 -> com.ypdlp.downloader.aura.AuraHomeScreen(
                            ui = ui,
                            vm = vm,
                            onOpenAutoMix = { selectedTab = 1 },
                            onOpenNowPlaying = { showNowPlayingScreen = true }
                        )
                        1 -> com.ypdlp.downloader.aura.AuraAutoMixScreen(
                            session = ui.autoMixSession,
                            playerState = playerState,
                            vm = vm
                        )
                        2 -> DownloadTab(ui = ui, vm = vm)
                        3 -> LibraryTab(
                            ui = ui,
                            vm = vm,
                            onWatchVideo = { file ->
                                vm.stopPlayback()
                                activeVideoFile = file
                            },
                            onOpenNowPlaying = { showNowPlayingScreen = true }
                        )
                        4 -> HachimanConsoleTab(ui = ui, vm = vm)
                    }
                }
            }
        }

        // Fullscreen AURA Music Universe Player
        if (showNowPlayingScreen && playerState.currentFile != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showNowPlayingScreen = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                com.ypdlp.downloader.aura.AuraNowPlayingScreen(
                    playerState = playerState,
                    vm = vm,
                    onClose = { showNowPlayingScreen = false }
                )
            }
        }

        // In-App Video Player Dialog
        activeVideoFile?.let { videoFile ->
            InAppVideoPlayerDialog(
                file = videoFile,
                onDismiss = { activeVideoFile = null },
                onOpenExternal = { openWithExternalPlayer(context, videoFile.file) },
                onShare = { shareMediaFile(context, videoFile.file) }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            ServerSettingsDialog(
                currentServer = ui.serverUrl,
                onDismiss = { showSettingsDialog = false },
                onSave = { newUrl ->
                    vm.setServerUrl(newUrl)
                    showSettingsDialog = false
                }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Glass Top Bar with Glow, Hachiman Switch & Settings
// ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    isHachimanMode: Boolean = false,
    onLogoClick: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onLogoClick)
                    .padding(end = 8.dp)
            ) {
                Image(
                    painter = painterResource(if (isHachimanMode) R.drawable.hachiman_logo else R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.8.dp, if (isHachimanMode) HachimanGreen else LiquidNeonRed, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (isHachimanMode) "8MAN Console" else "YPDlp",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = TextPure,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isHachimanMode) "「本物が欲しい」Service Club Edition" else "Liquid Crystal Edition",
                        fontSize = 10.sp,
                        color = if (isHachimanMode) HachimanGreen else LiquidCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassCard)
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = if (isHachimanMode) HachimanGreen else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

// ────────────────────────────────────────────────────────────────────
//  iOS Glass Capsule Bottom Navigation Bar (AURA Universe)
// ────────────────────────────────────────────────────────────────────
@Composable
fun GlassBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    queueBadgeCount: Int,
    downloadsCount: Int,
    isHachimanMode: Boolean = false,
    showDownloaderTab: Boolean = false
) {
    val activeGrad = if (isHachimanMode) HachimanGradient else RedGradient

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = if (isHachimanMode) HachimanGreen.copy(alpha = 0.3f) else LiquidNeonRed.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF101320).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (isHachimanMode) HachimanGreen.copy(alpha = 0.3f) else GlassHighlight)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavCapsuleItem(
                    selected = selected == 0,
                    icon = Icons.Filled.Home,
                    label = "AURA",
                    activeGradient = Brush.horizontalGradient(listOf(Color(0xFF9D4EDD), Color(0xFF00E5FF))),
                    onClick = { onSelect(0) }
                )
                NavCapsuleItem(
                    selected = selected == 1,
                    icon = Icons.Filled.Tune,
                    label = "AutoMix",
                    activeGradient = Brush.horizontalGradient(listOf(Color(0xFFFF2A55), Color(0xFFFF6B2B))),
                    onClick = { onSelect(1) }
                )
                if (showDownloaderTab) {
                    NavCapsuleItem(
                        selected = selected == 2,
                        icon = Icons.Filled.Download,
                        label = "Get",
                        activeGradient = activeGrad,
                        onClick = { onSelect(2) }
                    )
                }
                NavCapsuleItem(
                    selected = selected == 3,
                    icon = Icons.Filled.VideoLibrary,
                    label = "Library",
                    badge = if (downloadsCount > 0) "$downloadsCount" else null,
                    activeGradient = activeGrad,
                    onClick = { onSelect(3) }
                )
                if (isHachimanMode) {
                    NavCapsuleItem(
                        selected = selected == 4,
                        icon = Icons.Filled.Terminal,
                        label = "8MAN",
                        badge = "DEV",
                        activeGradient = HachimanGradient,
                        onClick = { onSelect(4) }
                    )
                }
            }
        }
    }
}

@Composable
fun NavCapsuleItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String? = null,
    activeGradient: Brush = RedGradient,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "navScale")

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) activeGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(
                badge = {
                    badge?.let {
                        Badge(containerColor = if (selected) Color.White else LiquidNeonRed) {
                            Text(it, color = if (selected) Color.Black else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) Color.Black else TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Download Tab (Supports Video & Playlist with Liquid Crystal Cards)
// ────────────────────────────────────────────────────────────────────
@Composable
fun DownloadTab(ui: UiState, vm: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Input Glass Box ─────────────────────────────────────
        item {
            LiquidGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (ui.isPlaylistMode) "🔗 Paste Playlist URL" else "🔗 Paste Video URL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (ui.isPlaylistMode) LiquidCyan else TextMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Mode toggle badge
                            FilterChip(
                                selected = ui.isPlaylistMode,
                                onClick = { vm.setPlaylistMode(!ui.isPlaylistMode) },
                                label = { Text(if (ui.isPlaylistMode) "Playlist Mode" else "Single", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LiquidCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = LiquidCyan,
                                    containerColor = GlassCard,
                                    labelColor = TextDim
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ui.urlText,
                            onValueChange = vm::onUrlChange,
                            placeholder = {
                                Text(
                                    if (ui.isPlaylistMode) "https://youtube.com/playlist?list=…" else "https://youtube.com/watch?v=…",
                                    color = TextDim,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (ui.isPlaylistMode) LiquidCyan else LiquidNeonRed,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPure,
                                unfocusedTextColor = TextPure,
                                cursorColor = LiquidNeonRed,
                                focusedContainerColor = GlassCard,
                                unfocusedContainerColor = GlassCard
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        // 1-Tap Paste Button
                        Button(
                            onClick = {
                                val clip = clipboardManager.getText()?.text?.trim() ?: ""
                                if (clip.isNotBlank()) {
                                    vm.onUrlChange(clip)
                                    vm.fetchInfo()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassCard),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Fetch Button with Liquid Gradient
                    Button(
                        onClick = vm::fetchInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = LiquidNeonRed.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !ui.isLoadingInfo
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (ui.isPlaylistMode) Brush.horizontalGradient(listOf(LiquidPurple, LiquidCyan)) else RedGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ui.isLoadingInfo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (ui.isPlaylistMode) Icons.Filled.PlaylistPlay else Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (ui.isPlaylistMode) "Fetch Entire Playlist" else "Fetch Video Details",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    ui.infoError?.let {
                        Text(
                            "⚠️ $it",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // ── Single Video Card ───────────────────────────────────
        ui.videoInfo?.let { info ->
            item {
                VideoPreviewCard(info = info)
            }
            item {
                FormatAndDownloadControls(
                    ui = ui,
                    vm = vm,
                    isPlaylist = false,
                    onDownload = {
                        vm.addToQueue()
                        Toast.makeText(context, "Added to Download Queue!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── Playlist Preview Card ───────────────────────────────
        ui.playlistInfo?.let { playlist ->
            item {
                PlaylistPreviewCard(playlist = playlist)
            }
            item {
                FormatAndDownloadControls(
                    ui = ui,
                    vm = vm,
                    isPlaylist = true,
                    itemCount = playlist.itemCount,
                    onDownload = {
                        vm.addPlaylistToQueue()
                        Toast.makeText(context, "Enqueued ${playlist.itemCount} items from Playlist!", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Video Preview Card (Liquid Glass 16:9 Cinema Preview)
// ────────────────────────────────────────────────────────────────────
@Composable
fun VideoPreviewCard(info: VideoInfo) {
    LiquidGlassCard {
        Column {
            if (info.thumbnailUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    AsyncImage(
                        model = info.thumbnailUrl,
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient vignette
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                    // Duration Pill
                    if (info.durationSeconds > 0) {
                        val m = info.durationSeconds / 60
                        val s = info.durationSeconds % 60
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, GlassBorder)
                        ) {
                            Text(
                                "⏱ %02d:%02d".format(m, s),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    info.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPure,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.channel.isNotBlank()) {
                        Text("📺 ${info.channel}", fontSize = 12.sp, color = TextMuted)
                    }
                    if (info.viewCount > 0) {
                        Text("👁 %,d views".format(info.viewCount), fontSize = 11.sp, color = TextDim)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Playlist Preview Card
// ────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistPreviewCard(playlist: PlaylistInfo) {
    var isExpanded by remember { mutableStateOf(false) }

    LiquidGlassCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = LiquidCyan
                    )
                    Text(
                        "📁 ${playlist.itemCount} Videos • by ${playlist.author.ifBlank { "YouTube" }}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expand Playlist",
                        tint = LiquidCyan
                    )
                }
            }

            // Expandable Video Items List
            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider(color = GlassBorder)
                    playlist.items.take(20).forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}.",
                                fontSize = 12.sp,
                                color = TextDim,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                item.title,
                                fontSize = 12.sp,
                                color = TextPure,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.durationSeconds.let { dur ->
                                    val m = dur / 60
                                    val s = dur % 60
                                    "%02d:%02d".format(m, s)
                                },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    if (playlist.items.size > 20) {
                        Text(
                            "+ ${playlist.items.size - 20} more videos…",
                            fontSize = 11.sp,
                            color = TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Format & Quality Controls
// ────────────────────────────────────────────────────────────────────
@Composable
fun FormatAndDownloadControls(
    ui: UiState,
    vm: MainViewModel,
    isPlaylist: Boolean,
    itemCount: Int = 1,
    onDownload: () -> Unit
) {
    LiquidGlassCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Select Output Quality & Format",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextMuted
            )

            // Video vs Audio Toggle
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(DownloadType.VIDEO to "🎬 Video Stream", DownloadType.AUDIO to "🎵 Audio Only").forEach { (t, label) ->
                    val isSelected = ui.selectedType == t
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { vm.setType(t) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) LiquidNeonRed.copy(alpha = 0.2f) else GlassCard,
                        border = BorderStroke(1.dp, if (isSelected) LiquidNeonRed else GlassBorder)
                    ) {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }

            if (ui.selectedType == DownloadType.VIDEO) {
                DropdownRowGlass(
                    label = "Resolution",
                    current = ui.selectedQuality,
                    options = listOf("Best", "4K (2160p)", "2K (1440p)", "1080p", "720p", "480p", "360p"),
                    onPick = vm::setQuality
                )
                DropdownRowGlass(
                    label = "Container",
                    current = ui.selectedContainer,
                    options = listOf("MP4", "MKV", "WEBM", "AVI"),
                    onPick = vm::setContainer
                )
            } else {
                DropdownRowGlass(
                    label = "Audio Format",
                    current = ui.selectedContainer,
                    options = listOf("MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS"),
                    onPick = vm::setContainer
                )
            }

            // Download Action Button
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = LiquidNeonRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isPlaylist) Brush.horizontalGradient(listOf(LiquidPurple, LiquidCyan)) else RedGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPlaylist) Icons.Filled.PlaylistAddCheck else Icons.Filled.FileDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPlaylist) "Download Entire Playlist ($itemCount Videos)" else "Download (${ui.selectedQuality} ${ui.selectedContainer})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Queue Tab with Live Status & Animations
// ────────────────────────────────────────────────────────────────────
@Composable
fun QueueTab(queue: List<DownloadItem>, vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Active Download Queue",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPure
            )
            TextButton(onClick = vm::clearDone) {
                Text("Clear Finished", color = LiquidCyan, fontSize = 12.sp)
            }
        }

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Download Queue is Empty",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Add videos or playlists to download",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    QueueCardItem(item = item, onCancel = { vm.cancelItem(item.id) })
                }
            }
        }
    }
}

@Composable
fun QueueCardItem(item: DownloadItem, onCancel: () -> Unit) {
    LiquidGlassCard {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(76.dp, 50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.videoInfo.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.videoInfo.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.PlayCircle, null, tint = TextDim, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.videoInfo.title.ifBlank { item.videoInfo.url.takeLast(35) },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadgeGlass(item)
                    FormatBadgeGlass(item.container)
                    if (item.quality.isNotBlank()) FormatBadgeGlass(item.quality)
                }

                // Progress Bar
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.POST_PROCESSING) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = LiquidNeonRed,
                        trackColor = GlassBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.progress}%", fontSize = 10.sp, color = TextMuted)
                        Text(item.speed, fontSize = 10.sp, color = LiquidCyan)
                    }
                } else if (item.status == DownloadStatus.ERROR && item.errorMessage.isNotBlank()) {
                    Text(
                        "⚠️ ${item.errorMessage.take(120)}",
                        fontSize = 11.sp,
                        color = Color(0xFFFF5252),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Cancel", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Downloads / Library Tab (In-App Media Player & Manager)
// ────────────────────────────────────────────────────────────────────
@Composable
fun LibraryTab(
    ui: UiState,
    vm: MainViewModel,
    onWatchVideo: (DownloadedFile) -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Downloaded Library",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPure
                )
                Text(
                    "${ui.downloadedFiles.size} media files on device",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            IconButton(
                onClick = vm::loadDownloadedFiles,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassCard)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = LiquidCyan, modifier = Modifier.size(18.dp))
            }
        }

        if (ui.downloadedFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No Downloads Found",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Downloaded videos & audios will appear here",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(ui.downloadedFiles, key = { it.path }) { file ->
                    DownloadedMediaCard(
                        file = file,
                        onPlay = {
                            if (file.isVideo) {
                                onWatchVideo(file)
                            } else {
                                vm.playMediaFile(file)
                                onOpenNowPlaying()
                            }
                        },
                        onWatchInApp = {
                            onWatchVideo(file)
                        },
                        onPlayAudio = {
                            vm.playMediaFile(file)
                            onOpenNowPlaying()
                        },
                        onOpenExternal = {
                            openWithExternalPlayer(context, file.file)
                        },
                        onShare = {
                            shareMediaFile(context, file.file)
                        },
                        onDelete = {
                            vm.deleteDownloadedFile(file)
                            Toast.makeText(context, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Downloaded Media Card with Play, Share, Delete Actions
// ────────────────────────────────────────────────────────────────────
@Composable
fun DownloadedMediaCard(
    file: DownloadedFile,
    onPlay: () -> Unit,
    onWatchInApp: () -> Unit,
    onPlayAudio: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Icon / Play Trigger
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (file.isVideo) LiquidNeonRed.copy(alpha = 0.15f) else LiquidPurple.copy(alpha = 0.15f))
                        .border(1.dp, if (file.isVideo) LiquidNeonRed.copy(alpha = 0.3f) else LiquidPurple.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (file.isVideo) Icons.Filled.PlayCircle else Icons.Filled.MusicNote,
                        contentDescription = "Play",
                        tint = if (file.isVideo) LiquidNeonRed else LiquidPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPlay),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        file.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPure,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.sizeFormatted, fontSize = 11.sp, color = LiquidCyan)
                        Text("•", fontSize = 11.sp, color = TextDim)
                        Text(file.extension, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick Actions: Open External Player, Share, Delete
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (file.isVideo) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onWatchInApp,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidNeonRed.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.SmartDisplay, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Watch In-App", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onPlayAudio,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        border = BorderStroke(1.dp, LiquidPurple.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Headphones, contentDescription = null, Modifier.size(15.dp), tint = LiquidPurple)
                        Spacer(Modifier.width(6.dp))
                        Text("Play Audio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  In-App Liquid Crystal Video Player Dialog
// ────────────────────────────────────────────────────────────────────
@Composable
fun InAppVideoPlayerDialog(
    file: DownloadedFile,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            kotlinx.coroutines.delay(4000)
            isControlsVisible = false
        }
    }

    // Live position tracking loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition.toLong()
                    if (durationMs <= 0 && vv.duration > 0) {
                        durationMs = vv.duration.toLong()
                    }
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    Dialog(
        onDismissRequest = {
            videoViewRef?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isControlsVisible = !isControlsVisible }
        ) {
            // Video Surface
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = android.view.Gravity.CENTER
                        }
                        setVideoPath(file.path)
                        setOnPreparedListener { mp ->
                            durationMs = mp.duration.toLong()
                            mp.isLooping = true
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        videoViewRef = this
                    }
                },
                update = { vv ->
                    videoViewRef = vv
                }
            )

            // Glass Overlay Controls
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    // Top Bar (Glass Header)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    videoViewRef?.stopPlayback()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = LiquidNeonRed.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, LiquidNeonRed.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            file.extension,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LiquidNeonRed,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        file.sizeFormatted,
                                        fontSize = 11.sp,
                                        color = LiquidCyan
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onOpenExternal,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in VLC/MX", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Center Play / Pause / Rewind / Skip Controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos.toLong()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Spacer(Modifier.width(28.dp))

                        // Large Play / Pause
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(LiquidNeonRed.copy(alpha = 0.85f))
                                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(Modifier.width(28.dp))

                        // Forward 10s
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos.toLong()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Bottom Seek & Time Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDurationMs(currentPositionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                formatDurationMs(durationMs),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                val target = (frac * durationMs).toLong()
                                currentPositionMs = target
                                videoViewRef?.seekTo(target.toInt())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = LiquidNeonRed,
                                activeTrackColor = LiquidNeonRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

// ────────────────────────────────────────────────────────────────────
//  Floating Glass Mini-Player (Plays in Background with Controls)
// ────────────────────────────────────────────────────────────────────
@Composable
fun FloatingMiniPlayer(
    file: DownloadedFile,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit = {}
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1B1E2E).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, LiquidCyan.copy(alpha = 0.4f))
    ) {
        Column {
            // Seek bar
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = LiquidCyan,
                trackColor = GlassBorder
            )

            val miniTransition = rememberInfiniteTransition(label = "MiniDisc")
            val miniDiscRotation by miniTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (isPlaying) 6000 else 100000000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "miniDiscRot"
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 💽 Rotating Mini Vinyl Disc Widget
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer { rotationZ = miniDiscRotation }
                        .clip(CircleShape)
                        .background(Color(0xFF141724))
                        .border(1.5.dp, LiquidCyan.copy(alpha = 0.7f), CircleShape),
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
                        Icon(
                            if (file.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = LiquidCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Mini spindle hole
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF090A12))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPure,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isPlaying) "Playing • Tap to open AURA Player" else "Paused",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                // Controls
                IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextDim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Liquid Glass Card Container
// ────────────────────────────────────────────────────────────────────
@Composable
fun LiquidGlassCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(24.dp),
        color = GlassCard,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        content()
    }
}

// ────────────────────────────────────────────────────────────────────
//  Glass Dropdown Selector
// ────────────────────────────────────────────────────────────────────
@Composable
fun DropdownRowGlass(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextMuted)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPure,
                    containerColor = GlassCard
                )
            ) {
                Text(current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Color(0xFF1A1D2B)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = TextPure, fontSize = 13.sp) },
                        onClick = { onPick(opt); expanded = false }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Settings Dialog (Custom Server Backend URL)
// ────────────────────────────────────────────────────────────────────
@Composable
fun ServerSettingsDialog(currentServer: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentServer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Engine Settings", fontWeight = FontWeight.Bold, color = TextPure) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LiquidCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, LiquidCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        "⚡ 100% On-Device Standalone Engine:\nDownloads, extracts, and merges videos directly on your phone's processor. No PC, Render, or server needed!",
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    "Optional: Custom Server URL (Leave empty for on-device engine):",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Leave empty for on-device mode", color = TextDim, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiquidCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPure,
                        unfocusedTextColor = TextPure
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { text = "" }) {
                        Text("✔ On-Device Mode (Default)", fontSize = 11.sp, color = LiquidCyan)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(containerColor = LiquidNeonRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = Color(0xFF1A1D2B),
        shape = RoundedCornerShape(20.dp)
    )
}

// ────────────────────────────────────────────────────────────────────
//  Status Badges
// ────────────────────────────────────────────────────────────────────
@Composable
fun StatusBadgeGlass(item: DownloadItem) {
    val (txt, color) = when (item.status) {
        DownloadStatus.QUEUED -> "Queued" to TextMuted
        DownloadStatus.DOWNLOADING -> "Downloading" to LiquidNeonOrange
        DownloadStatus.POST_PROCESSING -> "Merging…" to LiquidCyan
        DownloadStatus.DONE -> "✔ Done" to Color(0xFF00E676)
        DownloadStatus.ERROR -> "✘ Error" to Color(0xFFFF5252)
        DownloadStatus.CANCELLED -> "Cancelled" to Color(0xFF757575)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            txt,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun FormatBadgeGlass(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = GlassCard,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────────────
//  Helper Functions for File Sharing & External Playback
// ────────────────────────────────────────────────────────────────────
fun openWithExternalPlayer(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi")) "video/*" else "audio/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Play with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open player: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareMediaFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi")) "video/*" else "audio/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ────────────────────────────────────────────────────────────────────
//  8MAN Dev Console & Terminal Section (Hachiman Mode)
// ────────────────────────────────────────────────────────────────────
@Composable
fun HachimanConsoleTab(ui: UiState, vm: MainViewModel) {
    val logs by vm.consoleLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Hachiman Hero Header Banner ──
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = HachimanGreen.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0C101A).copy(alpha = 0.90f),
                border = BorderStroke(1.dp, HachimanGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.hachiman_logo),
                        contentDescription = "8MAN",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, HachimanGreen, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "8MAN Dev Console",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = TextPure
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = HachimanGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, HachimanGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HachimanGreen,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "「本物が欲しい」— Service Club Diagnostic Suite",
                            fontSize = 11.sp,
                            color = HachimanCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "\"Youth is a lie. It is evil. Hard work betrays plenty of dreams.\"",
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── 2. Quick Action Buttons ──
        item {
            LiquidGlassCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "🛠️ Quick Diagnostics & Actions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPure
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionChipGlass(
                            icon = Icons.Filled.SystemUpdate,
                            label = "Update yt-dlp",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.updateEngine()
                                Toast.makeText(context, "Updating yt-dlp binary...", Toast.LENGTH_SHORT).show()
                            }
                        )
                        ActionChipGlass(
                            icon = Icons.Filled.RestartAlt,
                            label = "Re-Init Engine",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.forceReinitEngine()
                                Toast.makeText(context, "Engine re-initialized!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionChipGlass(
                            icon = Icons.Filled.ContentCopy,
                            label = "Copy Log",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val report = vm.getDiagnosticReport()
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(report))
                                Toast.makeText(context, "Full diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        ActionChipGlass(
                            icon = Icons.Filled.CleaningServices,
                            label = "Purge Cache",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.clearTempCache()
                                Toast.makeText(context, "Cache purged!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        ActionChipGlass(
                            icon = Icons.Filled.Delete,
                            label = "Clear",
                            modifier = Modifier.weight(0.7f),
                            onClick = {
                                vm.clearConsoleLogs()
                            }
                        )
                    }
                }
            }
        }

        // ── 3. Quick Command Shell Bar ──
        item {
            LiquidGlassCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💻 Interactive 8MAN Shell",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = HachimanGreen
                        )
                        Text(
                            "yt-dlp v2024+",
                            fontSize = 10.sp,
                            color = TextDim,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Shell Command Quick Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val quickCmds = listOf("help", "diag", "ping", "8man", "reinit", "clearcache")
                        items(quickCmds) { cmd ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        vm.setTerminalInput(cmd)
                                        vm.executeTerminalCommand()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = HachimanGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, HachimanGreen.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    "> $cmd",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = HachimanGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Command Input Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ui.terminalInput,
                            onValueChange = vm::setTerminalInput,
                            placeholder = {
                                Text(
                                    "Enter command (e.g. diag, ping, info <url>)",
                                    color = TextDim,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HachimanGreen,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = HachimanGreen,
                                unfocusedTextColor = TextPure,
                                cursorColor = HachimanGreen,
                                focusedContainerColor = Color(0xFF080B12),
                                unfocusedContainerColor = Color(0xFF080B12)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = vm::executeTerminalCommand,
                            enabled = !ui.isRunningCommand && ui.terminalInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = HachimanGreen),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (ui.isRunningCommand) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Text("RUN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Live Terminal Console Window ──
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .shadow(16.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF07090F),
                border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.35f))
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Terminal Titlebar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0F1420),
                        border = BorderStroke(0.5.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                            }
                            Text(
                                "8man@sobu-high: /dev/pts/0",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${logs.size} lines",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextDim
                            )
                        }
                    }

                    // Terminal Logs Output
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            TerminalLineItem(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLineItem(entry: LogEntry) {
    val (lvlColor, lvlText) = when (entry.level) {
        LogLevel.INFO -> Color(0xFF00FF66) to "INF"
        LogLevel.DEBUG -> Color(0xFF00E5FF) to "DBG"
        LogLevel.WARN -> Color(0xFFFFB300) to "WRN"
        LogLevel.ERROR -> Color(0xFFFF3366) to "ERR"
        LogLevel.COMMAND -> Color(0xFFB388FF) to "CMD"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            entry.timestamp,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = TextDim,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            "[$lvlText]",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = lvlColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            entry.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (entry.level == LogLevel.ERROR) Color(0xFFFF5252) else if (entry.level == LogLevel.COMMAND) Color(0xFFE1BEE7) else Color(0xFFD6DBE8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ActionChipGlass(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = GlassCard,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = LiquidCyan, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPure,
                maxLines = 1
            )
        }
    }
}

