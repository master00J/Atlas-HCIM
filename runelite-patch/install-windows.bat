@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Python ontbreekt. Installeer Python 3 of gebruik apply_patch.py vanuit een terminal.
  pause
  exit /b 1
)
set /p "REPO=Pad naar de gekloonde guide-overlay repository: "
py apply_patch.py "%REPO%"
pause
