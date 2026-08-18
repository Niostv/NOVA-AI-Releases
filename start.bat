@echo off
cd /d "%~dp0"
set "CODEX_PYTHON=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"

if exist "%CODEX_PYTHON%" (
  "%CODEX_PYTHON%" server.py
  goto :end
)

where py >nul 2>nul
if not errorlevel 1 (
  py -3 server.py
  goto :end
)

where python >nul 2>nul
if not errorlevel 1 (
  python server.py
  goto :end
)

echo.
echo Python ne nayden. Ustanovite Python 3 s https://python.org/downloads/

:end
pause
