"""Самообновление лаунчера: если на GitHub вышел релиз новее, exe скачивает сам себя и перезапускается."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

from common import VERSION, download, parse_version
from game import Release


def is_frozen() -> bool:
    """True, когда запущен собранный exe, а не python-скрипт."""
    return bool(getattr(sys, "frozen", False))


def current_exe() -> Path:
    return Path(sys.executable)


def cleanup_previous() -> None:
    """Удаляет exe, оставшийся от прошлого обновления."""
    if not is_frozen():
        return
    old = current_exe().with_name(current_exe().stem + ".old.exe")
    try:
        old.unlink(missing_ok=True)
    except OSError:
        pass  # старый процесс ещё не закрылся — удалим в следующий раз


def needs_update(release: Release) -> bool:
    return (is_frozen() and VERSION != "dev" and release.launcher_url is not None
            and parse_version(release.version) > parse_version(VERSION))


def apply_update(release: Release, on_bytes) -> None:
    """Скачивает новый exe рядом с текущим, подменяет его и запускает. Не возвращается."""
    exe = current_exe()
    new = exe.with_name(exe.stem + ".new.exe")
    old = exe.with_name(exe.stem + ".old.exe")
    download(release.launcher_url, new, on_bytes=on_bytes)
    old.unlink(missing_ok=True)
    os.replace(exe, old)  # запущенный exe в Windows можно переименовать, но не перезаписать
    os.replace(new, exe)
    subprocess.Popen([str(exe)], close_fds=True)
    os._exit(0)
