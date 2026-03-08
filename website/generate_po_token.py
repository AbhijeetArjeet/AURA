"""
generate_po_token.py — Generate YouTube PO Token and Visitor Data

Run this script on YOUR PC (not on the server) to generate
the PO_TOKEN and VISITOR_DATA values. Then paste them into
Render.com environment variables.

Requirements: pip install yt-dlp
"""
import subprocess
import json
import re
import sys


def main():
    print("=" * 55)
    print("  YouTube PO Token Generator")
    print("=" * 55)
    print()
    print("This script extracts your PO token and visitor data")
    print("from a YouTube request. You need a browser with an")
    print("active YouTube session (just be logged into YouTube).")
    print()
    print("METHOD: Using yt-dlp verbose output to extract tokens")
    print("-" * 55)
    print()

    # Use yt-dlp verbose mode to extract visitor data
    test_url = "https://www.youtube.com/watch?v=jNQXAC9IVRw"  # "Me at the zoo" — first YT video
    print(f"Fetching token using test video...")
    print(f"URL: {test_url}")
    print()

    try:
        result = subprocess.run(
            [
                sys.executable, "-m", "yt_dlp",
                "--verbose",
                "--skip-download",
                "--dump-json",
                "--no-playlist",
                "--cookies-from-browser", "chrome",
                test_url,
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )

        output = result.stderr + "\n" + result.stdout

        # Try to extract visitor data from verbose output
        visitor_match = re.search(r'visitor_data["\s:=]+([A-Za-z0-9%_-]+)', output)
        po_match = re.search(r'po_token["\s:=]+([A-Za-z0-9%_-]+)', output)

        if visitor_match:
            visitor_data = visitor_match.group(1)
        else:
            visitor_data = None

        if po_match:
            po_token = po_match.group(1)
        else:
            po_token = None

        # Try parsing the JSON output for more info
        try:
            json_start = result.stdout.index("{")
            json_data = json.loads(result.stdout[json_start:])
            print("✔ Successfully connected to YouTube!")
            print(f"  Test video: {json_data.get('title', 'Unknown')}")
            print()
        except (ValueError, json.JSONDecodeError):
            if result.returncode != 0:
                print("✘ Failed to connect. Make sure:")
                print("  1. Chrome is installed")
                print("  2. You are logged into YouTube in Chrome")
                print("  3. Close Chrome before running this script")
                print()
                print("Error output:")
                print(result.stderr[-500:] if result.stderr else "No error output")
                return

        print("=" * 55)
        print("  YOUR TOKENS")
        print("=" * 55)
        print()

        if visitor_data:
            print(f"VISITOR_DATA = {visitor_data}")
        else:
            print("VISITOR_DATA = (not found automatically)")
            print("  → See manual method below")

        if po_token:
            print(f"PO_TOKEN     = {po_token}")
        else:
            print("PO_TOKEN     = (not found automatically)")
            print("  → See manual method below")

        print()
        print("=" * 55)
        print("  HOW TO SET ON RENDER.COM")
        print("=" * 55)
        print()
        print("1. Go to render.com → Your Web Service → Environment")
        print("2. Add these environment variables:")
        print(f"   PO_TOKEN     = (your token)")
        print(f"   VISITOR_DATA = (your visitor data)")
        print("3. Click 'Save Changes' → Render will auto-redeploy")
        print()
        print("=" * 55)
        print("  MANUAL METHOD (if auto-extraction failed)")
        print("=" * 55)
        print()
        print("1. Open Chrome → Go to youtube.com")
        print("2. Press F12 → Network tab")
        print("3. Play any video")
        print("4. Search for 'player' in network requests")
        print("5. Find a request to 'youtubei/v1/player'")
        print("6. Look in the Request Payload for:")
        print('   "visitorData": "..." → This is your VISITOR_DATA')
        print()
        print("For PO_TOKEN, use the tool at:")
        print("   https://github.com/YunzheZJU/youtube-po-token-generator")
        print()
        print("Or install: pip install yt-dlp-get-pot bgutil-ytdlp-pot-provider")
        print("Then yt-dlp will auto-generate PO tokens!")

    except subprocess.TimeoutExpired:
        print("✘ Timed out. Make sure Chrome is closed and try again.")
    except FileNotFoundError:
        print("✘ yt-dlp not found. Install it: pip install yt-dlp")
    except Exception as e:
        print(f"✘ Error: {e}")


if __name__ == "__main__":
    main()
