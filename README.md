# 🎧 AURA & YPDlp — Premium Music Universe & High-Performance Downloader

[![GitHub Release](https://img.shields.io/badge/📦_Latest_Release-v1.1.0-success?style=for-the-badge&logo=github)](https://github.com/AbhijeetArjeet/yt_downloader/releases/tag/v1.1.0)
[![Android](https://img.shields.io/badge/Android-APK_v1.1.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.1.0/AURA_YPDlp_Android_v1.1.0.apk)
[![Windows](https://img.shields.io/badge/Windows-Desktop_x64-0078D6?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.1.0/YPDlp_Windows_x64.zip)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

**AURA** is a Spotify/Apple Music/Winamp-inspired personal music universe combined with **YPDlp**'s 100% on-device standalone downloader. It turns your local music and video collection into an intelligent, evolving soundscape with real-time AutoMix DJ transitions, canvas visualizers, dynamic album art gradients, and an Otaku Mode.

---

## ⚡ Direct Downloads & Releases

| Platform | Format | Package Link |
|:---|:---|:---|
| 📱 **Android** | APK (Universal) | [📥 **Download AURA_YPDlp_Android_v1.1.0.apk**](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.1.0/AURA_YPDlp_Android_v1.1.0.apk) |
| 🖥️ **Windows (x64)** | Portable ZIP | [📥 **Download YPDlp_Windows_x64.zip**](https://github.com/AbhijeetArjeet/yt_downloader/releases/download/v1.1.0/YPDlp_Windows_x64.zip) |
| 📦 **All Releases** | Changelogs & Assets | [📂 **GitHub Releases Page**](https://github.com/AbhijeetArjeet/yt_downloader/releases) |

---

## 🎧 AURA Music Universe & Android Features

- 🌌 **Dynamic Album Art Aura:** Extracts vibrant dominant colors from album covers to render seamless glowing atmospheric gradients.
- 🎧 **AutoMix DJ Engine:** Live beat-matching and crossfade transitions (*Smooth Blend, Beat Match, DJ Sweep, Cinematic, Chill Wave, Hard Cut*) with configurable blend duration.
- 🎛️ **Hardware-Accelerated Visualizers:** 5 interactive Canvas visualizer modes:
  - 📊 **Spectrum:** Glowing audio frequency bars
  - 🌊 **Waveform:** Oscilloscope sound wave
  - 🪐 **Orbital Aura:** Circular orbit surrounding artwork
  - ✨ **Starfield:** Bass-reactive floating particles
  - 〰️ **Minimal Pulse:** Clean subtle rhythm pulse
- 🎙️ **AURA AI DJ:** Atmospheric contextual listening sessions based on time of day, mood, and listening habits.
- ✨ **Magic Playlist:** Generate custom playlists using natural language prompts (*"Walking alone at 2 AM", "Anime training arc", "Deep coding flow"*).
- 🌸 **Dedicated Otaku Mode:** Animated Sakura atmosphere, Japanese typography subtitles (`「本物が欲しい」`), anime OST auto-categorization, and Hachiman/Yukino companion commentary.
- 📺 **Dual Audio & Music Video Switching:** Seamlessly toggle between audio-only listening and fullscreen video mode for downloaded MP4 music videos.
- ⚡ **100% On-Device Standalone Downloader:** Integrated Python 3.11 + QuickJS + FFmpeg for downloading lossless MP3s, 1080p, and 4K videos directly into your AURA library.

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
