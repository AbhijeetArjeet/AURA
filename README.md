# YPDlp — YouTube Downloader

[![Website](https://img.shields.io/badge/🌐_Live_Website-Visit_Now-ff0000?style=for-the-badge)](https://yt-downloader-ccm6.onrender.com)
[![GitHub](https://img.shields.io/badge/GitHub-AbhijeetArjeet-181717?style=for-the-badge&logo=github)](https://github.com/AbhijeetArjeet/yt_downloader)

A full-featured YouTube downloader available as a **PC desktop app**, **Android app**, and **Website**.

### 🔗 Quick Links

| Platform | Link |
|----------|------|
| 🌐 **Website** | [**yt-downloader-ccm6.onrender.com**](https://yt-downloader-ccm6.onrender.com) |
| 🖥️ **Desktop** | [Source Code](https://github.com/AbhijeetArjeet/yt_downloader/tree/main/desktop) |
| 📱 **Android** | [Source Code](https://github.com/AbhijeetArjeet/yt_downloader/tree/main/android) |

---

## 📂 Project Structure

```
yt_downloader/
├── desktop/      ← Windows PC app (PyQt6, builds to .exe)
├── android/      ← Android app (Kotlin, builds to .apk)
├── website/      ← Web app + Flask backend (deployed on Render.com)
└── README.md
```

---

## 🖥️ Part 1: Desktop App (Windows EXE)

A native Windows desktop application built with **PyQt6** and **yt-dlp**.

### Features
- 🎬 Download videos in MP4, MKV, WebM, AVI
- 🎵 Download audio in MP3, M4A, FLAC, WAV, OGG, Opus
- 📺 Quality selection: 4K, 1440p, 1080p, 720p, 480p, 360p
- 📊 Download queue with progress tracking
- ⚙️ Settings panel for FFmpeg path and output directory

### Run from Source
```bash
cd desktop
pip install -r requirements.txt
python main.py
```

### Build EXE
```bash
cd desktop
build.bat
# Output: desktop/dist/YPDlp/YPDlp.exe
```

> **Note:** You need [FFmpeg](https://ffmpeg.org/download.html) installed for format conversion.

---

## 📱 Part 2: Android App

A native Android app built with **Kotlin** and **Jetpack Compose**.

### Build APK
1. Open `android/` folder in **Android Studio**
2. Sync Gradle and build
3. Run on device or generate APK via **Build → Build APK(s)**

> **Note:** The Android app requires `yt-dlp` binary for the device. See the app settings.

---

## 🌐 Part 3: Website

🔗 **Live at: [yt-downloader-ccm6.onrender.com](https://yt-downloader-ccm6.onrender.com)**

A web-based downloader with a Flask API backend and a beautiful frontend UI.

### Run Locally
```bash
cd website
pip install -r requirements.txt
python server.py
# Open http://127.0.0.1:5000
```

---

## 📦 Tech Stack

| Part | Technology |
|------|-----------|
| Desktop | Python, PyQt6, yt-dlp, PyInstaller |
| Android | Kotlin, Jetpack Compose, yt-dlp |
| Website | Python, Flask, yt-dlp, HTML/CSS/JS |

---

## ⭐ Star this repo!

If you found this useful, please give it a ⭐ on GitHub!

## ⚠️ Disclaimer

This tool is for **personal and educational use only**. Downloading copyrighted content without permission may violate YouTube's Terms of Service and local laws. Use responsibly.
