# 🖥️ YPDlp — Desktop YouTube Downloader

A powerful, modern **Windows desktop application** for downloading YouTube videos and audio in any format and quality.

![App Preview](preview.png)

---

## ✨ Features

- 🎬 **Video Download** — MP4, MKV, WebM, AVI
- 🎵 **Audio Extract** — MP3, M4A, FLAC, WAV, OGG, Opus
- 📺 **Quality Selection** — 4K (2160p), 1440p, 1080p, 720p, 480p, 360p
- 📋 **Paste & Go** — Paste URL from clipboard with one click
- 🎯 **Download Queue** — Download multiple videos simultaneously  
- 📊 **Progress Tracking** — Real-time progress, speed, and ETA
- 🖼️ **Video Preview** — Thumbnail, title, channel, duration, view count
- ⚙️ **Settings** — Configurable output directory and FFmpeg path
- 🌙 **Dark Theme** — Beautiful modern dark UI with red accents

---

## 📦 Requirements

- **Python 3.9+**
- **FFmpeg** — Required for format conversion ([download here](https://ffmpeg.org/download.html))

---

## 🚀 Quick Start

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Run the App
```bash
python main.py
```
Or double-click **`run.bat`**

### 3. Configure FFmpeg
- Open **⚙ Settings** in the app
- Set the path to your FFmpeg `bin` folder (e.g., `C:\ffmpeg\bin`)

---

## 🏗️ Build Standalone EXE

Build a portable `.exe` that runs without Python installed:

```bash
build.bat
```

The output will be in `dist/YPDlp/YPDlp.exe`

> **Tip:** You can distribute this folder — users just need FFmpeg on their system.

---

## 📁 Project Files

| File | Description |
|------|-------------|
| `main.py` | Entry point — launches the app |
| `ui_main.py` | Main window UI and download queue |
| `ui_settings.py` | Settings dialog |
| `downloader.py` | yt-dlp download engine |
| `thumbnail_loader.py` | Async thumbnail fetcher |
| `build.bat` | One-click EXE builder |
| `ypdlp.spec` | PyInstaller config |

---

## 🛠️ Tech Stack

| Technology | Purpose |
|-----------|---------|
| [PyQt6](https://pypi.org/project/PyQt6/) | GUI framework |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Video/audio download engine |
| [Pillow](https://pillow.readthedocs.io/) | Image processing |
| [PyInstaller](https://pyinstaller.org/) | EXE packaging |

---

## 📸 How to Use

1. **Paste** a YouTube URL (or any supported site)
2. **Click** "Fetch Info" to load video details
3. **Choose** format (MP4, MKV, MP3, etc.) and quality (4K, 1080p, etc.)
4. **Click** "Add to Queue" — download starts automatically
5. Files are saved to your configured output folder

---

## ⚠️ Disclaimer

This tool is for **personal and educational use only**. Downloading copyrighted content without permission may violate YouTube's Terms of Service and applicable laws.

---

## 📄 License

MIT License — Feel free to use, modify, and distribute.
