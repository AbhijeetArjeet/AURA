@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   YPDlp — Build Installable Android APK
echo ============================================
echo.

:: Automatically detect Android Studio JDK if system Java is version 24+
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [+] Using Android Studio JDK 21: %JAVA_HOME%
)

:: Verify Java version
java -version
if %errorlevel% neq 0 (
    echo [ERROR] Java JDK is required to build the Android APK.
    pause
    exit /b 1
)

echo.
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
