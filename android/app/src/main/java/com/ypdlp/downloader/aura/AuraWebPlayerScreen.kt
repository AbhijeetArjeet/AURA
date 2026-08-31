package com.ypdlp.downloader.aura

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuraWebPlayerScreen(
    initialUrl: String = "https://music.youtube.com",
    vm: com.ypdlp.downloader.MainViewModel? = null,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("YT Music Web") }

    val adBlockScript = """
        (function() {
            // Remove Ads & overlays
            const killAds = () => {
                const adSelectors = [
                    '.ytp-ad-module', '.ytp-ad-overlay-container', '.ytp-ad-player-overlay',
                    '#player-ads', '#masthead-ad', 'ytd-promoted-sparkles-web-renderer',
                    'ytd-banner-promo-renderer', '.ad-showing', '.video-ads',
                    'ytmusic-mealbar-promo-renderer', 'ytmusic-banner-renderer'
                ];
                adSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(el => el.remove());
                });
                
                const video = document.querySelector('video');
                if (video && document.querySelector('.ad-showing')) {
                    video.currentTime = video.duration || 0;
                }
                
                const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                if (skipBtn) skipBtn.click();
            };
            setInterval(killAds, 800);
        })();
    """.trimIndent()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Web Navigator Bar ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF101320),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }

                    // Web Source Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = currentUrl.contains("music.youtube.com"),
                            onClick = {
                                currentUrl = "https://music.youtube.com"
                                webViewRef?.loadUrl("https://music.youtube.com")
                            },
                            label = { Text("YT Music", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF2A55),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = currentUrl.contains("soundcloud.com"),
                            onClick = {
                                currentUrl = "https://soundcloud.com"
                                webViewRef?.loadUrl("https://soundcloud.com")
                            },
                            label = { Text("SoundCloud", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF6B2B),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = currentUrl.contains("spotify.com"),
                            onClick = {
                                currentUrl = "https://open.spotify.com"
                                webViewRef?.loadUrl("https://open.spotify.com")
                            },
                            label = { Text("Spotify", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1DB954),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color(0xFF00E5FF))
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color.Transparent
                )
            }

            // ── WebView Container with Adblock & Cookie Persistence ──
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(adBlockScript, null)
                                pageTitle = view?.title ?: "Web Player"
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val urlStr = request?.url?.toString() ?: ""
                                if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                    return false // Load inside WebView
                                }
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }
                        }

                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                update = {
                    webViewRef = it
                }
            )
        }

        // ── Floating 1-Click Audio Download Button ──
        FloatingActionButton(
            onClick = {
                val target = webViewRef?.url ?: currentUrl
                if (vm != null && target.isNotBlank()) {
                    vm.quickDownload(target, pageTitle)
                    android.widget.Toast.makeText(context, "⚡ Downloading: $pageTitle", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "URL: $target", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Color(0xFFFF2A55),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download")
                Spacer(Modifier.width(6.dp))
                Text("Download", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
