@echo off
setlocal
cd /d "%~dp0"
node server.js --demo --port 38878
pause
