# Готовит окружение разработчика и запускает задачу Gradle (по умолчанию runClient).
# Java 17+ берётся из системы, а если её нет — скачивается Temurin 21 в папку .jdk проекта.
param([string]$Task = "runClient")

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = Split-Path -Parent $PSScriptRoot
$LocalJdk = Join-Path $Root ".jdk"
$MinJava = 17

function Get-JavaMajor([string]$JavaExe) {
    # java -version пишет в stderr; при ErrorActionPreference=Stop PowerShell 5.1 счёл бы это ошибкой
    $ErrorActionPreference = "Continue"
    try {
        $output = & $JavaExe -version 2>&1 | Out-String
        if ($output -match 'version "(\d+)(\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1) { $major = [int]$Matches[3] }  # формат 1.8.0_xxx
            return $major
        }
    } catch { }
    return 0
}

function Find-Java {
    $candidates = @()
    $candidates += Join-Path $LocalJdk "bin\java.exe"
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME "bin\java.exe" }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    foreach ($java in $candidates) {
        if ((Test-Path $java) -and ((Get-JavaMajor $java) -ge $MinJava)) {
            return (Split-Path -Parent (Split-Path -Parent $java))
        }
    }
    return $null
}

function Install-LocalJdk {
    Write-Host "Подходящая Java не найдена — скачиваю Java 21 (Eclipse Temurin), это ~200 МБ..."
    $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    $zip = Join-Path $env:TEMP "visualclient-jdk21.zip"
    $unpack = Join-Path $env:TEMP "visualclient-jdk21"

    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    if (Test-Path $unpack) { Remove-Item $unpack -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $unpack -Force

    # В архиве одна папка вида jdk-21.0.x+y — переносим её содержимое в .jdk
    $inner = Get-ChildItem $unpack -Directory | Select-Object -First 1
    if (Test-Path $LocalJdk) { Remove-Item $LocalJdk -Recurse -Force }
    Move-Item $inner.FullName $LocalJdk
    Remove-Item $zip, $unpack -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Java установлена в $LocalJdk"
}

$javaHome = Find-Java
if (-not $javaHome) {
    Install-LocalJdk
    $javaHome = $LocalJdk
}
$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path
Write-Host "Java: $javaHome"

Write-Host "Запускаю Gradle: $Task (первый запуск скачивает Minecraft и библиотеки — это несколько минут)"
Set-Location $Root
& (Join-Path $Root "gradlew.bat") $Task
exit $LASTEXITCODE
