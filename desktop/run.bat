@echo off
echo Starting YPDlp YouTube Downloader...
python main.py
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] App crashed. Run install.bat if packages are missing.
    pause
)
