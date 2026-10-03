"""Логика установщика: скачивание клиента из GitHub Releases и настройка лаунчера Minecraft.

Схема установки:
  <Рабочий стол>/Visual Client/          — папка игры (gameDir профиля)
      mods/visual-client-<версия>.jar
      mods/fabric-api-<версия>.jar
  %APPDATA%/.minecraft/versions/fabric-loader-<loader>-<mc>/   — Fabric
  %APPDATA%/.minecraft/launcher_profiles.json                  — профиль "Visual Client"
  %APPDATA%/VisualClientInstaller/state.json                   — что и куда установлено

Что ставить, берётся из manifest.json последнего релиза, поэтому новая версия клиента,
Fabric или Fabric API подтягивается кнопкой «Обновить» без пересборки установщика.
"""

from __future__ import annotations

import datetime as _dt
import hashlib
import json
import os
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

REPO = "RovelLabs/111"
CLIENT_NAME = "Visual Client"
PROFILE_ID = "visual-client"

# Адреса можно переопределить переменными окружения (используется в тестах).
GITHUB_API = os.environ.get("VC_GITHUB_API", "https://api.github.com")
FABRIC_META = os.environ.get("VC_FABRIC_META", "https://meta.fabricmc.net")
FABRIC_MAVEN = os.environ.get("VC_FABRIC_MAVEN", "https://maven.fabricmc.net")

USER_AGENT = "VisualClientInstaller"

Progress = Callable[[float], None]  # 0.0 .. 1.0
Log = Callable[[str], None]


class InstallerError(Exception):
    """Ошибка, текст которой можно показать пользователю."""


# ---------------------------------------------------------------- пути


def _windows_desktop() -> Optional[Path]:
    try:
        import ctypes
        from ctypes import wintypes

        buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
        # CSIDL_DESKTOPDIRECTORY = 0x10; учитывает перенос рабочего стола в OneDrive.
        if ctypes.windll.shell32.SHGetFolderPathW(None, 0x10, None, 0, buf) == 0:
            return Path(buf.value)
    except Exception:
        pass
    return None


def desktop_dir() -> Path:
    override = os.environ.get("VC_DESKTOP")
    if override:
        return Path(override)
    if sys.platform == "win32":
        found = _windows_desktop()
        if found:
            return found
    return Path.home() / "Desktop"


def minecraft_dir() -> Path:
    override = os.environ.get("VC_MINECRAFT_DIR")
    if override:
        return Path(override)
    if sys.platform == "win32":
        return Path(os.environ["APPDATA"]) / ".minecraft"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "minecraft"
    return Path.home() / ".minecraft"


def installer_data_dir() -> Path:
    override = os.environ.get("VC_DATA_DIR")
    if override:
        return Path(override)
    if sys.platform == "win32":
        return Path(os.environ["APPDATA"]) / "VisualClientInstaller"
    return Path.home() / ".visualclient-installer"


def default_install_dir() -> Path:
    return desktop_dir() / CLIENT_NAME


# ---------------------------------------------------------------- сеть


def _request(url: str, accept: str = "application/json") -> urllib.request.Request:
    return urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})


def _get_json(url: str) -> dict:
    try:
        with urllib.request.urlopen(_request(url), timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise InstallerError(f"Сервер ответил {e.code} на запрос {url}") from e
    except urllib.error.URLError as e:
        raise InstallerError(f"Нет соединения с {urllib.parse.urlparse(url).netloc}: {e.reason}") from e


def _download(url: str, target: Path, progress: Optional[Progress] = None, sha256: Optional[str] = None) -> None:
    """Скачивает во временный файл и переименовывает — оборванная загрузка не портит установку."""
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    digest = hashlib.sha256()
    try:
        with urllib.request.urlopen(_request(url, "application/octet-stream"), timeout=60) as response, \
                open(partial, "wb") as out:
            total = int(response.headers.get("Content-Length") or 0)
            done = 0
            while True:
                chunk = response.read(64 * 1024)
                if not chunk:
                    break
                out.write(chunk)
                digest.update(chunk)
                done += len(chunk)
                if progress and total:
                    progress(done / total)
    except urllib.error.URLError as e:
        partial.unlink(missing_ok=True)
        raise InstallerError(f"Не удалось скачать {target.name}: {getattr(e, 'reason', e)}") from e

    if sha256 and digest.hexdigest().lower() != sha256.lower():
        partial.unlink(missing_ok=True)
        raise InstallerError(f"Файл {target.name} скачался повреждённым (не совпала контрольная сумма)")
    os.replace(partial, target)


# ---------------------------------------------------------------- релизы


@dataclass
class Release:
    version: str
    minecraft: str
    loader: str
    fabric_api: str
    client_jar: str
    client_url: str
    client_sha256: Optional[str]
    notes: str


def parse_version(text: str) -> tuple:
    parts = []
    for piece in text.lstrip("vV").split("."):
        digits = "".join(ch for ch in piece if ch.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts)


def fetch_latest_release() -> Release:
    data = _get_json(f"{GITHUB_API}/repos/{REPO}/releases/latest")
    assets = {a["name"]: a["browser_download_url"] for a in data.get("assets", [])}
    if "manifest.json" not in assets:
        raise InstallerError("В последнем релизе нет manifest.json — релиз собран неправильно")

    manifest = _get_json(assets["manifest.json"])
    client_jar = manifest["client_jar"]
    if client_jar not in assets:
        raise InstallerError(f"В релизе нет файла клиента {client_jar}")

    return Release(
        version=manifest["version"],
        minecraft=manifest["minecraft"],
        loader=manifest["loader"],
        fabric_api=manifest["fabric_api"],
        client_jar=client_jar,
        client_url=assets[client_jar],
        client_sha256=manifest.get("client_sha256"),
        notes=data.get("body") or "",
    )


# ---------------------------------------------------------------- состояние


@dataclass
class InstallState:
    install_dir: Path
    version: str
    minecraft: str
    loader: str
    fabric_api: str
    client_jar: str

    @property
    def is_present(self) -> bool:
        return (self.install_dir / "mods" / self.client_jar).is_file()


def _state_file() -> Path:
    return installer_data_dir() / "state.json"


def load_state() -> Optional[InstallState]:
    try:
        raw = json.loads(_state_file().read_text(encoding="utf-8"))
        state = InstallState(install_dir=Path(raw.pop("install_dir")), **raw)
    except (OSError, ValueError, KeyError, TypeError):
        return None
    return state if state.is_present else None


def _save_state(state: InstallState) -> None:
    _state_file().parent.mkdir(parents=True, exist_ok=True)
    raw = dict(state.__dict__)
    raw["install_dir"] = str(state.install_dir)
    _state_file().write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")


# ---------------------------------------------------------------- шаги установки


def _fabric_api_name(version: str) -> str:
    return f"fabric-api-{version}.jar"


def _install_fabric_loader(release: Release, log: Log) -> str:
    """Кладёт профиль версии Fabric в .minecraft/versions, как это делает официальный Fabric Installer."""
    mc_dir = minecraft_dir()
    url = f"{FABRIC_META}/v2/versions/loader/{release.minecraft}/{release.loader}/profile/json"
    profile = _get_json(url)
    version_id = profile["id"]

    version_dir = mc_dir / "versions" / version_id
    version_dir.mkdir(parents=True, exist_ok=True)
    (version_dir / f"{version_id}.json").write_text(json.dumps(profile, indent=2), encoding="utf-8")
    # Лаунчер ожидает jar рядом с json; сам код игры он скачает по inheritsFrom.
    jar = version_dir / f"{version_id}.jar"
    if not jar.exists():
        jar.write_bytes(b"")
    log(f"Fabric {release.loader} для Minecraft {release.minecraft} установлен")
    return version_id


def _add_launcher_profile(version_id: str, install_dir: Path, log: Log) -> None:
    profiles_file = minecraft_dir() / "launcher_profiles.json"
    if profiles_file.exists():
        try:
            data = json.loads(profiles_file.read_text(encoding="utf-8"))
        except ValueError:
            shutil.copy2(profiles_file, profiles_file.with_suffix(".json.broken"))
            data = {}
    else:
        data = {}
    data.setdefault("profiles", {})

    now = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    existing = data["profiles"].get(PROFILE_ID, {})
    data["profiles"][PROFILE_ID] = {
        **existing,
        "name": CLIENT_NAME,
        "type": "custom",
        "created": existing.get("created", now),
        "lastUsed": now,
        "icon": "Furnace",
        "lastVersionId": version_id,
        "gameDir": str(install_dir),
    }
    data.setdefault("version", 3)

    profiles_file.parent.mkdir(parents=True, exist_ok=True)
    tmp = profiles_file.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, profiles_file)
    log(f"Профиль «{CLIENT_NAME}» добавлен в лаунчер Minecraft")


def _replace_mod(mods_dir: Path, old_name: Optional[str], new_name: str, url: str, sha256: Optional[str],
                 progress: Progress, log: Log, label: str, force: bool) -> None:
    target = mods_dir / new_name
    if not force and target.is_file() and old_name == new_name:
        log(f"{label}: уже актуален")
        progress(1.0)
        return
    log(f"Скачиваю {label} ({new_name})…")
    _download(url, target, progress, sha256)
    if old_name and old_name != new_name:
        (mods_dir / old_name).unlink(missing_ok=True)


def install(install_dir: Optional[Path], progress: Progress, log: Log, force: bool = True) -> InstallState:
    """Установка (force=True) или обновление (force=False) до последнего релиза."""
    previous = load_state()
    if install_dir is None:
        install_dir = previous.install_dir if previous else default_install_dir()

    log("Получаю информацию о последней версии…")
    progress(0.02)
    release = fetch_latest_release()
    log(f"Последняя версия клиента: {release.version}")

    if not force and previous and previous.version == release.version \
            and previous.loader == release.loader and previous.fabric_api == release.fabric_api:
        progress(1.0)
        log("У вас уже последняя версия — обновлять нечего")
        return previous

    mods_dir = install_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    keep_old = previous if previous and previous.install_dir == install_dir else None

    def stage(start: float, end: float) -> Progress:
        return lambda p: progress(start + (end - start) * p)

    _replace_mod(mods_dir, keep_old.client_jar if keep_old else None, release.client_jar,
                 release.client_url, release.client_sha256, stage(0.05, 0.55), log, "клиент", force)

    fabric_api_jar = _fabric_api_name(release.fabric_api)
    fabric_api_url = (f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/"
                      f"{urllib.parse.quote(release.fabric_api)}/{urllib.parse.quote(fabric_api_jar)}")
    _replace_mod(mods_dir, _fabric_api_name(keep_old.fabric_api) if keep_old else None, fabric_api_jar,
                 fabric_api_url, None, stage(0.55, 0.9), log, "Fabric API", force)

    log("Настраиваю лаунчер Minecraft…")
    version_id = _install_fabric_loader(release, log)
    _add_launcher_profile(version_id, install_dir, log)
    progress(0.97)

    state = InstallState(install_dir=install_dir, version=release.version, minecraft=release.minecraft,
                         loader=release.loader, fabric_api=release.fabric_api, client_jar=release.client_jar)
    _save_state(state)
    progress(1.0)
    log(f"Готово! {CLIENT_NAME} {release.version} установлен в {install_dir}")
    return state
