@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   YPDlp — Build Installable Android APK
echo ============================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install JDK 17 or JDK 21.
    pause
    exit /b 1
)

echo [+] Java found.
echo [+] Building universal APK with Gradle...
echo.

call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed! Check the output above.
    pause
    exit /b 1
)

echo.
echo [+] Copying APK to: YPDlp_Android.apk...
copy /y "app\build\outputs\apk\debug\app-debug.apk" "YPDlp_Android.apk" >nul

echo.
echo ============================================
echo   Done!
echo   Installable APK:  android\YPDlp_Android.apk
echo.
echo   You can transfer this APK to ANY Android
echo   phone and install it directly!
echo ============================================
pause
