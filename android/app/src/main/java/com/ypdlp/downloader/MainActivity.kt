package com.ypdlp.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

// ────────────────────────────────────────────────────────────────────
//  Colors  (YouTube dark palette)
// ────────────────────────────────────────────────────────────────────
private val YTRed        = Color(0xFFFF0000)
private val YTDark       = Color(0xFF0F0F0F)
private val YTSurface    = Color(0xFF181818)
private val YTCard       = Color(0xFF212121)
private val YTBorder     = Color(0xFF282828)
private val YTTextPrim   = Color(0xFFE8E8E8)
private val YTTextSec    = Color(0xFFAAAAAA)
private val YTGreen      = Color(0xFF44BB44)
private val YTOrange     = Color(0xFFFFAA00)

private val DarkColorScheme = darkColorScheme(
    primary         = YTRed,
    background      = YTDark,
    surface         = YTSurface,
    onBackground    = YTTextPrim,
    onSurface       = YTTextPrim,
    onPrimary       = Color.White,
    secondary       = YTCard,
)

// ────────────────────────────────────────────────────────────────────
//  Activity
// ────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle share intent from other apps (YouTube share button)
        val sharedUrl = intent
            .takeIf { it?.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            MaterialTheme(colorScheme = DarkColorScheme) {
                YPDlpApp(prefilledUrl = sharedUrl)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Root App  (Tab navigation)
// ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YPDlpApp(
    prefilledUrl: String? = null,
    vm: MainViewModel = viewModel()
) {
    val ui    by vm.ui.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(prefilledUrl) {
        prefilledUrl?.let {
            vm.onUrlChange(it)
            vm.fetchInfo()
        }
    }

    Scaffold(
        containerColor = YTDark,
        topBar = { YTTopBar() },
        bottomBar = {
            YTBottomNav(selected = selectedTab, onSelect = { selectedTab = it },
                badgeCount = queue.count {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                })
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DownloadTab(ui = ui, vm = vm)
                1 -> QueueTab(queue = queue, vm = vm)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Top Bar  (YouTube-style)
// ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null,
                    tint = YTRed, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("YPDlp", fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = YTTextPrim)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = YTDark),
        actions = {
            Text("4K · MKV · MP3", fontSize = 11.sp, color = YTTextSec,
                modifier = Modifier.padding(end = 12.dp))
        }
    )
}

// ────────────────────────────────────────────────────────────────────
//  Bottom Nav
// ────────────────────────────────────────────────────────────────────
@Composable
fun YTBottomNav(selected: Int, onSelect: (Int) -> Int, badgeCount: Int) {
    NavigationBar(containerColor = YTSurface, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selected == 0,
            onClick  = { onSelect(0) },
            icon     = { Icon(Icons.Filled.Download, null) },
            label    = { Text("Download") },
            colors   = NavigationBarItemDefaults.colors(indicatorColor = YTRed)
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick  = { onSelect(1) },
            icon     = {
                BadgedBox(badge = {
                    if (badgeCount > 0) Badge(containerColor = YTRed) { Text("$badgeCount") }
                }) { Icon(Icons.Filled.List, null) }
            },
            label    = { Text("Queue") },
            colors   = NavigationBarItemDefaults.colors(indicatorColor = YTRed)
        )
    }
}

// ────────────────────────────────────────────────────────────────────
//  Download Tab
// ────────────────────────────────────────────────────────────────────
@Composable
fun DownloadTab(ui: UiState, vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(YTDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── URL Input card ──────────────────────────────────────
        item {
            YTCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste URL", fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = YTTextSec)
                    OutlinedTextField(
                        value = ui.urlText,
                        onValueChange = vm::onUrlChange,
                        placeholder = { Text("https://youtube.com/watch?v=…", color = Color(0xFF555)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = YTRed,
                            unfocusedBorderColor = YTBorder,
                            focusedTextColor     = YTTextPrim,
                            unfocusedTextColor   = YTTextPrim,
                            cursorColor          = YTRed,
                            focusedContainerColor   = YTCard,
                            unfocusedContainerColor = YTCard,
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = vm::fetchInfo,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YTRed),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !ui.isLoadingInfo
                    ) {
                        if (ui.isLoadingInfo) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (ui.isLoadingInfo) "Fetching…" else "Fetch Video Info",
                            fontWeight = FontWeight.Bold)
                    }
                    ui.infoError?.let {
                        Text("Error: $it", color = Color(0xFFFF4444), fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Video info card ─────────────────────────────────────
        ui.videoInfo?.let { info ->
            item {
                VideoInfoCard(info)
            }

            // ── Format selector card ────────────────────────────
            item {
                YTCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select Format", fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = YTTextSec)

                        // Type toggle
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(DownloadType.VIDEO to "🎬 Video",
                                   DownloadType.AUDIO to "🎵 Audio Only").forEach { (t, label) ->
                                FilterChipYT(
                                    selected = ui.selectedType == t,
                                    label    = label,
                                    onClick  = { vm.setType(t) }
                                )
                            }
                        }

                        if (ui.selectedType == DownloadType.VIDEO) {
                            // Quality
                            DropdownRowYT("Quality", ui.selectedQuality,
                                listOf("Best","4K (2160p)","2K (1440p)","1080p","720p","480p","360p"),
                                onPick = vm::setQuality)
                            // Container
                            DropdownRowYT("Format", ui.selectedContainer,
                                listOf("MP4","MKV","WEBM","AVI"),
                                onPick = vm::setContainer)
                        } else {
                            DropdownRowYT("Format", ui.selectedContainer,
                                listOf("MP3","M4A","FLAC","WAV","OGG","OPUS"),
                                onPick = vm::setContainer)
                        }

                        Button(
                            onClick = vm::addToQueue,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = YTRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to Download Queue", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Video Info Card
// ────────────────────────────────────────────────────────────────────
@Composable
fun VideoInfoCard(info: VideoInfo) {
    YTCard {
        Column {
            if (info.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = YTTextPrim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (info.channel.isNotBlank()) {
                        Text("📺 ${info.channel}", fontSize = 12.sp, color = YTTextSec)
                    }
                    if (info.durationSeconds > 0) {
                        val m = info.durationSeconds / 60; val s = info.durationSeconds % 60
                        Text("⏱ %02d:%02d".format(m, s), fontSize = 12.sp, color = YTTextSec)
                    }
                }
                if (info.viewCount > 0) {
                    Text("👁 %,d views".format(info.viewCount),
                        fontSize = 11.sp, color = Color(0xFF666666))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Queue Tab
// ────────────────────────────────────────────────────────────────────
@Composable
fun QueueTab(queue: List<DownloadItem>, vm: MainViewModel) {
    Column(Modifier.fillMaxSize().background(YTDark).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Download Queue", fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = YTTextPrim)
            TextButton(onClick = vm::clearDone) {
                Text("Clear Done", color = YTTextSec, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty.\nPaste a URL and add items.", color = Color(0xFF444444),
                    fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(queue, key = { it.id }) { item ->
                    QueueItemCard(item = item, onCancel = { vm.cancelItem(item.id) })
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Queue Item Card
// ────────────────────────────────────────────────────────────────────
@Composable
fun QueueItemCard(item: DownloadItem, onCancel: () -> Unit) {
    YTCard {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail or icon
            Box(Modifier.size(72.dp, 48.dp).clip(RoundedCornerShape(8.dp))
                    .background(YTCard), contentAlignment = Alignment.Center) {
                if (item.videoInfo.thumbnailUrl.isNotBlank()) {
                    AsyncImage(model = item.videoInfo.thumbnailUrl,
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.PlayCircle, null, tint = Color(0xFF444444),
                        modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.videoInfo.title.ifBlank { item.videoInfo.url.takeLast(35) },
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = YTTextPrim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(item)
                    FormatBadge(item.container)
                    if (item.quality.isNotBlank() && item.videoInfo.url.isNotBlank())
                        FormatBadge(item.quality)
                }
                // Progress
                if (item.status == DownloadStatus.DOWNLOADING ||
                    item.status == DownloadStatus.POST_PROCESSING) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color    = YTRed,
                        trackColor = YTBorder,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.progress}%  ETA ${item.eta}",
                            fontSize = 10.sp, color = YTTextSec)
                        Text(item.speed, fontSize = 10.sp, color = YTTextSec)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Cancel", tint = Color(0xFFFF4444),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//  Reusable small composables
// ────────────────────────────────────────────────────────────────────

@Composable
fun YTCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = YTSurface),
        border   = BorderStroke(1.dp, YTBorder)
    ) { content() }
}

@Composable
fun FilterChipYT(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label    = { Text(label, fontSize = 13.sp) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor  = YTRed,
            selectedLabelColor      = Color.White,
            containerColor          = YTCard,
            labelColor              = YTTextSec,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = YTBorder, selectedBorderColor = YTRed
        )
    )
}

@Composable
fun DropdownRowYT(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = YTTextSec)
        Box {
            OutlinedButton(onClick = { expanded = true },
                border = BorderStroke(1.dp, YTBorder),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = YTTextPrim,
                    containerColor = YTCard)
            ) {
                Text(current, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                containerColor = YTCard) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = YTTextPrim, fontSize = 13.sp) },
                        onClick = { onPick(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(item: DownloadItem) {
    val (txt, color) = when (item.status) {
        DownloadStatus.QUEUED           -> "Queued"       to YTTextSec
        DownloadStatus.DOWNLOADING      -> "Downloading"  to YTOrange
        DownloadStatus.POST_PROCESSING  -> "Processing"   to YTOrange
        DownloadStatus.DONE             -> "✔ Done"       to YTGreen
        DownloadStatus.ERROR            -> "✘ Error"      to Color(0xFFFF4444)
        DownloadStatus.CANCELLED        -> "Cancelled"    to Color(0xFF666666)
    }
    Text(txt, fontSize = 10.sp, color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp))
}

@Composable
fun FormatBadge(label: String) {
    Text(label, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(YTRed, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp))
}
