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

REM Ensure bin\ffmpeg.exe is prepared
python -c "import os, shutil, imageio_ffmpeg; os.makedirs('bin', exist_ok=True); shutil.copy2(imageio_ffmpeg.get_ffmpeg_exe(), 'bin/ffmpeg.exe') if not os.path.isfile('bin/ffmpeg.exe') else None"

echo.
echo [+] Building standalone application with PyInstaller...
python -m PyInstaller ypdlp.spec --clean --noconfirm

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed! Check the output above.
    pause
    exit /b 1
)

REM Ensure ffmpeg.exe is in the output folder
if exist "bin\ffmpeg.exe" (
    copy /y "bin\ffmpeg.exe" "dist\YPDlp\ffmpeg.exe" >nul
    if not exist "dist\YPDlp\bin" mkdir "dist\YPDlp\bin"
    copy /y "bin\ffmpeg.exe" "dist\YPDlp\bin\ffmpeg.exe" >nul
    echo [+] Bundled FFmpeg into dist\YPDlp\ffmpeg.exe
)

echo.
echo [+] Creating release archive: dist\YPDlp_Windows_x64.zip...
powershell -Command "if (Test-Path 'dist\YPDlp') { Compress-Archive -Path 'dist\YPDlp\*' -DestinationPath 'dist\YPDlp_Windows_x64.zip' -Force; Write-Host '[+] Zip package created successfully with FFmpeg included.' }"

echo.
echo ============================================
echo   Done!
echo   Folder:  dist\YPDlp\YPDlp.exe
echo   Zip:     dist\YPDlp_Windows_x64.zip
echo ============================================
pause
