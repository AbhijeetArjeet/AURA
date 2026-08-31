package com.ypdlp.downloader.aura

import androidx.compose.foundation.*
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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ypdlp.downloader.DownloadedFile
import com.ypdlp.downloader.MainViewModel
import com.ypdlp.downloader.MoodType
import com.ypdlp.downloader.UiState
import java.util.Calendar

@Composable
fun AuraHomeScreen(
    ui: UiState,
    vm: MainViewModel,
    onOpenAutoMix: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late Night Atmosphere"
        }
    }

    var magicPrompt by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Personalized Hero Greeting ───────────────────────────────────────
        item {
            Column {
                Text(
                    greeting.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ready for your music universe?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        // ── Search & Filter Songs/Folders ─────────────────────────────────
        item {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.setSearchQuery(it)
                },
                placeholder = { Text("Search songs, artists, albums, or folders...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF00E5FF)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = {
                            query = ""
                            vm.setSearchQuery("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF141726).copy(alpha = 0.7f),
                    unfocusedContainerColor = Color(0xFF141726).copy(alpha = 0.7f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
        }

        // ── ✨ Magic Playlist Natural Prompt Input ───────────────────────────
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161928).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF9D4EDD), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("✨ Magic AI Playlist", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    OutlinedTextField(
                        value = magicPrompt,
                        onValueChange = { magicPrompt = it },
                        placeholder = { Text("e.g. \"Walking alone at 2 AM\" or \"Anime training arc\"", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9D4EDD),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (magicPrompt.isNotBlank()) {
                                IconButton(onClick = {
                                    vm.generateMagicPlaylist(magicPrompt)
                                    magicPrompt = ""
                                }) {
                                    Icon(Icons.Filled.ArrowForward, contentDescription = "Generate", tint = Color(0xFF00E5FF))
                                }
                            }
                        }
                    )
                }
            }
        }

        // ── Quick Action Hero Cards (AutoMix & AI DJ) ────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // AutoMix DJ Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            vm.toggleAutoMix()
                            onOpenAutoMix()
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF2A55), Color(0xFFFF6B2B))
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Column {
                                Text("🎧 AutoMix Room", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Text("DJ Transitions & BPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                // AI DJ Session Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { vm.startAiDjSession() },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF9D4EDD), Color(0xFF00E5FF))
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Column {
                                Text("🎙️ AURA AI DJ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Text("Atmospheric Sets", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }

        // ── Mood-Based Playlists ─────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Vibe & Mood Playlists", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(MoodType.values()) { mood ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val playlist = MagicPlaylistEngine.generateMoodPlaylist(mood, ui.downloadedFiles)
                                    playlist.tracks.firstOrNull()?.let { vm.playMediaFile(it) }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF181B2B).copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mood.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(mood.title.split(" ").first(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // ── Recently Added Local Music ───────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Local Tracks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        uri?.let {
                            try {
                                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                context.contentResolver.takePersistableUriPermission(it, flags)
                            } catch (e: Exception) {
                                // Ignore if not persistable
                            }
                            vm.addCustomMusicFolder(it.toString())
                        }
                    }

                    OutlinedButton(
                        onClick = { launcher.launch(null) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Add Folder", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }

                if (ui.downloadedFiles.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141724).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📁 No local music found yet", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Use the Downloader tab to fetch songs or drop files in Downloads/YPDlp", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    val displayedTracks = if (ui.searchQuery.isBlank()) {
                        ui.downloadedFiles
                    } else {
                        ui.downloadedFiles.filter {
                            it.title.contains(ui.searchQuery, ignoreCase = true) ||
                            it.artist.contains(ui.searchQuery, ignoreCase = true) ||
                            it.album.contains(ui.searchQuery, ignoreCase = true) ||
                            it.path.contains(ui.searchQuery, ignoreCase = true)
                        }
                    }

                    displayedTracks.forEach { track ->
                        AuraTrackRow(
                            track = track,
                            isFavorite = track.path in ui.favorites,
                            onClick = {
                                vm.playMediaFile(track)
                                onOpenNowPlaying()
                            },
                            onToggleFav = { vm.toggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuraTrackRow(
    track: DownloadedFile,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF121422).copy(alpha = 0.60f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2235)),
                contentAlignment = Alignment.Center
            ) {
                if (track.artworkByteArray != null) {
                    coil.compose.AsyncImage(
                        model = track.artworkByteArray,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        if (track.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.artist} • ${track.bpm} BPM",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF2A55) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}