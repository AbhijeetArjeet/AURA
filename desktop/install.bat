@echo off
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
echo [+] Installing packages...
echo.
pip install --upgrade pip
pip install -r requirements.txt

echo.
echo ============================================
echo  Installation complete!
echo  Run "run.bat" to start the application.
echo ============================================
pause
