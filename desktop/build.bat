@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   YPDlp — Build Standalone Windows Release
echo ============================================
echo.

REM Ensure dependencies and FFmpeg are present
echo [+] Checking dependencies...
python -m pip install -r requirements.txt

echo.
echo [+] Building standalone application with PyInstaller...
python -m PyInstaller ypdlp.spec --clean --noconfirm

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed! Check the output above.
    pause
    exit /b 1
)

echo.
echo [+] Creating release archive: dist\YPDlp_Windows_x64.zip...
powershell -Command "if (Test-Path 'dist\YPDlp') { Compress-Archive -Path 'dist\YPDlp\*' -DestinationPath 'dist\YPDlp_Windows_x64.zip' -Force; Write-Host '[+] Zip package created successfully.' }"

echo.
echo ============================================
echo   Done!
echo   Folder:  dist\YPDlp\YPDlp.exe
echo   Zip:     dist\YPDlp_Windows_x64.zip
echo ============================================
pause
