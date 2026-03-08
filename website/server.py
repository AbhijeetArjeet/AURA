"""
server.py — Flask API backend for YPDlp web-based YouTube downloader
"""
import os
import re
import uuid
import json
import threading
import yt_dlp
from flask import Flask, request, jsonify, send_file, send_from_directory, abort
from flask_cors import CORS

app = Flask(__name__, static_folder="static")
CORS(app)

# ─── Config ──────────────────────────────────────────────────────────────────
DOWNLOAD_DIR   = os.path.join(os.path.dirname(__file__), "downloads")
FFMPEG_PATH    = os.environ.get("FFMPEG_PATH", "")  # Set env var or leave empty (Render has ffmpeg built-in)
os.makedirs(DOWNLOAD_DIR, exist_ok=True)

# ─── PO Token (Proof of Origin) — bypasses YouTube bot detection ─────────────
PO_TOKEN       = os.environ.get("PO_TOKEN", "")
VISITOR_DATA   = os.environ.get("VISITOR_DATA", "")

# In-memory job tracker
jobs = {}  # job_id -> { status, progress, speed, eta, file_path, error, title }

# ─── Supported formats ──────────────────────────────────────────────────────
QUALITY_MAP = {
    "best":   "bestvideo+bestaudio/best",
    "2160p":  "bestvideo[height<=2160]+bestaudio/best[height<=2160]",
    "1440p":  "bestvideo[height<=1440]+bestaudio/best[height<=1440]",
    "1080p":  "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
    "720p":   "bestvideo[height<=720]+bestaudio/best[height<=720]",
    "480p":   "bestvideo[height<=480]+bestaudio/best[height<=480]",
    "360p":   "bestvideo[height<=360]+bestaudio/best[height<=360]",
}

AUDIO_FORMATS = {"mp3", "m4a", "flac", "wav", "ogg", "opus"}


def _base_opts():
    """Return common yt-dlp options including PO token if configured."""
    opts = {
        "quiet":        True,
        "no_warnings":  True,
        "noplaylist":   True,
    }
    if FFMPEG_PATH:
        opts["ffmpeg_location"] = FFMPEG_PATH
    # YouTube Proof-of-Origin token — lets cloud servers bypass bot detection
    if PO_TOKEN and VISITOR_DATA:
        opts["extractor_args"] = {
            "youtube": {
                "player_client": ["web"],
                "po_token":      [f"web+{PO_TOKEN}"],
            }
        }
        opts["extractor_args"]["youtube"]["visitor_data"] = [VISITOR_DATA]
    return opts


# ═════════════════════════════════════════════════════════════════════════════
#  ROUTES — Static pages
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/")
def index():
    return send_from_directory("static", "index.html")


@app.route("/<path:path>")
def static_files(path):
    return send_from_directory("static", path)


# ═════════════════════════════════════════════════════════════════════════════
#  API — Fetch Video Info
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/info", methods=["POST"])
def api_info():
    data = request.get_json(force=True)
    url  = data.get("url", "").strip()
    if not url:
        return jsonify({"error": "No URL provided"}), 400

    try:
        ydl_opts = _base_opts()
        ydl_opts["skip_download"] = True

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

        duration = int(info.get("duration", 0) or 0)
        hrs, rem = divmod(duration, 3600)
        mins, secs = divmod(rem, 60)
        dur_str = f"{hrs:02d}:{mins:02d}:{secs:02d}" if hrs else f"{mins:02d}:{secs:02d}"

        views = info.get("view_count", 0) or 0

        return jsonify({
            "title":     info.get("title", "Unknown"),
            "channel":   info.get("uploader", info.get("channel", "")),
            "thumbnail": info.get("thumbnail", ""),
            "duration":  dur_str,
            "views":     f"{views:,}",
            "url":       url,
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ═════════════════════════════════════════════════════════════════════════════
#  API — Supported Formats
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/formats")
def api_formats():
    return jsonify({
        "video_containers": ["mp4", "mkv", "webm", "avi"],
        "audio_containers": ["mp3", "m4a", "flac", "wav", "ogg", "opus"],
        "qualities": ["best", "2160p", "1440p", "1080p", "720p", "480p", "360p"],
    })


# ═════════════════════════════════════════════════════════════════════════════
#  API — Start Download (async job)
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/download", methods=["POST"])
def api_download():
    data = request.get_json(force=True)
    url       = data.get("url", "").strip()
    container = data.get("format", "mp4").lower()
    quality   = data.get("quality", "best").lower()

    if not url:
        return jsonify({"error": "No URL provided"}), 400

    job_id = str(uuid.uuid4())[:8]
    jobs[job_id] = {
        "status": "starting",
        "progress": 0,
        "speed": "",
        "eta": "",
        "file_path": None,
        "error": None,
        "title": "",
        "filename": "",
    }

    thread = threading.Thread(target=_run_download, args=(job_id, url, container, quality))
    thread.daemon = True
    thread.start()

    return jsonify({"job_id": job_id})


def _progress_hook(job_id, d):
    if d["status"] == "downloading":
        raw = d.get("_percent_str", "0%").strip()
        pct = int(float(re.sub(r"[^\d.]", "", raw) or 0))
        jobs[job_id]["progress"] = pct
        jobs[job_id]["speed"]    = d.get("_speed_str", "").strip()
        jobs[job_id]["eta"]      = d.get("_eta_str", "").strip()
        jobs[job_id]["status"]   = "downloading"
    elif d["status"] == "finished":
        jobs[job_id]["status"] = "processing"


def _run_download(job_id, url, container, quality):
    is_audio = container in AUDIO_FORMATS

    outtmpl = os.path.join(DOWNLOAD_DIR, f"{job_id}_%(title)s.%(ext)s")

    opts = _base_opts()
    opts["outtmpl"]         = outtmpl
    opts["progress_hooks"]  = [lambda d: _progress_hook(job_id, d)]

    if is_audio:
        opts["format"]         = "bestaudio/best"
        opts["postprocessors"] = [{
            "key":              "FFmpegExtractAudio",
            "preferredcodec":   container,
            "preferredquality": "0",
        }]
    else:
        fmt = QUALITY_MAP.get(quality, QUALITY_MAP["best"])
        opts["format"]              = fmt
        opts["merge_output_format"] = container
        opts["postprocessors"]      = [{
            "key":            "FFmpegVideoConvertor",
            "preferedformat": container,
        }]

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            title = info.get("title", "download")
            jobs[job_id]["title"] = title

        # Find the output file
        for f in os.listdir(DOWNLOAD_DIR):
            if f.startswith(job_id):
                fpath = os.path.join(DOWNLOAD_DIR, f)
                jobs[job_id]["file_path"] = fpath
                jobs[job_id]["filename"]  = f.replace(f"{job_id}_", "", 1)
                break

        jobs[job_id]["status"]   = "done"
        jobs[job_id]["progress"] = 100

    except Exception as e:
        jobs[job_id]["status"] = "error"
        jobs[job_id]["error"]  = str(e)


# ═════════════════════════════════════════════════════════════════════════════
#  API — Job Status
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/status/<job_id>")
def api_status(job_id):
    job = jobs.get(job_id)
    if not job:
        return jsonify({"error": "Job not found"}), 404
    return jsonify({
        "status":   job["status"],
        "progress": job["progress"],
        "speed":    job["speed"],
        "eta":      job["eta"],
        "error":    job["error"],
        "title":    job["title"],
        "filename": job["filename"],
    })


# ═════════════════════════════════════════════════════════════════════════════
#  API — Download the finished file
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/file/<job_id>")
def api_file(job_id):
    job = jobs.get(job_id)
    if not job or job["status"] != "done" or not job["file_path"]:
        abort(404)
    return send_file(
        job["file_path"],
        as_attachment=True,
        download_name=job["filename"],
    )


# ═════════════════════════════════════════════════════════════════════════════
#  Run
# ═════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 50)
    print("  YPDlp Web Server — http://127.0.0.1:5000")
    print("=" * 50)
    app.run(debug=True, host="0.0.0.0", port=5000)
