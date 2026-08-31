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
from werkzeug.utils import secure_filename
from werkzeug.exceptions import HTTPException

app = Flask(__name__, static_folder="static")
CORS(app)

@app.errorhandler(Exception)
def handle_exception(e):
    if isinstance(e, HTTPException):
        return jsonify({"error": e.description}), e.code
    import traceback
    traceback.print_exc()
    return jsonify({"error": f"Internal Server Error: {str(e)}"}), 500

# ─── Config ──────────────────────────────────────────────────────────────────
DOWNLOAD_DIR   = os.path.join(os.path.dirname(__file__), "downloads")
FFMPEG_PATH    = os.environ.get("FFMPEG_PATH", "")  # Set env var or leave empty (Render has ffmpeg built-in)
os.makedirs(DOWNLOAD_DIR, exist_ok=True)

# ─── PO Token (Proof of Origin) — bypasses YouTube bot detection ─────────────
# Loaded from env vars at startup; can be overridden at runtime via /api/set-po
# NOTE: On Render free tier, runtime overrides are RAM-only and reset on restart.
# Set PO_TOKEN and VISITOR_DATA as persistent Render environment variables instead.
GLOBAL_PO_TOKEN     = os.environ.get("PO_TOKEN", "")
GLOBAL_VISITOR_DATA = os.environ.get("VISITOR_DATA", "")

# File to persist PO token across restarts (best-effort; ephemeral on free Render)
PO_TOKEN_FILE = os.path.join(os.path.dirname(__file__), "po_token.json")

# ─── Cookie file (backup auth) ───────────────────────────────────────────────
COOKIES_FILE   = os.path.join(os.path.dirname(__file__), "cookies.txt")

# Try loading saved PO token from file if not set via env vars
if not GLOBAL_PO_TOKEN and os.path.isfile(PO_TOKEN_FILE):
    try:
        with open(PO_TOKEN_FILE) as f:
            _saved = json.load(f)
            GLOBAL_PO_TOKEN     = _saved.get("po_token", "")
            GLOBAL_VISITOR_DATA = _saved.get("visitor_data", "")
    except Exception:
        pass

# In-memory job tracker
jobs = {}  # job_id -> { status, progress, speed, eta, file_path, error, title }

# ─── Supported formats ──────────────────────────────────────────────────────
QUALITY_MAP = {
    "best":   "bestvideo+bestaudio/best",
    "2160p":  "bestvideo[height<=2160]+bestaudio/best[height<=2160]/bestvideo+bestaudio/best",
    "1440p":  "bestvideo[height<=1440]+bestaudio/best[height<=1440]/bestvideo+bestaudio/best",
    "1080p":  "bestvideo[height<=1080]+bestaudio/best[height<=1080]/bestvideo+bestaudio/best",
    "720p":   "bestvideo[height<=720]+bestaudio/best[height<=720]/bestvideo+bestaudio/best",
    "480p":   "bestvideo[height<=480]+bestaudio/best[height<=480]/bestvideo+bestaudio/best",
    "360p":   "bestvideo[height<=360]+bestaudio/best[height<=360]/bestvideo+bestaudio/best",
}

AUDIO_FORMATS = {"mp3", "m4a", "flac", "wav", "ogg", "opus"}


def _base_opts():
    """Return common yt-dlp options with player fallbacks and auth."""
    opts = {
        "quiet":       True,
        "no_warnings": True,
        "noplaylist":  True,
        "extractor_args": {
            "youtube": {
                "player_client": ["web", "mweb", "android", "ios"],
            }
        }
    }
    if FFMPEG_PATH:
        opts["ffmpeg_location"] = FFMPEG_PATH

    # Always add cookies if available (most reliable method)
    if os.path.isfile(COOKIES_FILE):
        opts["cookiefile"] = COOKIES_FILE

    # Add PO Token if available (for environments/videos requiring Proof of Origin)
    if GLOBAL_PO_TOKEN:
        po_val = GLOBAL_PO_TOKEN if "+" in GLOBAL_PO_TOKEN else f"web+{GLOBAL_PO_TOKEN}"
        opts["extractor_args"]["youtube"]["po_token"] = [po_val]
        if GLOBAL_VISITOR_DATA:
            opts["extractor_args"]["youtube"]["visitor_data"] = [GLOBAL_VISITOR_DATA]

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
#  API — Cookie Upload (backup auth)
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/upload-cookies", methods=["POST"])
def api_upload_cookies():
    """Accept a Netscape-format cookies.txt file for YouTube auth."""
    if "file" not in request.files:
        return jsonify({"error": "No file uploaded"}), 400

    f = request.files["file"]
    if not f.filename:
        return jsonify({"error": "Empty filename"}), 400

    content = f.read().decode("utf-8", errors="ignore")

    # Basic validation — Netscape cookies start with a comment or domain lines
    if not ("youtube.com" in content.lower() or "google.com" in content.lower()):
        return jsonify({"error": "This doesn't look like a YouTube cookies file. "
                                 "Make sure you export cookies from youtube.com"}), 400

    with open(COOKIES_FILE, "w", encoding="utf-8") as out:
        out.write(content)

    return jsonify({"ok": True, "message": "Cookies uploaded! YouTube auth is now active."})


@app.route("/api/auth-status")
def api_auth_status():
    """Return current authentication status."""
    try:
        has_cookies = os.path.isfile(COOKIES_FILE)
        has_po = bool(GLOBAL_PO_TOKEN)
        
        status_method = "none"
        if has_cookies and has_po: status_method = "both"
        elif has_cookies: status_method = "cookies"
        elif has_po: status_method = "po_token"

        # Warn if neither method is active
        ephemeral_warning = None
        if not has_cookies and not has_po:
            ephemeral_warning = "No auth active. Note: Render free tier resets uploaded cookies on restart. Use Render env vars (PO_TOKEN, VISITOR_DATA) for persistent auth."
        
        return jsonify({
            "method": status_method,
            "authenticated": has_cookies or has_po,
            "has_cookies": has_cookies,
            "has_po_token": has_po,
            "po_token": GLOBAL_PO_TOKEN,
            "visitor_data": GLOBAL_VISITOR_DATA,
            "warning": ephemeral_warning,
        })
    except Exception as e:
        print(f"Auth status check error: {e}")
        return jsonify({
            "method": "none",
            "authenticated": False,
            "error": str(e)
        })

@app.route("/api/set-po", methods=["POST"])
def api_set_po():
    """Receive PO Token and Visitor Data from UI and persist to file."""
    global GLOBAL_PO_TOKEN, GLOBAL_VISITOR_DATA
    data = request.get_json(force=True)
    GLOBAL_PO_TOKEN = data.get("po_token", "").strip()
    GLOBAL_VISITOR_DATA = data.get("visitor_data", "").strip()

    # Save to file so it survives a gunicorn worker restart (not a full redeploy)
    try:
        with open(PO_TOKEN_FILE, "w") as f:
            json.dump({"po_token": GLOBAL_PO_TOKEN, "visitor_data": GLOBAL_VISITOR_DATA}, f)
    except Exception as e:
        return jsonify({"ok": True, "message": f"Tokens saved in memory (file save failed: {e}). Use Render env vars for permanent storage."})

    return jsonify({"ok": True, "message": "Tokens saved. For permanent storage across redeploys, set PO_TOKEN and VISITOR_DATA in Render environment variables."})


@app.route("/api/clear-cookies", methods=["POST"])
def api_clear_cookies():
    """Remove uploaded cookies and saved tokens."""
    global GLOBAL_PO_TOKEN, GLOBAL_VISITOR_DATA
    if os.path.isfile(COOKIES_FILE):
        os.remove(COOKIES_FILE)
    if os.path.isfile(PO_TOKEN_FILE):
        os.remove(PO_TOKEN_FILE)
    GLOBAL_PO_TOKEN = ""
    GLOBAL_VISITOR_DATA = ""
    return jsonify({"ok": True, "message": "Cookies and Tokens removed."})



# ═════════════════════════════════════════════════════════════════════════════
#  API — Fetch Video Info
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/info", methods=["POST"])
def api_info():
    data = request.get_json(force=True)
    url  = data.get("url", "").strip()
    if not url:
        return jsonify({"error": "No URL provided"}), 400

    # Normalize Shorts URLs
    if "/shorts/" in url:
        try:
            vid_id = url.split("/shorts/")[1].split("?")[0].split("&")[0].split("/")[0]
            if vid_id:
                url = f"https://www.youtube.com/watch?v={vid_id}"
        except Exception:
            pass

    try:
        ydl_opts = _base_opts()
        ydl_opts["skip_download"] = True
        ydl_opts["ignore_no_formats_error"] = True

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
        if not info:
            return jsonify({"error": "Could not retrieve video information. Please verify the URL."}), 400

        duration = int(info.get("duration", 0) or 0)
        hrs, rem = divmod(duration, 3600)
        mins, secs = divmod(rem, 60)
        dur_str = f"{hrs:02d}:{mins:02d}:{secs:02d}" if hrs else f"{mins:02d}:{secs:02d}"

        views = info.get("view_count", 0) or 0

        # Thumbnail fallback
        thumbs = info.get("thumbnails") or []
        thumb_url = info.get("thumbnail") or (thumbs[-1].get("url") if thumbs else "")

        return jsonify({
            "title":     info.get("title", "Unknown"),
            "channel":   info.get("uploader", info.get("channel", "")),
            "thumbnail": thumb_url,
            "duration":  dur_str,
            "views":     f"{views:,}",
            "url":       url,
        })
    except yt_dlp.utils.DownloadError as e:
        error_msg = str(e)
        if "Requested format is not available" in error_msg or "Sign in to confirm you’re not a bot" in error_msg:
            return jsonify({"error": "YouTube Bot Detection blocked the video stream. Please upload active YouTube cookies via the 'Sign In' button to bypass this!"}), 400
        return jsonify({"error": error_msg}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ═════════════════════════════════════════════════════════════════════════════
#  API — Fetch Playlist Info
# ═════════════════════════════════════════════════════════════════════════════

@app.route("/api/playlist", methods=["POST"])
def api_playlist():
    data = request.get_json(force=True)
    url  = data.get("url", "").strip()
    if not url:
        return jsonify({"error": "No URL provided"}), 400

    try:
        ydl_opts = _base_opts()
        ydl_opts["skip_download"] = True
        ydl_opts["extract_flat"] = True
        ydl_opts["noplaylist"] = False

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

        entries = info.get("entries") or []
        items = []
        for e in entries:
            if not e:
                continue
            vid_id = e.get("id", "")
            vid_url = e.get("url") or (f"https://www.youtube.com/watch?v={vid_id}" if vid_id else "")
            
            # Formatted duration
            duration = int(e.get("duration", 0) or 0)
            hrs, rem = divmod(duration, 3600)
            mins, secs = divmod(rem, 60)
            dur_str = f"{hrs:02d}:{mins:02d}:{secs:02d}" if hrs else f"{mins:02d}:{secs:02d}"

            thumbs = e.get("thumbnails") or []
            thumb_url = thumbs[-1].get("url") if thumbs else (f"https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg" if vid_id else "")

            items.append({
                "id": vid_id,
                "title": e.get("title", "Unknown Title"),
                "duration": dur_str,
                "duration_seconds": duration,
                "url": vid_url,
                "thumbnail": thumb_url,
                "channel": e.get("uploader") or e.get("channel") or "",
            })

        return jsonify({
            "title": info.get("title", "YouTube Playlist"),
            "author": info.get("uploader") or info.get("channel") or "",
            "item_count": len(items),
            "items": items,
            "url": url,
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

    except yt_dlp.utils.DownloadError as e:
        error_msg = str(e)
        if "Requested format is not available" in error_msg or "Sign in to confirm you’re not a bot" in error_msg:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"]  = "YouTube Bot Detection blocked the download. Please upload active YouTube cookies via the 'Sign In' button to bypass this."
        else:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"]  = error_msg
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
