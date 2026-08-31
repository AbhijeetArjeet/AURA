@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo    YPDlp - Installing Dependencies
echo ============================================
echo.

:: Check Python
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH.
    echo Please install Python from https://python.org
    pause
    exit /b 1
)

echo [+] Python found.
echo [+] Installing packages and built-in FFmpeg...
echo.
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

echo.
echo [+] Verifying environment and FFmpeg...
python -c "import yt_dlp, PyQt6, PIL, requests, imageio_ffmpeg, ffmpeg_utils; print('[OK] All modules loaded. FFmpeg:', ffmpeg_utils.get_ffmpeg_path())"

echo.
echo ============================================
echo  Installation complete!
echo  Run "run.bat" to start the application.
echo ============================================
pause
