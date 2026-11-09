@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-runelite-windows.ps1"
if errorlevel 1 echo Installatie niet voltooid. Bekijk de foutmelding.
pause
