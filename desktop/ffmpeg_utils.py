"""
ffmpeg_utils.py — Utilities for finding, validating, and auto-providing FFmpeg for YPDlp
"""
import os
import sys
import shutil
import logging
import zipfile
import urllib.request
from typing import Optional, Callable

logger = logging.getLogger(__name__)

# Fallback AppData directory for storing downloaded FFmpeg
APPDATA_DIR = os.path.join(
    os.getenv("LOCALAPPDATA") or os.path.expanduser("~"),
    "YPDlp",
    "bin"
)


def get_bundled_ffmpeg_path() -> Optional[str]:
    """Check if ffmpeg is bundled with the PyInstaller package or in the app directory."""
    search_dirs = []
    
    # 1. PyInstaller _MEIPASS (onefile mode)
    if hasattr(sys, "_MEIPASS"):
        search_dirs.append(sys._MEIPASS)
        search_dirs.append(os.path.join(sys._MEIPASS, "bin"))
        search_dirs.append(os.path.join(sys._MEIPASS, "_internal"))
        search_dirs.append(os.path.join(sys._MEIPASS, "_internal", "bin"))

    # 2. Executable / script directory (onedir mode or source)
    base_dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    search_dirs.extend([
        base_dir,
        os.path.join(base_dir, "bin"),
        os.path.join(base_dir, "_internal"),
        os.path.join(base_dir, "_internal", "bin"),
        os.path.join(base_dir, "_internal", "imageio_ffmpeg", "binaries"),
        os.path.join(base_dir, "ffmpeg"),
        os.path.join(base_dir, "ffmpeg", "bin"),
    ])

    for d in search_dirs:
        if not os.path.isdir(d):
            continue
        try:
            for f in os.listdir(d):
                if f.lower().startswith("ffmpeg") and f.lower().endswith(".exe"):
                    full_path = os.path.join(d, f)
                    if os.path.isfile(full_path):
                        return full_path
        except OSError:
            continue

    return None


def get_imageio_ffmpeg_path() -> Optional[str]:
    """Check if imageio-ffmpeg package provides a valid ffmpeg binary."""
    try:
        import imageio_ffmpeg
        exe = imageio_ffmpeg.get_ffmpeg_exe()
        if exe and os.path.isfile(exe):
            return exe
    except Exception:
        pass
    return None


def get_appdata_ffmpeg_path() -> Optional[str]:
    """Check if ffmpeg exists in user's AppData directory."""
    candidate = os.path.join(APPDATA_DIR, "ffmpeg.exe")
    if os.path.isfile(candidate):
        return candidate
    return None


def get_system_ffmpeg_path() -> Optional[str]:
    """Check if ffmpeg is available in system PATH."""
    which_path = shutil.which("ffmpeg")
    if which_path and os.path.isfile(which_path):
        return which_path
    return None


def get_ffmpeg_path(custom_path: str = "") -> str:
    """
    Resolve the best available FFmpeg path.
    
    Order of preference:
    1. Valid custom path provided by user settings (file or directory)
    2. Bundled with application (PyInstaller / local bin / _internal)
    3. imageio-ffmpeg package binary
    4. AppData / local cache binary
    5. System PATH
    """
    # 1. User custom setting
    if custom_path:
        custom_path = custom_path.strip('"').strip("'").strip()
        if os.path.isfile(custom_path):
            return custom_path
        if os.path.isdir(custom_path):
            exe_in_dir = os.path.join(custom_path, "ffmpeg.exe")
            if os.path.isfile(exe_in_dir):
                return exe_in_dir

    # 2. Bundled in PyInstaller / App directory
    bundled = get_bundled_ffmpeg_path()
    if bundled:
        return bundled

    # 3. imageio-ffmpeg
    img_ff = get_imageio_ffmpeg_path()
    if img_ff:
        return img_ff

    # 4. AppData
    appdata_ff = get_appdata_ffmpeg_path()
    if appdata_ff:
        return appdata_ff

    # 5. System PATH
    sys_ff = get_system_ffmpeg_path()
    if sys_ff:
        return sys_ff

    return ""


def has_ffmpeg(custom_path: str = "") -> bool:
    """Check if FFmpeg is available and executable."""
    path = get_ffmpeg_path(custom_path)
    return bool(path and os.path.isfile(path))


def download_ffmpeg_if_missing(progress_callback: Optional[Callable[[int, str], None]] = None) -> Optional[str]:
    """
    Download a standalone FFmpeg Windows build if not present on system.
    Installs into %LOCALAPPDATA%/YPDlp/bin/ffmpeg.exe
    """
    current = get_ffmpeg_path()
    if current and os.path.isfile(current):
        return current

    os.makedirs(APPDATA_DIR, exist_ok=True)
    target_exe = os.path.join(APPDATA_DIR, "ffmpeg.exe")
    if os.path.isfile(target_exe):
        return target_exe

    # Download URL for static Windows build (using GitHub release mirror)
    url = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    zip_path = os.path.join(APPDATA_DIR, "ffmpeg.zip")

    if progress_callback:
        progress_callback(5, "Connecting to download FFmpeg...")

    try:
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) YPDlp/1.0"}
        req = urllib.request.Request(url, headers=headers)
        
        with urllib.request.urlopen(req, timeout=30) as resp, open(zip_path, "wb") as out_file:
            total_size = int(resp.headers.get("Content-Length", 0))
            downloaded = 0
            block_size = 1024 * 1024  # 1MB blocks

            while True:
                chunk = resp.read(block_size)
                if not chunk:
                    break
                out_file.write(chunk)
                downloaded += len(chunk)
                if total_size > 0 and progress_callback:
                    pct = int(10 + (downloaded / total_size) * 75)
                    progress_callback(pct, f"Downloading FFmpeg... {downloaded // (1024*1024)}MB / {total_size // (1024*1024)}MB")

        if progress_callback:
            progress_callback(90, "Extracting FFmpeg binaries...")

        with zipfile.ZipFile(zip_path, "r") as zf:
            for member in zf.namelist():
                if member.endswith("ffmpeg.exe") or member.endswith("ffprobe.exe"):
                    filename = os.path.basename(member)
                    with zf.open(member) as src, open(os.path.join(APPDATA_DIR, filename), "wb") as dst:
                        shutil.copyfileobj(src, dst)

        # Cleanup zip file
        try:
            os.remove(zip_path)
        except OSError:
            pass

        if progress_callback:
            progress_callback(100, "FFmpeg ready!")

        if os.path.isfile(target_exe):
            return target_exe

    except Exception as e:
        logger.error(f"Failed to auto-download FFmpeg: {e}")
        if progress_callback:
            progress_callback(-1, f"FFmpeg download failed: {e}")

    return None
