@echo off
setlocal
cd /d "%~dp0website"

echo ===================================================
echo   YPDlp — Local Private Download Server
echo ===================================================
echo.
echo [+] Checking Python...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH.
    pause
    exit /b 1
)

echo [+] Starting Local Server on http://0.0.0.0:5000 ...
echo.
echo [!] To connect from your Android phone on the same Wi-Fi:
echo     1. Open Command Prompt and check your IPv4 address (run: ipconfig)
echo     2. In YPDlp Android App - Settings, enter: http://YOUR_PC_IP:5000
echo.
echo ===================================================

python server.py
pause
