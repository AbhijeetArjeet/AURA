package com.ypdlp.downloader.aura

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ypdlp.downloader.AutoMixSession
import com.ypdlp.downloader.AutoMixTransitionMode
import com.ypdlp.downloader.MainViewModel
import com.ypdlp.downloader.PlayerState

@Composable
fun AuraAutoMixScreen(
    session: AutoMixSession,
    playerState: PlayerState,
    vm: MainViewModel
) {
    val currentTrack = playerState.currentFile

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── AutoMix Status Header ────────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF141728),
                border = BorderStroke(1.dp, Color(0xFFFF2A55).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = Color(0xFFFF2A55), modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("AURA AUTOMIX ENGINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF2A55), letterSpacing = 1.sp)
                                Text(if (session.isEnabled) "DJ Session Active" else "AutoMix Standby", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Switch(
                            checked = session.isEnabled,
                            onCheckedChange = { vm.toggleAutoMix() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF2A55)
                            )
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f))

                    // Live BPM & Energy Match
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CURRENT BPM", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text("${currentTrack?.bpm ?: 124} BPM", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TRANSITION", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text("➔ ${session.transitionDurationSec}s Blend ➔", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B2B))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TARGET BPM", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text("${(currentTrack?.bpm ?: 124) + 2} BPM", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF9D4EDD))
                        }
                    }
                }
            }
        }

        // ── Transition Mode Selector ─────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Transition Styles", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                AutoMixTransitionMode.values().forEach { mode ->
                    val isSelected = session.transitionMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { vm.setAutoMixTransitionMode(mode) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFFFF2A55).copy(alpha = 0.15f) else Color(0xFF141724),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF2A55) else Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isSelected) Color(0xFFFF2A55) else Color.White)
                                Spacer(Modifier.height(2.dp))
                                Text(mode.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFFFF2A55))
                            }
                        }
                    }
                }
            }
        }
    }
}