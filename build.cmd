@echo off
rem NeoMod build wrapper (ASCII only). The real work is in build.ps1.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
pause /b %ERRORLEVEL%
