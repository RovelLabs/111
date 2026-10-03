@echo off
chcp 65001 >nul
setlocal
title Visual Client - установка

rem Скачивает последнюю версию установщика из GitHub Releases и запускает его.
rem Ничего ставить заранее не нужно: используется встроенный в Windows PowerShell.

set "URL=https://github.com/RovelLabs/111/releases/latest/download/VisualClientInstaller.exe"
set "EXE=%TEMP%\VisualClientInstaller.exe"

echo Скачиваю установщик Visual Client...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue';" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "try { Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%EXE%' } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 (
  echo.
  echo Не удалось скачать установщик. Проверьте интернет и попробуйте ещё раз.
  pause
  exit /b 1
)

rem Для автоматической проверки в CI: только скачать, не запускать.
if defined VC_NO_START exit /b 0

echo Запускаю установщик...
start "" "%EXE%"
