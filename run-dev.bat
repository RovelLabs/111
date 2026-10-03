@echo off
chcp 65001 >nul
setlocal
title Visual Client - запуск для разработки
cd /d "%~dp0"

rem Сам скачивает Java 21 (в папку .jdk, без прав администратора), если подходящей Java нет,
rem и запускает игру с клиентом из исходников. Другую задачу Gradle можно передать аргументом:
rem   run-dev.bat build

set "TASK=%~1"
if "%TASK%"=="" set "TASK=runClient"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev.ps1" %TASK%
if errorlevel 1 (
  echo.
  echo Что-то пошло не так — текст ошибки выше.
)
pause
