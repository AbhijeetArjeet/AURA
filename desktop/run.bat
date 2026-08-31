@echo off
setlocal
cd /d "%~dp0"

echo Starting YPDlp YouTube Downloader...
python main.py
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] App exited with error. Run install.bat if packages are missing.
    pause
)
