"""
downloader.py — yt-dlp download engine (runs in a QThread worker)
"""
import os
import re
import yt_dlp
from PyQt6.QtCore import QThread, pyqtSignal

# ── Hardcoded FFmpeg location (fallback if Settings is empty) ──────────────
FFMPEG_FALLBACK = r"C:\Users\hp\Downloads\ypdlp\ffmpeg\bin"


# ─────────────────────────────────────────────
#  Helpers
# ─────────────────────────────────────────────

FORMAT_MAP = {
    # Video containers
    "MP4":  "mp4",
    "MKV":  "mkv",
    "WEBM": "webm",
    "AVI":  "avi",
    # Audio containers
    "MP3":  "mp3",
    "M4A":  "m4a",
    "FLAC": "flac",
    "WAV":  "wav",
    "OGG":  "vorbis",
    "OPUS": "opus",
}

QUALITY_MAP = {
    "Best":         "bestvideo+bestaudio/best",
    "4K (2160p)":   "bestvideo[height<=2160]+bestaudio/best[height<=2160]",
    "2K (1440p)":   "bestvideo[height<=1440]+bestaudio/best[height<=1440]",
    "1080p":        "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
    "720p":         "bestvideo[height<=720]+bestaudio/best[height<=720]",
    "480p":         "bestvideo[height<=480]+bestaudio/best[height<=480]",
    "360p":         "bestvideo[height<=360]+bestaudio/best[height<=360]",
}

AUDIO_QUALITIES = {
    "Best":     "0",
    "320 kbps": "0",
    "256 kbps": "2",
    "192 kbps": "4",
    "128 kbps": "5",
    "96 kbps":  "7",
}

AUDIO_FORMATS = {"MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS"}


# ─────────────────────────────────────────────
#  Metadata fetcher (blocking, run in thread)
# ─────────────────────────────────────────────

def fetch_info(url: str) -> dict:
    """Return video metadata without downloading."""
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
    return info


# ─────────────────────────────────────────────
#  Download Worker
# ─────────────────────────────────────────────

class DownloadWorker(QThread):
    """
    QThread-based yt-dlp download worker.

    Signals:
        progress(int percent, str speed, str eta)
        finished(bool success, str message)
        status(str message)
    """
    progress = pyqtSignal(int, str, str)   # percent, speed, eta
    finished = pyqtSignal(bool, str)       # success, message
    status   = pyqtSignal(str)             # status text

    def __init__(
        self,
        url: str,
        output_dir: str,
        container: str,           # e.g. "MP4", "MP3", "MKV" …
        quality: str,             # key from QUALITY_MAP / AUDIO_QUALITIES
        audio_quality: str = "Best",
        ffmpeg_location: str = "",
        parent=None,
    ):
        super().__init__(parent)
        self.url            = url
        self.output_dir     = output_dir
        self.container      = container.upper()
        self.quality        = quality
        self.audio_quality  = audio_quality
        self.ffmpeg_location = ffmpeg_location
        self._cancelled     = False

    # ── cancel ──────────────────────────────
    def cancel(self):
        self._cancelled = True
        self.terminate()

    # ── progress hook ────────────────────────
    def _progress_hook(self, d):
        if self._cancelled:
            raise yt_dlp.utils.DownloadCancelled()

        if d["status"] == "downloading":
            raw_percent = d.get("_percent_str", "0%").strip()
            pct = int(float(re.sub(r"[^\d.]", "", raw_percent) or 0))
            speed = d.get("_speed_str", "--").strip()
            eta   = d.get("_eta_str",   "--").strip()
            self.progress.emit(pct, speed, eta)

        elif d["status"] == "finished":
            self.status.emit("Post-processing…")

    # ── build ydl options ────────────────────
    def _build_opts(self) -> dict:
        is_audio = self.container in AUDIO_FORMATS

        outtmpl = os.path.join(self.output_dir, "%(title)s.%(ext)s")

        opts: dict = {
            "outtmpl":         outtmpl,
            "progress_hooks":  [self._progress_hook],
            "quiet":           True,
            "no_warnings":     True,
            "noplaylist":      True,
        }

        # Use explicit path → fallback to hardcoded path (if exists) → let yt-dlp find in PATH
        ffmpeg = self.ffmpeg_location or (FFMPEG_FALLBACK if os.path.exists(FFMPEG_FALLBACK) else "")
        if ffmpeg and os.path.exists(ffmpeg):
            opts["ffmpeg_location"] = ffmpeg

        if is_audio:
            aq = AUDIO_QUALITIES.get(self.audio_quality, "0")
            opts["format"]          = "bestaudio/best"
            opts["postprocessors"]  = [{
                "key":            "FFmpegExtractAudio",
                "preferredcodec": FORMAT_MAP[self.container],
                "preferredquality": aq,
            }]
        else:
            fmt = QUALITY_MAP.get(self.quality, QUALITY_MAP["Best"])
            ext = FORMAT_MAP[self.container]
            opts["format"] = fmt
            opts["merge_output_format"] = ext
            opts["postprocessors"] = [{
                "key":              "FFmpegVideoConvertor",
                "preferedformat":   ext,
            }]

        return opts

    # ── main run ─────────────────────────────
    def run(self):
        try:
            opts = self._build_opts()
            self.status.emit("Starting download…")
            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([self.url])
            self.finished.emit(True, "Download complete!")
        except yt_dlp.utils.DownloadCancelled:
            self.finished.emit(False, "Cancelled.")
        except Exception as e:
            self.finished.emit(False, str(e))


# ─────────────────────────────────────────────
#  Info Worker (async metadata fetch)
# ─────────────────────────────────────────────

class InfoWorker(QThread):
    """Fetch video info in background and emit result."""
    result = pyqtSignal(dict)   # info dict
    error  = pyqtSignal(str)    # error message

    def __init__(self, url: str, parent=None):
        super().__init__(parent)
        self.url = url

    def run(self):
        try:
            info = fetch_info(self.url)
            self.result.emit(info)
        except Exception as e:
            self.error.emit(str(e))
