"""
generate_po_token.py — Generate YouTube PO Token and Visitor Data
Tries multiple methods automatically, falls back gracefully.

Run on YOUR PC (not the server).
Requirements: pip install yt-dlp requests
"""
import subprocess
import json
import re
import sys
import os
import time


def separator(title=""):
    if title:
        pad = (53 - len(title) - 2) // 2
        print("=" * pad + f" {title} " + "=" * pad)
    else:
        print("=" * 55)


def success(label, value):
    print(f"\n  ✔ {label}:")
    print(f"    {value}\n")


def fail(reason):
    print(f"  ✘ {reason}")


# ─── Method 1: cookies-from-browser (Chrome closed) ──────────────────────────
def try_chrome_closed():
    print("Trying: Chrome (closed) cookies extraction...")
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp",
             "--verbose", "--skip-download", "--dump-json",
             "--no-playlist", "--cookies-from-browser", "chrome",
             "https://www.youtube.com/watch?v=jNQXAC9IVRw"],
            capture_output=True, text=True, timeout=60
        )
        return _parse_output(result)
    except subprocess.TimeoutExpired:
        fail("Timed out.")
    except Exception as e:
        fail(str(e))
    return None, None


# ─── Method 2: cookies-from-browser (Firefox) ────────────────────────────────
def try_firefox():
    print("Trying: Firefox cookies extraction...")
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp",
             "--verbose", "--skip-download", "--dump-json",
             "--no-playlist", "--cookies-from-browser", "firefox",
             "https://www.youtube.com/watch?v=jNQXAC9IVRw"],
            capture_output=True, text=True, timeout=60
        )
        return _parse_output(result)
    except subprocess.TimeoutExpired:
        fail("Timed out.")
    except Exception as e:
        fail(str(e))
    return None, None


# ─── Method 3: cookies-from-browser (Edge) ───────────────────────────────────
def try_edge():
    print("Trying: Microsoft Edge cookies extraction...")
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp",
             "--verbose", "--skip-download", "--dump-json",
             "--no-playlist", "--cookies-from-browser", "edge",
             "https://www.youtube.com/watch?v=jNQXAC9IVRw"],
            capture_output=True, text=True, timeout=60
        )
        return _parse_output(result)
    except subprocess.TimeoutExpired:
        fail("Timed out.")
    except Exception as e:
        fail(str(e))
    return None, None


# ─── Method 4: existing cookies.txt file ─────────────────────────────────────
def try_cookies_file():
    # Look for cookies.txt in current dir or common locations
    candidates = [
        "cookies.txt",
        os.path.join(os.path.expanduser("~"), "Downloads", "cookies.txt"),
        os.path.join(os.path.expanduser("~"), "Desktop", "cookies.txt"),
    ]
    found = next((p for p in candidates if os.path.isfile(p)), None)
    if not found:
        fail("No cookies.txt found in current folder, Downloads, or Desktop.")
        return None, None

    print(f"Trying: cookies.txt file ({found})...")
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp",
             "--verbose", "--skip-download", "--dump-json",
             "--no-playlist", "--cookies", found,
             "https://www.youtube.com/watch?v=jNQXAC9IVRw"],
            capture_output=True, text=True, timeout=60
        )
        return _parse_output(result)
    except subprocess.TimeoutExpired:
        fail("Timed out.")
    except Exception as e:
        fail(str(e))
    return None, None


# ─── Method 5: no auth (visitor data only, no PO token) ──────────────────────
def try_no_auth():
    print("Trying: No auth (visitor data only)...")
    try:
        result = subprocess.run(
            [sys.executable, "-m", "yt_dlp",
             "--verbose", "--skip-download", "--dump-json",
             "--no-playlist",
             "--extractor-args", "youtube:player_client=ios",
             "https://www.youtube.com/watch?v=jNQXAC9IVRw"],
            capture_output=True, text=True, timeout=60
        )
        visitor, po = _parse_output(result)
        return visitor, None  # PO token won't be present, that's fine
    except subprocess.TimeoutExpired:
        fail("Timed out.")
    except Exception as e:
        fail(str(e))
    return None, None


# ─── Output parser ────────────────────────────────────────────────────────────
def _parse_output(result):
    output = result.stderr + "\n" + result.stdout

    visitor = None
    po = None

    # Extract visitor_data
    m = re.search(r'visitor_data["\s:=]+([A-Za-z0-9%+=/_-]{10,})', output)
    if m:
        visitor = m.group(1).strip().rstrip('"\'\\')

    # Extract po_token
    m = re.search(r'po_token["\s:=]+([A-Za-z0-9%+=/_-]{10,})', output)
    if m:
        po = m.group(1).strip().rstrip('"\'\\')

    # Also try parsing JSON output for visitor_data
    if not visitor:
        try:
            idx = result.stdout.index("{")
            jdata = json.loads(result.stdout[idx:])
            visitor = (jdata.get("visitor_data") or
                       jdata.get("_visitor_data") or
                       visitor)
        except Exception:
            pass

    if visitor or po:
        return visitor, po

    # If returncode != 0, show last bit of error
    if result.returncode != 0:
        snippet = (result.stderr or "")[-300:]
        fail(f"yt-dlp failed:\n{snippet}")

    return None, None


# ─── Print final result ───────────────────────────────────────────────────────
def print_result(visitor, po):
    separator("YOUR TOKENS")
    print()

    if visitor:
        success("VISITOR_DATA", visitor)
    else:
        print("  ⚠  VISITOR_DATA — not found (downloads may still work without it)\n")

    if po:
        success("PO_TOKEN", po)
    else:
        print("  ⚠  PO_TOKEN — not found\n")
        print("     YouTube may still work with just cookies.")
        print("     If you get bot errors, get PO_TOKEN manually:")
        print("     → https://github.com/YunzheZJU/youtube-po-token-generator\n")

    separator("SET ON RENDER.COM")
    print()
    print("  1. Go to render.com → Your Web Service → Environment")
    print("  2. Add / update these variables:")
    print(f"       PO_TOKEN     = {po or '(see above)'}")
    print(f"       VISITOR_DATA = {visitor or '(see above)'}")
    print("  3. Click Save Changes → Render auto-redeploys")
    print()
    print("  OR: Open your site → Sign In button → paste tokens there")
    print("      (takes effect instantly, no redeploy needed)")
    print()
    separator()


# ─── Main ─────────────────────────────────────────────────────────────────────
def main():
    separator("YouTube PO Token Generator")
    print()
    print("  Trying multiple methods automatically...")
    print("  (Chrome/Firefox/Edge can be open or closed)")
    print()
    separator()
    print()

    methods = [
        try_chrome_closed,
        try_firefox,
        try_edge,
        try_cookies_file,
        try_no_auth,
    ]

    visitor, po = None, None
    for method in methods:
        visitor, po = method()
        if visitor or po:
            print(f"  ✔ Success with: {method.__name__.replace('try_', '').replace('_', ' ').title()}")
            print()
            break
        print()

    if not visitor and not po:
        separator("ALL METHODS FAILED")
        print()
        print("  Manual steps:")
        print("  1. Open Chrome → youtube.com (logged in)")
        print("  2. Press F12 → Network tab → play any video")
        print("  3. Filter by 'player' → click 'youtubei/v1/player'")
        print("  4. Payload tab → find visitorData → copy it")
        print()
        print("  For PO_TOKEN:")
        print("  → https://github.com/YunzheZJU/youtube-po-token-generator")
        print()
        return

    print_result(visitor, po)

    # Save to a local file for convenience
    out = {}
    if visitor:
        out["VISITOR_DATA"] = visitor
    if po:
        out["PO_TOKEN"] = po

    if out:
        with open("tokens.json", "w") as f:
            json.dump(out, f, indent=2)
        print(f"  💾 Also saved to tokens.json in current folder")
        print()


if __name__ == "__main__":
    main()
