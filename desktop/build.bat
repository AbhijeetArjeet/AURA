@echo off
echo ============================================
echo   YPDlp — Build Windows EXE
echo ============================================
echo.

REM Ensure venv is active or install deps globally
pip install -r requirements.txt
pip install pyinstaller

echo.
echo Building EXE...
pyinstaller ypdlp.spec --clean

echo.
echo ============================================
echo   Done!  EXE is in:  dist\YPDlp\YPDlp.exe
echo ============================================
pause
