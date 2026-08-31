"""
downloader.py — yt-dlp download engine (runs in a QThread worker)
"""
import os
import re
import urllib.parse
import yt_dlp
from PyQt6.QtCore import QThread, pyqtSignal

import ffmpeg_utils


# ─────────────────────────────────────────────
#  Helpers & Mappings
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
    "4K (2160p)":   "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best",
    "2K (1440p)":   "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best",
    "1080p":        "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
    "720p":         "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
    "480p":         "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
    "360p":         "bestvideo[height<=360]+bestaudio/best[height<=360]/best",
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


def clean_url(url: str) -> str:
    """Sanitize URL and normalize YouTube URLs."""
    url = url.strip()
    if not url:
        return ""
    
    # Handle YouTube shorts
    if "youtube.com/shorts/" in url:
        video_id = url.split("youtube.com/shorts/")[1].split("?")[0].split("/")[0]
        return f"https://www.youtube.com/watch?v={video_id}"
    
    return url


# ─────────────────────────────────────────────
#  Metadata fetcher (blocking, run in thread)
# ─────────────────────────────────────────────

def fetch_info(url: str) -> dict:
    """Return video metadata without downloading."""
    url = clean_url(url)
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "extract_flat": False,
        "extractor_args": {
            "youtube": {
                "player_client": ["web", "mweb", "android", "ios"]
            }
        },
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
        self.url             = clean_url(url)
        self.output_dir      = output_dir
        self.container       = container.upper()
        self.quality         = quality
        self.audio_quality   = audio_quality
        self.ffmpeg_location = ffmpeg_location
        self._cancelled      = False

    # ── cancel ──────────────────────────────
    def cancel(self):
        self._cancelled = True

    # ── progress hook ────────────────────────
    def _progress_hook(self, d):
        if self._cancelled:
            raise yt_dlp.utils.DownloadCancelled("Download cancelled by user.")

        if d["status"] == "downloading":
            pct = 0
            downloaded = d.get("downloaded_bytes", 0)
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            if total > 0:
                pct = int((downloaded / total) * 100)
            else:
                raw_percent = d.get("_percent_str", "0%").strip()
                # Strip ANSI color codes if any
                clean_percent = re.sub(r"\x1b\[[0-9;]*m", "", raw_percent)
                match = re.search(r"([\d.]+)", clean_percent)
                pct = int(float(match.group(1))) if match else 0

            speed = d.get("_speed_str", "--").strip()
            speed = re.sub(r"\x1b\[[0-9;]*m", "", speed)
            eta   = d.get("_eta_str",   "--").strip()
            eta   = re.sub(r"\x1b\[[0-9;]*m", "", eta)

            self.progress.emit(min(max(pct, 0), 100), speed, eta)

        elif d["status"] == "finished":
            self.status.emit("Processing / Merging…")

    # ── build ydl options ────────────────────
    def _build_opts(self) -> dict:
        is_audio = self.container in AUDIO_FORMATS
        os.makedirs(self.output_dir, exist_ok=True)
        outtmpl = os.path.join(self.output_dir, "%(title)s.%(ext)s")

        opts: dict = {
            "outtmpl":         outtmpl,
            "progress_hooks":  [self._progress_hook],
            "quiet":           True,
            "no_warnings":     True,
            "noplaylist":      True,
            "extractor_args": {
                "youtube": {
                    "player_client": ["web", "mweb", "android", "ios"]
                }
            },
        }

        # Resolve FFmpeg path using our robust detector
        ffmpeg_bin = ffmpeg_utils.get_ffmpeg_path(self.ffmpeg_location)
        if ffmpeg_bin and os.path.isfile(ffmpeg_bin):
            opts["ffmpeg_location"] = ffmpeg_bin

        if is_audio:
            aq = AUDIO_QUALITIES.get(self.audio_quality, "0")
            opts["format"] = "bestaudio/best"
            if ffmpeg_bin:
                opts["postprocessors"] = [{
                    "key":            "FFmpegExtractAudio",
                    "preferredcodec": FORMAT_MAP.get(self.container, "mp3"),
                    "preferredquality": aq,
                }]
        else:
            ext = FORMAT_MAP.get(self.container, "mp4")
            if ffmpeg_bin:
                fmt = QUALITY_MAP.get(self.quality, QUALITY_MAP["Best"])
                opts["format"] = fmt
                opts["merge_output_format"] = ext
            else:
                # Fallback format for single-stream if FFmpeg is completely missing
                opts["format"] = "best[ext=mp4]/best"

        return opts

    # ── main run ─────────────────────────────
    def _ensure_ffmpeg(self) -> str:
        """Ensure FFmpeg is available.
        If not found, attempt to download it using ffmpeg_utils.download_ffmpeg_if_missing.
        Returns the path to ffmpeg.exe or raises RuntimeError.
        """
        # First, try existing location or detection
        ffmpeg_path = ffmpeg_utils.get_ffmpeg_path(self.ffmpeg_location)
        if ffmpeg_path and os.path.isfile(ffmpeg_path):
            return ffmpeg_path
        # Not found – attempt auto-download (only on Windows)
        self.status.emit("Downloading FFmpeg binary (required for high‑resolution muxing)…")
        downloaded = ffmpeg_utils.download_ffmpeg_if_missing(
            progress_callback=lambda pct, msg: self.status.emit(msg)
        )
        if downloaded and os.path.isfile(downloaded):
            # Update location for future use
            self.ffmpeg_location = downloaded
            self.status.emit("FFmpeg ready.")
            return downloaded
        raise RuntimeError("FFmpeg not found and auto‑download failed. Install FFmpeg or set ffmpeg_location.")

    def run(self):
        try:
            # Ensure ffmpeg is present before building options
            self.ffmpeg_location = self._ensure_ffmpeg()
            opts = self._build_opts()
            self.status.emit("Starting download…")
            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([self.url])
            if self._cancelled:
                self.finished.emit(False, "Cancelled.")
            else:
                self.finished.emit(True, "Download complete!")
        except RuntimeError as re:
            self.finished.emit(False, str(re))
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
