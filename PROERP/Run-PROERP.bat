@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Run-Standalone.ps1" %*
set "PROERP_EXIT=%ERRORLEVEL%"
if not "%PROERP_EXIT%"=="0" pause
exit /b %PROERP_EXIT%
