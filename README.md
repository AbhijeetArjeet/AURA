# 🎬 YPDlp — High-Performance YouTube Downloader & Media Suite

[![GitHub Release](https://img.shields.io/badge/📦_Latest_Release-v1.0.12-success?style=for-the-badge&logo=github)](https://github.com/AbhijeetArjeet/yt_downloader/releases/tag/v1.0.12)
[![Android](https://img.shields.io/badge/Android-APK_v1.0.12-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.0.12/YPDlp_Android_v1.0.12.apk)
[![Windows](https://img.shields.io/badge/Windows-Desktop_x64-0078D6?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.0.12/YPDlp_Windows_x64.zip)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

**YPDlp** is a 100% standalone, zero-telemetry, on-device YouTube downloader and offline media suite engineered for **Windows Desktop** and **Android**. It merges high-bitrate video/audio streams on-device without relying on external servers.

---

## ⚡ Direct Downloads & Releases

| Platform | Format | Package Link |
|:---|:---|:---|
| 📱 **Android** | APK (Universal) | [📥 **Download YPDlp_Android_v1.0.12.apk**](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.0.12/YPDlp_Android_v1.0.12.apk) |
| 🖥️ **Windows (x64)** | Portable ZIP | [📥 **Download YPDlp_Windows_x64.zip**](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.0.12/YPDlp_Windows_x64.zip) |
| 📦 **All Releases** | Changelogs & Assets | [📂 **GitHub Releases Page**](https://github.com/AbhijeetArjeet/yt_downloader/releases) |

---

## 📱 Android App Features (Liquid Crystal Edition)

- 💎 **Liquid Crystal Glass UI:** iOS-inspired translucent frosted glass, glowing neon accents, and smooth physics.
- 📺 **In-App Fullscreen Video Player:** Watch downloaded videos directly inside the app with timeline scrubbing, fast-forward/rewind (+/- 10s), auto-hiding controls, and aspect ratio controls.
- ⚡ **100% On-Device Standalone Engine:** Powered by embedded **Python 3.11** (`io.github.junkfood02.youtubedl-android:0.18.1`) + **FFmpeg**.
- 🧩 **QuickJS JavaScript Engine (`libqjs.so`):** Native challenge solver to bypass YouTube's throttling & player signature changes.
- 🎬 **True High-Res Extraction:** Download **4K (2160p)**, **2K (1440p)**, **1080p**, **720p**, and audio formats (**MP3, M4A, FLAC, OPUS, WAV**).
- 📁 **Full Playlist Batch Downloader:** 1-tap automated playlist extraction and batch downloading.
- 🎵 **Integrated Background Media Player:** Floating mini-player, lock-screen controls, and system notification playback.
- 📂 **In-App Media Library:** Browse, watch, share, and manage downloaded video & audio directly.
- 🛠️ **8MAN Easter Egg & Dev Terminal:** Tap Yukino to activate *Hachiman Mode* — an interactive terminal shell with diagnostic tools, cache clearing, latency testing, and live engine updating.

---

## 🖥️ Windows Desktop App Features

- ⚡ **Zero-Dependency Portable Binary:** Built with PyQt6 and PyInstaller.
- 🛠️ **Bundled Static FFmpeg:** Automatically integrates FFmpeg for seamless multi-stream merging into pristine MP4/MKV.
- 📊 **Concurrent Queue Manager:** Download multiple videos simultaneously with configurable concurrency limits and custom save destinations.
- 🎵 **Lossless Audio Extraction:** Extract direct 320kbps MP3s, FLAC, and AAC audio streams.

---

## 🗺️ Future Roadmap & Optimization Plans

Here is what is coming next in future releases of YPDlp:

### 🎯 Phase 1: In-App Player & Playback Optimization
- [ ] **Hardware-Accelerated Video Player:** Integrate VLC / ExoPlayer with gesture controls (brightness, volume swipe, double-tap seek).
- [ ] **Picture-in-Picture (PiP) Mode:** Floating overlay player for Android and Windows picture-in-picture mode.
- [ ] **Background Audio Queue & Playlist Playback:** Gapless audio playback with custom playlists and shuffle/repeat modes.
- [ ] **Equalizer & Audio Enhancer:** 10-band equalizer with bass boost and vocal clarity presets.

### 🎯 Phase 2: Engine & Network Performance
- [ ] **Multi-Segment Turbo Downloader (Aria2c integration):** Split video downloads into multiple parallel HTTP connections for up to 5x faster speeds.
- [ ] **Smart Chunk Streaming:** Stream while downloading — start playing video before the entire file finishes.
- [ ] **Subtitle & Closed Captions Downloader:** Automatic subtitle embedding (SRT/VTT) in multiple languages.
- [ ] **Thumbnail & Metadata Tagging:** Automatic ID3 tagging (Cover art, Artist, Album, Year) for music tracks.

### 🎯 Phase 3: Platform Expansion & Ecosystem
- [ ] **Browser Integration & Share Sheet:** Quick "Share to YPDlp" intent from the YouTube app / browsers on mobile and PC.
- [ ] **Channel Subscriptions & RSS Downloader:** Subscribe to your favorite creators to auto-download new uploads.
- [ ] **Android Auto & CarPlay Support:** Seamless offline music listening on the road.
- [ ] **macOS & Linux Builds:** Native cross-platform desktop builds.

---

## 🛠️ Build from Source

### Android
```bash
cd android
./gradlew assembleDebug
# Output APK: android/app/build/outputs/apk/debug/app-debug.apk
```

### Windows Desktop
```bash
cd desktop
install.bat
build.bat
# Output ZIP: desktop/dist/YPDlp_Windows_x64.zip
```

---

## ⚠️ Disclaimer

This project is intended for **personal, offline, and educational use only**. Respect content creators and applicable copyright laws.
