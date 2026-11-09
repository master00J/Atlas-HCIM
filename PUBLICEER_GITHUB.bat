@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\publish-github.ps1"
if errorlevel 1 (
 echo.
 echo Publicatie kon niet worden afgerond. Lees de melding hierboven.
 pause
 exit /b 1
)
echo.
echo Publicatie klaar. Controleer de repository in je browser.
pause
