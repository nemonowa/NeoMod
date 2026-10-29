@echo off
rem Generate the Eclipse .classpath (ASCII only). The real work is in setup-eclipse.ps1.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-eclipse.ps1"
pause /b %ERRORLEVEL%
