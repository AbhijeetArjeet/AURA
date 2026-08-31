package com.ypdlp.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

private val TextPure       = Color(0xFFFFFFFF)
private val TextMuted      = Color(0xFFA0A5B8)
private val TextDim        = Color(0xFF6B7280)

private val RedGradient = Brush.horizontalGradient(
    listOf(LiquidNeonRed, LiquidNeonOrange)
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

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(prefilledUrl) {
        prefilledUrl?.let {
            vm.onUrlChange(it)
            vm.fetchInfo()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshBackgroundGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                Column {
                    // Floating Liquid Glass Mini-Player (if playing audio/video in background)
                    AnimatedVisibility(
                        visible = playerState.currentFile != null,
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
                                onClose = vm::stopPlayback
                            )
                        }
                    }

                    // iOS Glass Capsule Bottom Navigation
                    GlassBottomNav(
                        selected = selectedTab,
                        onSelect = { selectedTab = it },
                        queueBadgeCount = queue.count {
                            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                        },
                        downloadsCount = ui.downloadedFiles.size
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
                        0 -> DownloadTab(ui = ui, vm = vm)
                        1 -> QueueTab(queue = queue, vm = vm)
                        2 -> LibraryTab(ui = ui, vm = vm)
                    }
                }
            }
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
//  Glass Top Bar with Glow & Settings
// ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(RedGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "YPDlp",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = TextPure,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Liquid Crystal Edition",
                        fontSize = 10.sp,
                        color = LiquidCyan,
                        fontWeight = FontWeight.Medium
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
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

// ────────────────────────────────────────────────────────────────────
//  iOS Glass Capsule Bottom Navigation Bar
// ────────────────────────────────────────────────────────────────────
@Composable
fun GlassBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    queueBadgeCount: Int,
    downloadsCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = LiquidNeonRed.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF141724).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, GlassHighlight)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavCapsuleItem(
                    selected = selected == 0,
                    icon = Icons.Filled.Download,
                    label = "Download",
                    onClick = { onSelect(0) }
                )
                NavCapsuleItem(
                    selected = selected == 1,
                    icon = Icons.Filled.FormatListBulleted,
                    label = "Queue",
                    badge = if (queueBadgeCount > 0) "$queueBadgeCount" else null,
                    onClick = { onSelect(1) }
                )
                NavCapsuleItem(
                    selected = selected == 2,
                    icon = Icons.Filled.VideoLibrary,
                    label = "Downloads",
                    badge = if (downloadsCount > 0) "$downloadsCount" else null,
                    onClick = { onSelect(2) }
                )
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
    onClick: () -> Unit
) {
    val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "navBg")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "navScale")

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) RedGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(
                badge = {
                    badge?.let {
                        Badge(containerColor = if (selected) Color.White else LiquidNeonRed) {
                            Text(it, color = if (selected) LiquidNeonRed else Color.White, fontSize = 9.sp)
                        }
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) Color.White else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
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
fun LibraryTab(ui: UiState, vm: MainViewModel) {
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
                            vm.playMediaFile(file)
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
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Icon / Play Trigger
            Box(
                modifier = Modifier
                    .size(54.dp)
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
    }
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
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp)),
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

            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (file.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = LiquidCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
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
                        if (isPlaying) "Playing in background" else "Paused",
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
