"""Установка и запуск игры: Java, Minecraft 1.20.1, Fabric, Fabric API и сам клиент.

Всё скачивается в папку клиента на рабочем столе — официальный лаунчер не нужен.
Что именно ставить (версии Fabric, Fabric API, файл клиента) берётся из manifest.json
последнего релиза на GitHub, поэтому новая версия клиента подтягивается сама.
"""

from __future__ import annotations

import hashlib
import json
import os
import platform
import sys
import shutil
import subprocess
import tempfile
import threading
import uuid
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from common import (APP_NAME, IS_WINDOWS, MC_VERSION, REPO, VERSION, LauncherError, download, file_sha256,
                    game_dir, get_json, home_dir, is_valid, minecraft_dir, runtime_dir)

GITHUB_API = os.environ.get("VC_GITHUB_API", "https://api.github.com")
MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MOJANG_RESOURCES = "https://resources.download.minecraft.net"
FABRIC_META = "https://meta.fabricmc.net"
FABRIC_MAVEN = "https://maven.fabricmc.net"

Status = Callable[[str], None]
Progress = Callable[[float], None]


# ------------------------------------------------------------------ релиз


@dataclass
class Release:
    version: str
    minecraft: str
    loader: str
    fabric_api: str
    client_jar: str
    client_url: str
    client_sha256: Optional[str]
    launcher_url: Optional[str]
    notes: str


def fetch_release() -> Release:
    """Последний релиз с GitHub. VC_LOCAL_RELEASE=<папка> — взять релиз из локальной папки (для тестов)."""
    local = os.environ.get("VC_LOCAL_RELEASE")
    if local:
        folder = Path(local)
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        assets = {p.name: p.resolve().as_uri() for p in folder.iterdir()}
        notes = "Локальная сборка"
    else:
        data = get_json(f"{GITHUB_API}/repos/{REPO}/releases/latest")
        assets = {a["name"]: a["browser_download_url"] for a in data.get("assets", [])}
        if "manifest.json" not in assets:
            raise LauncherError("Последний релиз на GitHub собран неправильно (нет manifest.json)")
        manifest = get_json(assets["manifest.json"])
        notes = data.get("body") or ""

    if manifest["client_jar"] not in assets:
        raise LauncherError("В релизе нет файла клиента")
    return Release(
        version=manifest["version"],
        minecraft=manifest["minecraft"],
        loader=manifest["loader"],
        fabric_api=manifest["fabric_api"],
        client_jar=manifest["client_jar"],
        client_url=assets[manifest["client_jar"]],
        client_sha256=manifest.get("client_sha256"),
        launcher_url=assets.get(manifest.get("launcher_exe", "VisualClient.exe")),
        notes=notes,
    )


# ------------------------------------------------------------------ прогресс


class Steps:
    """Делит общий прогресс 0..1 между этапами установки пропорционально их весу."""

    def __init__(self, progress: Progress, weights: dict[str, float]):
        self.progress = progress
        self.total = sum(weights.values())
        self.offsets: dict[str, float] = {}
        acc = 0.0
        for name, weight in weights.items():
            self.offsets[name] = acc
            acc += weight
        self.weights = weights

    def stage(self, name: str) -> Progress:
        start, weight = self.offsets[name], self.weights[name]
        return lambda p: self.progress((start + weight * max(0.0, min(1.0, p))) / self.total)


def _download_many(jobs: list[tuple[str, Path, Optional[str]]], progress: Progress, workers: int = 16) -> None:
    """Параллельно скачивает файлы (url, путь, sha1). Один и тот же путь качается один раз."""
    unique: dict[Path, tuple[str, Path, Optional[str]]] = {}
    for job in jobs:
        unique.setdefault(job[1], job)
    jobs = list(unique.values())
    if not jobs:
        progress(1.0)
        return
    done = 0
    lock = threading.Lock()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(download, url, path, sha1=sha1) for url, path, sha1 in jobs]
        for future in as_completed(futures):
            future.result()  # пробрасываем ошибку загрузки
            with lock:
                done += 1
                progress(done / len(jobs))


# ------------------------------------------------------------------ Java


def _os_name() -> str:
    return {"win32": "windows", "darwin": "osx"}.get(sys.platform, "linux")


def java_executable(major: int, console: bool = False) -> Path:
    name = ("java.exe" if console else "javaw.exe") if IS_WINDOWS else "java"
    return runtime_dir() / f"java-{major}" / "bin" / name


def ensure_java(major: int, progress: Progress, status: Status) -> Path:
    java = java_executable(major)
    if java.is_file():
        progress(1.0)
        return java

    status(f"Скачиваю Java {major}…")
    os_name = {"windows": "windows", "osx": "mac", "linux": "linux"}[_os_name()]
    arch = "aarch64" if platform.machine().lower() in ("arm64", "aarch64") else "x64"
    url = f"https://api.adoptium.net/v3/binary/latest/{major}/ga/{os_name}/{arch}/jre/hotspot/normal/eclipse"
    target = runtime_dir() / f"java-{major}"

    with tempfile.TemporaryDirectory(dir=home_dir()) as tmp:
        archive = Path(tmp) / ("java.zip" if os_name == "windows" else "java.tar.gz")
        download(url, archive, on_bytes=lambda d, t: progress(0.9 * d / t if t else 0))
        status(f"Распаковываю Java {major}…")
        unpack = Path(tmp) / "unpack"
        if archive.suffix == ".zip":
            with zipfile.ZipFile(archive) as zf:
                zf.extractall(unpack)
        else:
            shutil.unpack_archive(str(archive), str(unpack))
        inner = next(p for p in unpack.iterdir() if p.is_dir())
        if (inner / "Contents" / "Home").is_dir():  # macOS
            inner = inner / "Contents" / "Home"
        if target.exists():
            shutil.rmtree(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(inner), str(target))
    if not IS_WINDOWS:
        for exe in (target / "bin").iterdir():
            exe.chmod(0o755)
    progress(1.0)
    return java


# ------------------------------------------------------------------ Minecraft


def _rules_allow(rules: Optional[list]) -> bool:
    """Правила Mojang: подходит ли библиотека/аргумент к этой системе."""
    if not rules:
        return True
    allowed = False
    os_name = _os_name()
    machine = platform.machine().lower()
    arch = "arm64" if machine in ("arm64", "aarch64") else ("x86" if machine in ("x86", "i386", "i686") else "x86_64")
    for rule in rules:
        matches = True
        os_rule = rule.get("os", {})
        if "name" in os_rule and os_rule["name"] != os_name:
            matches = False
        if "arch" in os_rule and os_rule["arch"] != arch:
            matches = False
        for value in rule.get("features", {}).values():
            if value:  # демо-режим, своё разрешение, quick play — у нас всё выключено
                matches = False
        if matches:
            allowed = rule["action"] == "allow"
    return allowed


def maven_path(name: str) -> str:
    """group:artifact:version[:classifier] → group/artifact/version/artifact-version[-classifier].jar"""
    parts = name.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = f"-{parts[3]}" if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"


def _library_key(name: str) -> str:
    parts = name.split(":")
    return ":".join([parts[0], parts[1]] + parts[3:])  # без версии


def _vanilla_json_path(version: str) -> Path:
    return minecraft_dir() / "versions" / version / f"{version}.json"


def ensure_minecraft(version: str, progress: Progress, status: Status) -> dict:
    """Версия игры, её библиотеки и клиентский jar."""
    status(f"Проверяю файлы Minecraft {version}…")
    json_path = _vanilla_json_path(version)
    if json_path.is_file():
        data = json.loads(json_path.read_text(encoding="utf-8"))
    else:
        manifest = get_json(MOJANG_MANIFEST)
        entry = next((v for v in manifest["versions"] if v["id"] == version), None)
        if entry is None:
            raise LauncherError(f"Mojang не знает версию {version}")
        download(entry["url"], json_path, sha1=entry.get("sha1"))
        data = json.loads(json_path.read_text(encoding="utf-8"))

    jobs = []
    client = data["downloads"]["client"]
    client_jar = minecraft_dir() / "versions" / version / f"{version}.jar"
    if not is_valid(client_jar, client.get("size"), client.get("sha1")):
        jobs.append((client["url"], client_jar, client.get("sha1")))

    for lib in data["libraries"]:
        artifact = lib.get("downloads", {}).get("artifact")
        if not artifact or not _rules_allow(lib.get("rules")):
            continue
        path = minecraft_dir() / "libraries" / artifact["path"]
        if not is_valid(path, artifact.get("size")):
            jobs.append((artifact["url"], path, artifact.get("sha1")))

    if jobs:
        status(f"Скачиваю Minecraft {version} ({len(jobs)} файлов)…")
    _download_many(jobs, progress)
    return data


def ensure_assets(version_data: dict, progress: Progress, status: Status) -> None:
    """Звуки, текстуры, переводы — несколько тысяч мелких файлов."""
    index_info = version_data["assetIndex"]
    index_path = minecraft_dir() / "assets" / "indexes" / f"{index_info['id']}.json"
    if not is_valid(index_path, index_info.get("size"), index_info.get("sha1")):
        download(index_info["url"], index_path, sha1=index_info.get("sha1"))
    objects = json.loads(index_path.read_text(encoding="utf-8"))["objects"]

    status("Проверяю ресурсы игры…")
    jobs = []
    for obj in objects.values():
        h = obj["hash"]
        path = minecraft_dir() / "assets" / "objects" / h[:2] / h
        if not is_valid(path, obj["size"]):
            jobs.append((f"{MOJANG_RESOURCES}/{h[:2]}/{h}", path, h))
    if jobs:
        status(f"Скачиваю ресурсы игры ({len(jobs)} файлов)…")
    _download_many(jobs, progress, workers=24)


def ensure_fabric(release: Release, progress: Progress, status: Status) -> dict:
    status(f"Проверяю Fabric {release.loader}…")
    version_id = f"fabric-loader-{release.loader}-{release.minecraft}"
    json_path = minecraft_dir() / "versions" / version_id / f"{version_id}.json"
    if json_path.is_file():
        data = json.loads(json_path.read_text(encoding="utf-8"))
    else:
        data = get_json(f"{FABRIC_META}/v2/versions/loader/{release.minecraft}/{release.loader}/profile/json")
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

    jobs = []
    for lib in data["libraries"]:
        path = minecraft_dir() / "libraries" / maven_path(lib["name"])
        if not is_valid(path, lib.get("size")):
            base = lib.get("url", FABRIC_MAVEN + "/").rstrip("/")
            jobs.append((f"{base}/{maven_path(lib['name'])}", path, lib.get("sha1")))
    _download_many(jobs, progress)
    return data


def ensure_mods(release: Release, progress: Progress, status: Status) -> None:
    mods = game_dir() / "mods"
    mods.mkdir(parents=True, exist_ok=True)

    client = mods / release.client_jar
    if not (client.is_file() and (not release.client_sha256 or file_sha256(client) == release.client_sha256)):
        status(f"Скачиваю {APP_NAME} {release.version}…")
        download(release.client_url, client, sha256=release.client_sha256,
                 on_bytes=lambda d, t: progress(0.5 * d / t if t else 0))
    fabric_api = mods / f"fabric-api-{release.fabric_api}.jar"
    if not fabric_api.is_file():
        status("Скачиваю Fabric API…")
        url = (f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/"
               f"{release.fabric_api.replace('+', '%2B')}/fabric-api-{release.fabric_api.replace('+', '%2B')}.jar")
        download(url, fabric_api, on_bytes=lambda d, t: progress(0.5 + 0.5 * d / t if t else 0.5))

    # Старые версии клиента и Fabric API удаляем, иначе игра загрузит обе
    for jar in mods.glob("*.jar"):
        if jar.name.startswith("visual-client-") and jar != client:
            jar.unlink()
        if jar.name.startswith("fabric-api-") and jar != fabric_api:
            jar.unlink()
    progress(1.0)


def prepare(release: Release, progress: Progress, status: Status) -> None:
    """Докачивает всё, чего не хватает. Повторный вызов почти мгновенный — проверяются только размеры."""
    steps = Steps(progress, {"java": 20, "minecraft": 15, "assets": 45, "fabric": 5, "mods": 15})
    version_data = ensure_minecraft(release.minecraft, steps.stage("minecraft"), status)
    ensure_java(version_data.get("javaVersion", {}).get("majorVersion", 17), steps.stage("java"), status)
    ensure_assets(version_data, steps.stage("assets"), status)
    ensure_fabric(release, steps.stage("fabric"), status)
    ensure_mods(release, steps.stage("mods"), status)
    progress(1.0)
    status("Всё готово")


def is_prepared(release_like: dict) -> bool:
    """Быстрая проверка по сохранённым настройкам: установлено ли что-то вообще."""
    version = release_like.get("minecraft", MC_VERSION)
    return _vanilla_json_path(version).is_file() and (game_dir() / "mods").is_dir()


# ------------------------------------------------------------------ запуск


def offline_uuid(nickname: str) -> str:
    """UUID как у Java UUID.nameUUIDFromBytes("OfflinePlayer:<ник>") — так делает сам Minecraft."""
    digest = bytearray(hashlib.md5(f"OfflinePlayer:{nickname}".encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return uuid.UUID(bytes=bytes(digest)).hex


def _resolve_args(args: list, values: dict[str, str]) -> list[str]:
    result = []
    for arg in args:
        if isinstance(arg, dict):
            if not _rules_allow(arg.get("rules")):
                continue
            value = arg["value"]
            items = value if isinstance(value, list) else [value]
        else:
            items = [arg]
        for item in items:
            for key, replacement in values.items():
                item = item.replace("${" + key + "}", replacement)
            result.append(item)
    return result


def build_command(release_info: dict, nickname: str, ram_mb: int, console: bool = False) -> list[str]:
    mc_version, loader = release_info["minecraft"], release_info["loader"]
    vanilla = json.loads(_vanilla_json_path(mc_version).read_text(encoding="utf-8"))
    fabric_id = f"fabric-loader-{loader}-{mc_version}"
    fabric = json.loads((minecraft_dir() / "versions" / fabric_id / f"{fabric_id}.json").read_text(encoding="utf-8"))

    # Библиотеки Fabric идут первыми и заменяют одноимённые ванильные
    classpath: list[str] = []
    seen: set[str] = set()
    for lib in fabric["libraries"]:
        key = _library_key(lib["name"])
        if key not in seen:
            seen.add(key)
            classpath.append(str(minecraft_dir() / "libraries" / maven_path(lib["name"])))
    for lib in vanilla["libraries"]:
        artifact = lib.get("downloads", {}).get("artifact")
        if not artifact or not _rules_allow(lib.get("rules")):
            continue
        key = _library_key(lib["name"])
        if key not in seen:
            seen.add(key)
            classpath.append(str(minecraft_dir() / "libraries" / artifact["path"]))
    classpath.append(str(minecraft_dir() / "versions" / mc_version / f"{mc_version}.jar"))

    natives = minecraft_dir() / "natives"
    natives.mkdir(parents=True, exist_ok=True)
    values = {
        "auth_player_name": nickname,
        "version_name": fabric_id,
        "game_directory": str(game_dir()),
        "assets_root": str(minecraft_dir() / "assets"),
        "assets_index_name": vanilla["assetIndex"]["id"],
        "auth_uuid": offline_uuid(nickname),
        "auth_access_token": "0",
        "clientid": "0",
        "auth_xuid": "0",
        "user_type": "legacy",
        "version_type": APP_NAME,
        "natives_directory": str(natives),
        "launcher_name": "visual-client",
        "launcher_version": VERSION,
        "classpath": os.pathsep.join(classpath),
        "classpath_separator": os.pathsep,
        "library_directory": str(minecraft_dir() / "libraries"),
    }

    java_major = vanilla.get("javaVersion", {}).get("majorVersion", 17)
    command = [str(java_executable(java_major, console=console)),
               f"-Xmx{ram_mb}M", "-Xms512M",
               "-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=20",
               "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M",
               "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
               "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"]
    command += _resolve_args(vanilla["arguments"]["jvm"], values)
    command += _resolve_args(fabric.get("arguments", {}).get("jvm", []), values)
    command.append(fabric["mainClass"])
    command += _resolve_args(vanilla["arguments"]["game"], values)
    command += _resolve_args(fabric.get("arguments", {}).get("game", []), values)
    return command


def launch(release_info: dict, nickname: str, ram_mb: int) -> subprocess.Popen:
    game_dir().mkdir(parents=True, exist_ok=True)
    log_path = home_dir() / "logs" / "game-output.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log = open(log_path, "w", encoding="utf-8", errors="replace")
    flags = 0
    if IS_WINDOWS:
        flags = subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP
    return subprocess.Popen(build_command(release_info, nickname, ram_mb), cwd=str(game_dir()),
                            stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                            creationflags=flags)


def game_log_tail(lines: int = 15) -> str:
    try:
        text = (home_dir() / "logs" / "game-output.log").read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    return "\n".join(text.splitlines()[-lines:])
