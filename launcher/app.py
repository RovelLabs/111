"""Pulse Client — лаунчер: установка, обновление и запуск игры в одном окне."""

from __future__ import annotations

import json
import os
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import traceback
from pathlib import Path

import customtkinter as ctk
from PIL import Image, ImageDraw, ImageFilter

import brand
import game
import selfupdate
from common import (APP_NAME, IS_WINDOWS, MC_VERSION, VERSION, LauncherError, asset_path, home_dir, load_settings,
                    save_settings)

NICK_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")


# ------------------------------------------------------------------ система


def total_ram_gb() -> int:
    try:
        if IS_WINDOWS:
            import ctypes

            class MemoryStatus(ctypes.Structure):
                _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong),
                            ("ullTotalPhys", ctypes.c_ulonglong), ("ullAvailPhys", ctypes.c_ulonglong),
                            ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                            ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong),
                            ("ullAvailExtendedVirtual", ctypes.c_ulonglong)]

            status = MemoryStatus()
            status.dwLength = ctypes.sizeof(MemoryStatus)
            ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
            return max(2, round(status.ullTotalPhys / 1024 ** 3))
        return max(2, round(os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES") / 1024 ** 3))
    except Exception:
        return 8


def open_path(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    if IS_WINDOWS:
        os.startfile(str(path))  # noqa: S606
    else:
        subprocess.Popen(["xdg-open", str(path)])


def migrate_old_installer() -> None:
    """Убирает следы старого установщика: батники, профиль в официальном лаунчере, его настройки."""
    home = home_dir()
    for name in ("Обновить клиент.bat", "Как играть.txt"):
        (home / name).unlink(missing_ok=True)
    old_mods = home / "mods"
    if old_mods.is_dir() and all(p.name.startswith(("pulse-client-", "fabric-api-")) for p in old_mods.iterdir()):
        shutil.rmtree(old_mods, ignore_errors=True)
    if IS_WINDOWS and os.environ.get("APPDATA"):
        appdata = Path(os.environ["APPDATA"])
        shutil.rmtree(appdata / "PulseClientInstaller", ignore_errors=True)
        profiles = appdata / ".minecraft" / "launcher_profiles.json"
        try:
            data = json.loads(profiles.read_text(encoding="utf-8"))
            if data.get("profiles", {}).pop("pulse-client", None) is not None:
                profiles.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
        except (OSError, ValueError):
            pass


def install_launcher_copy() -> None:
    """Кладёт exe в папку клиента и делает ярлык на рабочем столе — дальше запускать оттуда."""
    if not selfupdate.is_frozen():
        return
    target = home_dir() / f"{APP_NAME}.exe"
    current = selfupdate.current_exe()
    if current.resolve() != target.resolve():
        shutil.copy2(current, target)
    if IS_WINDOWS:
        shortcut = home_dir().parent / f"{APP_NAME}.lnk"
        script = (f"$s=(New-Object -ComObject WScript.Shell).CreateShortcut('{shortcut}');"
                  f"$s.TargetPath='{target}';$s.WorkingDirectory='{home_dir()}';"
                  f"$s.IconLocation='{target},0';$s.Save()")
        subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
                       creationflags=subprocess.CREATE_NO_WINDOW, check=False)


def make_banner(width: int, height: int) -> Image.Image:
    """Баннер: случайный фирменный фон, затемнение снизу и логотип Pulse."""
    import random

    scale = 2  # двойное разрешение для чётких экранов
    w, h = width * scale, height * scale
    art = Image.open(asset_path(f"background_{random.randint(1, 3)}.jpg")).convert("RGB")
    ratio = max(w / art.width, h / art.height)
    art = art.resize((round(art.width * ratio), round(art.height * ratio)), Image.LANCZOS)
    left, top = (art.width - w) // 2, (art.height - h) // 2
    art = art.crop((left, top, left + w, top + h)).convert("RGBA")

    shade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=(8, 6, 20, int(150 * (y / h) ** 1.5)))
    art.alpha_composite(shade)

    # тёмное пятно под логотипом, чтобы он читался на любом фоне
    blob = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(blob).ellipse([w * 0.22, h * 0.12, w * 0.78, h * 0.88], fill=(6, 4, 18, 190))
    art.alpha_composite(blob.filter(ImageFilter.GaussianBlur(h * 0.12)))

    logo = Image.open(asset_path("logo.png")).convert("RGBA")
    lh = int(h * 0.62)
    logo = logo.resize((round(logo.width * lh / logo.height), lh), Image.LANCZOS)
    art.alpha_composite(logo, ((w - logo.width) // 2, (h - logo.height) // 2))

    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=16 * scale, fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(art, (0, 0), mask)
    return out


# ------------------------------------------------------------------ окно


class Launcher(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        ctk.set_appearance_mode("dark")
        self.title(APP_NAME)
        self.geometry("940x640")
        self.minsize(940, 640)
        self.resizable(False, False)
        self.configure(fg_color=brand.BG)
        self._set_icon()

        self.events: queue.Queue = queue.Queue()
        self.settings = load_settings()
        self.release: game.Release | None = None
        self.busy = False
        self.game_process: subprocess.Popen | None = None
        self.max_ram = max(2, min(16, total_ram_gb() - 2))

        self._build()
        self._refresh()
        self.after(60, self._pump)

        selfupdate.cleanup_previous()
        self._set_status("Проверяю обновления…")
        self._background(self._startup_check, "release")

    # ---------------------------------------------------------- построение интерфейса

    def _set_icon(self) -> None:
        if not IS_WINDOWS:
            return
        try:
            path = Path(tempfile.gettempdir()) / "pulse-client-icon.ico"
            brand.save_icon(str(path))
            self.iconbitmap(str(path))
        except Exception:
            pass

    def _build(self) -> None:
        font = lambda size, weight="normal": ctk.CTkFont(family="Segoe UI", size=size, weight=weight)

        # Левая колонка: логотип и «что нового»
        side = ctk.CTkFrame(self, width=320, fg_color=brand.CARD, corner_radius=0)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)

        logo = ctk.CTkImage(light_image=brand.make_logo(128), dark_image=brand.make_logo(128), size=(64, 64))
        header = ctk.CTkFrame(side, fg_color="transparent")
        header.pack(fill="x", padx=28, pady=(32, 0))
        ctk.CTkLabel(header, image=logo, text="").pack(side="left")
        names = ctk.CTkFrame(header, fg_color="transparent")
        names.pack(side="left", padx=14)
        ctk.CTkLabel(names, text="PULSE", font=font(24, "bold"), text_color=brand.TEXT, height=26).pack(anchor="w")
        ctk.CTkLabel(names, text="CLIENT", font=font(24, "bold"), text_color=brand.ACCENT, height=26).pack(anchor="w")

        ctk.CTkLabel(side, text="ЧТО НОВОГО", font=font(12, "bold"), text_color=brand.MUTED).pack(
            anchor="w", padx=28, pady=(36, 6))
        self.notes = ctk.CTkTextbox(side, fg_color=brand.FIELD, text_color=brand.TEXT, font=font(13),
                                    corner_radius=12, border_width=0, wrap="word", activate_scrollbars=True)
        self.notes.pack(fill="both", expand=True, padx=24)
        self._set_notes("Загружаю…")

        self.version_label = ctk.CTkLabel(side, text="", font=font(12), text_color=brand.MUTED)
        self.version_label.pack(anchor="w", padx=28, pady=16)

        # Правая колонка: ник, память, кнопка
        main = ctk.CTkFrame(self, fg_color="transparent")
        main.pack(side="left", fill="both", expand=True, padx=40, pady=32)

        banner = make_banner(540, 180)
        ctk.CTkLabel(main, image=ctk.CTkImage(light_image=banner, dark_image=banner, size=(540, 180)), text="",
                     corner_radius=16).pack(anchor="w")
        ctk.CTkLabel(main, text=f"Свой Minecraft {MC_VERSION}: фирменное меню, визуалы и HUD", font=font(14),
                     text_color=brand.MUTED).pack(anchor="w", pady=(10, 18))

        ctk.CTkLabel(main, text="НИКНЕЙМ", font=font(12, "bold"), text_color=brand.MUTED).pack(anchor="w")
        self.nick = ctk.CTkEntry(main, height=44, corner_radius=10, font=font(15), fg_color=brand.FIELD,
                                 border_color=brand.CARD_BORDER, text_color=brand.TEXT,
                                 placeholder_text="Например, Steve")
        self.nick.pack(fill="x", pady=(6, 18))
        if self.settings.get("nickname"):
            self.nick.insert(0, self.settings["nickname"])

        ram_row = ctk.CTkFrame(main, fg_color="transparent")
        ram_row.pack(fill="x")
        ctk.CTkLabel(ram_row, text="ОПЕРАТИВНАЯ ПАМЯТЬ", font=font(12, "bold"), text_color=brand.MUTED).pack(side="left")
        self.ram_label = ctk.CTkLabel(ram_row, text="", font=font(13, "bold"), text_color=brand.TEXT)
        self.ram_label.pack(side="right")
        self.ram = ctk.CTkSlider(main, from_=2, to=self.max_ram, number_of_steps=max(1, self.max_ram - 2),
                                 button_color=brand.ACCENT, button_hover_color=brand.ACCENT_HOVER,
                                 progress_color=brand.ACCENT, fg_color=brand.FIELD, command=self._on_ram)
        self.ram.set(min(self.max_ram, self.settings.get("ram_gb", min(4, self.max_ram))))
        self.ram.pack(fill="x", pady=(8, 0))
        self._on_ram(self.ram.get())

        self.play = ctk.CTkButton(main, text="", height=58, corner_radius=14, font=font(20, "bold"),
                                  fg_color=brand.ACCENT, hover_color=brand.ACCENT_HOVER, command=self._on_play)
        self.play.pack(fill="x", side="bottom")

        self.status = ctk.CTkLabel(main, text="", font=font(13), text_color=brand.MUTED, anchor="w",
                                   justify="left", wraplength=480)
        self.status.pack(fill="x", side="bottom", pady=(0, 14))
        self.progress = ctk.CTkProgressBar(main, height=6, corner_radius=3, progress_color=brand.ACCENT,
                                           fg_color=brand.FIELD)
        self.progress.set(0)
        self.progress.pack(fill="x", side="bottom", pady=(0, 8))

        tools = ctk.CTkFrame(main, fg_color="transparent")
        tools.pack(fill="x", side="bottom", pady=(0, 18))
        small = dict(height=32, corner_radius=8, font=font(12), fg_color=brand.FIELD, hover_color=brand.CARD_BORDER,
                     text_color=brand.TEXT)
        ctk.CTkButton(tools, text="Папка игры", command=lambda: open_path(game.game_dir()), **small).pack(
            side="left", padx=(0, 8))
        self.repair_button = ctk.CTkButton(tools, text="⟳  Обновить", command=self._on_update, **small)
        self.repair_button.pack(side="left")

    def _set_notes(self, text: str) -> None:
        self.notes.configure(state="normal")
        self.notes.delete("1.0", "end")
        self.notes.insert("1.0", text)
        self.notes.configure(state="disabled")

    def _on_ram(self, value: float) -> None:
        self.ram_label.configure(text=f"{int(round(value))} ГБ")

    def _installed(self) -> bool:
        return bool(self.settings.get("installed")) and game.is_prepared(self.settings.get("release", {}))

    def _refresh(self) -> None:
        if self.game_process is not None:
            label = "ИГРА ЗАПУЩЕНА"
        elif self.busy:
            label = "ПОДОЖДИТЕ…"
        else:
            label = "ИГРАТЬ" if self._installed() else "УСТАНОВИТЬ"
        blocked = self.busy or self.game_process is not None
        self.play.configure(text=label, state="disabled" if blocked else "normal",
                            fg_color=brand.ACCENT_DARK if blocked else brand.ACCENT)
        self.repair_button.configure(state="disabled" if blocked or not self._installed() else "normal")
        installed = self.settings.get("release", {}).get("version")
        self.version_label.configure(
            text=f"Лаунчер {VERSION}" + (f"  ·  клиент {installed}" if installed else ""))

    def _set_status(self, text: str, color: str = brand.MUTED) -> None:
        self.status.configure(text=text, text_color=color)

    # ---------------------------------------------------------- действия

    def _on_play(self) -> None:
        if self.busy or self.game_process is not None:
            return
        nickname = self.nick.get().strip()
        installing = not self._installed()
        if not installing and not NICK_RE.match(nickname):
            self._set_status("Ник: 3–16 символов, только латиница, цифры и _", brand.ERROR)
            self.nick.focus()
            return
        self.settings["nickname"] = nickname
        self.settings["ram_gb"] = int(round(self.ram.get()))
        self._start(lambda: self._prepare_and_maybe_launch(launch=not installing))

    def _on_update(self) -> None:
        """Кнопка «Обновить»: сначала сам лаунчер, потом клиент и все файлы игры."""
        if self.busy:
            return

        def job():
            self.events.put(("status", "Проверяю обновления…"))
            release = game.fetch_release()
            self.events.put(("release", release))
            if selfupdate.needs_update(release):
                self.events.put(("status", f"Обновляю лаунчер до версии {release.version}…"))
                selfupdate.apply_update(release, lambda d, t: self.events.put(("progress", d / t if t else 0)))
            before = self.settings.get("release", {}).get("version")
            self._prepare_and_maybe_launch(launch=False)
            return "updated" if before != release.version else "uptodate"

        self._start(job)

    def _start(self, job) -> None:
        self.busy = True
        self.progress.set(0)
        self._refresh()
        self._background(job, "done")

    def _prepare_and_maybe_launch(self, launch: bool):
        status = lambda text: self.events.put(("status", text))
        progress = lambda value: self.events.put(("progress", value))

        release = self.release
        if release is None:
            try:
                release = game.fetch_release()
                self.events.put(("release", release))
            except LauncherError:
                if not (launch and self._installed()):
                    raise
                status("Нет связи с GitHub — запускаю установленную версию")

        if not self.settings.get("installed"):
            status("Подготавливаю папку…")
            home_dir().mkdir(parents=True, exist_ok=True)
            migrate_old_installer()
            install_launcher_copy()

        if release is not None:
            game.prepare(release, progress, status)
            self.settings["release"] = {"version": release.version, "minecraft": release.minecraft,
                                        "loader": release.loader, "fabric_api": release.fabric_api}
        self.settings["installed"] = True
        save_settings(self.settings)

        if not launch:
            return "installed"
        status("Запускаю игру…")
        process = game.launch(self.settings["release"], self.settings["nickname"], self.settings["ram_gb"] * 1024)
        return process

    # ---------------------------------------------------------- старт и обновления

    def _startup_check(self) -> game.Release:
        release = game.fetch_release()
        if selfupdate.needs_update(release):
            self.events.put(("status", f"Обновляю лаунчер до версии {release.version}…"))
            self.events.put(("busy", True))
            selfupdate.apply_update(release, lambda d, t: self.events.put(("progress", d / t if t else 0)))
        return release

    def _watch_game(self, process: subprocess.Popen) -> None:
        started = time.time()
        code = process.wait()
        self.events.put(("game_exit", (code, time.time() - started)))

    # ---------------------------------------------------------- фоновые задачи

    def _background(self, job, kind: str) -> None:
        def worker():
            try:
                self.events.put((kind, job()))
            except LauncherError as e:
                self.events.put(("error", (kind, str(e))))
            except Exception as e:  # неожиданное — показываем, а не падаем молча
                traceback.print_exc()
                self.events.put(("error", (kind, f"Непредвиденная ошибка: {e}")))

        threading.Thread(target=worker, daemon=True).start()

    def _pump(self) -> None:
        try:
            while True:
                kind, value = self.events.get_nowait()
                self._handle(kind, value)
        except queue.Empty:
            pass
        self.after(60, self._pump)

    def _handle(self, kind: str, value) -> None:
        if kind == "progress":
            self.progress.set(max(0.0, min(1.0, value)))
        elif kind == "status":
            self._set_status(value)
        elif kind == "busy":
            self.busy = value
            self._refresh()
        elif kind == "release":
            self.release = value
            self._set_notes(f"Версия {value.version}\n\n{value.notes.strip()}")
            if not self.busy:
                installed = self.settings.get("release", {}).get("version")
                if not self._installed():
                    self._set_status("Нажмите «Установить» — всё нужное скачается автоматически (~600 МБ)")
                elif installed != value.version:
                    self._set_status(f"Доступна версия {value.version} — нажмите «Обновить» "
                                     "(или она обновится сама при нажатии «Играть»)", brand.ACCENT)
                else:
                    self._set_status("Готово к игре")
        elif kind == "done":
            self.busy = False
            if value == "updated":
                self.progress.set(1)
                version = self.settings.get("release", {}).get("version")
                self._set_status(f"Обновлено до версии {version}!", brand.SUCCESS)
            elif value == "uptodate":
                self.progress.set(1)
                self._set_status("У вас последняя версия, все файлы на месте", brand.SUCCESS)
            elif value == "installed":
                self.progress.set(1)
                self._set_status(f"Установлено! Папка «{APP_NAME}» и ярлык — на рабочем столе. "
                                 "Введите ник и нажмите «Играть».", brand.SUCCESS)
            else:
                self.game_process = value
                self._set_status("Игра запускается — окно Minecraft появится через несколько секунд", brand.SUCCESS)
                threading.Thread(target=self._watch_game, args=(value,), daemon=True).start()
                self.after(4000, self.iconify)
            self._refresh()
        elif kind == "game_exit":
            code, seconds = value
            self.game_process = None
            self.deiconify()
            if code != 0:
                tail = game.game_log_tail(6)
                self._set_status(f"Игра закрылась с ошибкой (код {code}).\n{tail}\n"
                                 f"Полный лог: {home_dir() / 'logs' / 'game-output.log'}", brand.ERROR)
            else:
                self._set_status("Игра закрыта")
            self._refresh()
        elif kind == "error":
            source, message = value
            self.busy = False
            if source == "release":
                self._set_notes("Не удалось загрузить список изменений.")
            self._set_status(message, brand.ERROR)
            self._refresh()


def main() -> None:
    window = Launcher()
    if len(sys.argv) > 2 and sys.argv[1] == "--smoke-test":
        # Проверка собранного exe в CI: окно открылось — пишем отчёт и закрываемся
        def report():
            Path(sys.argv[2]).write_text(f"ok {VERSION} {window.play.cget('text')}", encoding="utf-8")
            window.destroy()

        window.after(4000, report)
    window.mainloop()


if __name__ == "__main__":
    main()
