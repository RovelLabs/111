"""Общие вещи лаунчера: название, пути, загрузка файлов."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable, Optional

APP_NAME = "Pulse Client"
REPO = "RovelLabs/111"
MC_VERSION = "1.20.1"

try:  # файл создаётся при сборке релиза
    from _build import VERSION  # type: ignore
except ImportError:
    VERSION = "dev"

IS_WINDOWS = sys.platform == "win32"
USER_AGENT = f"PulseClientLauncher/{VERSION}"


class LauncherError(Exception):
    """Ошибка, текст которой можно показать пользователю."""


# ------------------------------------------------------------------ пути


def _windows_desktop() -> Optional[Path]:
    try:
        import ctypes
        from ctypes import wintypes

        buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
        # CSIDL_DESKTOPDIRECTORY = 0x10 — учитывает перенос рабочего стола в OneDrive
        if ctypes.windll.shell32.SHGetFolderPathW(None, 0x10, None, 0, buf) == 0:
            return Path(buf.value)
    except Exception:
        pass
    return None


def desktop_dir() -> Path:
    if IS_WINDOWS:
        found = _windows_desktop()
        if found:
            return found
    return Path.home() / "Desktop"


def home_dir() -> Path:
    """Папка клиента: <Рабочий стол>/Pulse Client.
    Если лаунчер запущен из уже установленной папки (там лежит launcher.json) — это она,
    даже если папку переименовали (например, старая «Visual Client»)."""
    override = os.environ.get("VC_HOME")
    if override:
        return Path(override)
    if getattr(sys, "frozen", False):
        exe_dir = Path(sys.executable).parent
        if (exe_dir / "launcher.json").is_file():
            return exe_dir
    return desktop_dir() / APP_NAME


def asset_path(name: str) -> Path:
    """Картинки лаунчера: внутри exe (PyInstaller) или рядом со скриптами."""
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).parent))
    return base / "assets" / name


def game_dir() -> Path:
    """Миры, настройки игры, mods/ — то, что видит сама игра."""
    return home_dir() / "game"


def minecraft_dir() -> Path:
    """Файлы Minecraft: versions/, libraries/, assets/."""
    return home_dir() / "minecraft"


def runtime_dir() -> Path:
    """Скачанная Java."""
    return home_dir() / "runtime"


def settings_file() -> Path:
    return home_dir() / "launcher.json"


def load_settings() -> dict:
    try:
        return json.loads(settings_file().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def save_settings(settings: dict) -> None:
    settings_file().parent.mkdir(parents=True, exist_ok=True)
    tmp = settings_file().with_suffix(".tmp")
    tmp.write_text(json.dumps(settings, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, settings_file())


# ------------------------------------------------------------------ сеть


def _request(url: str) -> urllib.request.Request:
    return urllib.request.Request(url, headers={"User-Agent": USER_AGENT})


def _host(url: str) -> str:
    return urllib.parse.urlparse(url).netloc or url


def get_json(url: str, retries: int = 3) -> dict:
    last: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(_request(url), timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code < 500:
                raise LauncherError(f"Сервер {_host(url)} ответил ошибкой {e.code}") from e
            last = e
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            last = e
        time.sleep(1 + attempt * 2)
    raise LauncherError(f"Нет связи с {_host(url)}. Проверьте интернет.") from last


def file_sha1(path: Path) -> str:
    digest = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_valid(path: Path, size: Optional[int] = None, sha1: Optional[str] = None) -> bool:
    if not path.is_file():
        return False
    if size is not None and path.stat().st_size != size:
        return False
    if sha1 and file_sha1(path) != sha1.lower():
        return False
    return True


def _remove_quietly(path: Path) -> None:
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass  # файл занят антивирусом или другим процессом — не критично


def download(url: str, target: Path, *, sha1: Optional[str] = None, sha256: Optional[str] = None,
             on_bytes: Optional[Callable[[int, int], None]] = None, retries: int = 3) -> None:
    """Скачивает во временный файл, проверяет хэш и переименовывает.
    on_bytes(скачано, всего) вызывается по ходу загрузки."""
    target.parent.mkdir(parents=True, exist_ok=True)
    # Своё имя временного файла на каждый поток — параллельные загрузки не мешают друг другу
    partial = target.with_name(f"{target.name}.{os.getpid()}-{threading.get_ident()}.part")
    last: Exception | None = None
    for attempt in range(retries):
        try:
            sha1_d, sha256_d = hashlib.sha1(), hashlib.sha256()
            with urllib.request.urlopen(_request(url), timeout=60) as response, open(partial, "wb") as out:
                total = int(response.headers.get("Content-Length") or 0)
                done = 0
                while True:
                    chunk = response.read(256 * 1024)
                    if not chunk:
                        break
                    out.write(chunk)
                    sha1_d.update(chunk)
                    sha256_d.update(chunk)
                    done += len(chunk)
                    if on_bytes:
                        on_bytes(done, total)
            if sha1 and sha1_d.hexdigest() != sha1.lower():
                raise LauncherError(f"{target.name}: файл скачался повреждённым")
            if sha256 and sha256_d.hexdigest() != sha256.lower():
                raise LauncherError(f"{target.name}: файл скачался повреждённым")
            os.replace(partial, target)
            return
        except urllib.error.HTTPError as e:
            _remove_quietly(partial)
            if e.code < 500:
                raise LauncherError(f"Не удалось скачать {target.name}: ошибка {e.code}") from e
            last = e
        except (LauncherError, urllib.error.URLError, TimeoutError, OSError) as e:
            _remove_quietly(partial)
            last = e
        time.sleep(1 + attempt * 2)
    if isinstance(last, LauncherError):
        raise last
    raise LauncherError(f"Не удалось скачать {target.name} с {_host(url)}. Проверьте интернет.") from last


def parse_version(text: str) -> tuple:
    parts = []
    for piece in text.lstrip("vV").split("."):
        digits = "".join(ch for ch in piece if ch.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts)
