# YPDlp — YouTube Downloader

A full-featured YouTube downloader available as a **PC desktop app**, **Android app**, and **Website**.

---

## 📂 Project Structure

```
ypdlp/
├── desktop/      ← Windows PC app (PyQt6, builds to .exe)
├── android/      ← Android app (Kotlin, builds to .apk)
├── website/      ← Web app + Flask backend (deploy to Render.com)
└── README.md
```

---

## 🖥️ Part 1: Desktop App (Windows EXE)

A native Windows desktop application built with **PyQt6** and **yt-dlp**.

### Features
- Download videos in MP4, MKV, WebM, AVI
- Download audio in MP3, M4A, FLAC, WAV, OGG, Opus
- Quality selection: 4K, 1440p, 1080p, 720p, 480p, 360p
- Download queue with progress tracking
- Settings panel for FFmpeg path and output directory

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

> **Note:** You need [FFmpeg](https://ffmpeg.org/download.html) installed and accessible for format conversion.

---

## 📱 Part 2: Android App

A native Android app built with **Kotlin** and **Jetpack Compose**.

### Build APK
1. Open `android/` folder in **Android Studio**
2. Sync Gradle and build
3. Run on device or generate APK via **Build → Build APK(s)**

> **Note:** The Android app requires `yt-dlp` binary for the device. See the app settings.

---

## 🌐 Part 3: Website (Flask Backend)

A web-based downloader with a Flask API backend and a frontend UI.

### Run Locally
```bash
cd website
pip install -r requirements.txt
python server.py
# Open http://127.0.0.1:5000
```

### Deploy to Render.com (Free, No Credit Card)

1. Push this repo to GitHub
2. Go to [render.com](https://render.com) → Sign up with GitHub
3. Click **New → Web Service** → Connect your repo
4. Set these values:
   - **Root Directory:** `website`
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `gunicorn server:app --bind 0.0.0.0:$PORT`
5. Click **Create Web Service** → Done! 🎉

Your site will be live at `https://your-app-name.onrender.com`

---

## 🚀 GitHub Setup

```bash
git init
git add .
git commit -m "Initial commit — YPDlp YouTube Downloader"
git remote add origin https://github.com/YOUR_USERNAME/ypdlp.git
git push -u origin main
```

---

## 📦 Tech Stack

| Part | Technology |
|------|-----------|
| Desktop | Python, PyQt6, yt-dlp, PyInstaller |
| Android | Kotlin, Jetpack Compose, yt-dlp |
| Website | Python, Flask, yt-dlp, HTML/CSS/JS |

---

## ⚠️ Disclaimer

This tool is for **personal and educational use only**. Downloading copyrighted content without permission may violate YouTube's Terms of Service and local laws. Use responsibly.
