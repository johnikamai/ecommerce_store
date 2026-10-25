@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-services.ps1" %*
exit /b %errorlevel%
