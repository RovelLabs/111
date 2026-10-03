"""Окно установщика Visual Client: кнопки «Установить» и «Обновить»."""

from __future__ import annotations

import os
import queue
import subprocess
import sys
import threading
import traceback

import customtkinter as ctk

import core

ACCENT = "#8A5CF6"
ACCENT_HOVER = "#7443F0"
BG = "#0F0F14"
CARD = "#181820"
MUTED = "#8B8B99"


class InstallerApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        ctk.set_appearance_mode("dark")
        self.title(f"{core.CLIENT_NAME} — установщик")
        self.geometry("560x440")
        self.resizable(False, False)
        self.configure(fg_color=BG)

        self.events: queue.Queue = queue.Queue()
        self.busy = False
        self.install_state = core.load_state()
        self.latest: core.Release | None = None

        self._build()
        self._refresh_buttons()
        self._run_in_background(self._check_latest)
        self.after(50, self._pump_events)

    # ------------------------------------------------------------ интерфейс

    def _build(self) -> None:
        ctk.CTkLabel(self, text=core.CLIENT_NAME.upper(), font=ctk.CTkFont(size=30, weight="bold"),
                     text_color="white").pack(pady=(28, 0))
        ctk.CTkLabel(self, text="Minecraft 1.20.1 · Fabric", font=ctk.CTkFont(size=13),
                     text_color=MUTED).pack()

        card = ctk.CTkFrame(self, fg_color=CARD, corner_radius=14)
        card.pack(fill="x", padx=28, pady=(22, 0))
        self.installed_label = ctk.CTkLabel(card, text="", font=ctk.CTkFont(size=14), anchor="w")
        self.installed_label.pack(fill="x", padx=18, pady=(14, 2))
        self.latest_label = ctk.CTkLabel(card, text="Последняя версия: проверяю…", font=ctk.CTkFont(size=14),
                                         anchor="w", text_color=MUTED)
        self.latest_label.pack(fill="x", padx=18)
        self.path_label = ctk.CTkLabel(card, text="", font=ctk.CTkFont(size=12), anchor="w",
                                       text_color=MUTED, wraplength=480, justify="left")
        self.path_label.pack(fill="x", padx=18, pady=(2, 14))

        buttons = ctk.CTkFrame(self, fg_color="transparent")
        buttons.pack(fill="x", padx=28, pady=(18, 0))
        buttons.grid_columnconfigure((0, 1), weight=1)
        self.install_button = ctk.CTkButton(buttons, text="Установить", height=46, corner_radius=12,
                                            font=ctk.CTkFont(size=16, weight="bold"),
                                            fg_color=ACCENT, hover_color=ACCENT_HOVER, command=self._on_install)
        self.install_button.grid(row=0, column=0, sticky="ew", padx=(0, 6))
        self.update_button = ctk.CTkButton(buttons, text="Обновить", height=46, corner_radius=12,
                                           font=ctk.CTkFont(size=16, weight="bold"),
                                           fg_color="#2A2A36", hover_color="#34344A", command=self._on_update)
        self.update_button.grid(row=0, column=1, sticky="ew", padx=(6, 0))

        self.progress = ctk.CTkProgressBar(self, height=8, corner_radius=4, progress_color=ACCENT)
        self.progress.set(0)
        self.progress.pack(fill="x", padx=28, pady=(20, 6))
        self.status_label = ctk.CTkLabel(self, text="", font=ctk.CTkFont(size=12), text_color=MUTED,
                                         wraplength=500, justify="left", anchor="w")
        self.status_label.pack(fill="x", padx=28)

        self.open_button = ctk.CTkButton(self, text="Открыть папку клиента", height=30, fg_color="transparent",
                                         border_width=1, border_color="#2A2A36", hover_color="#1E1E28",
                                         command=self._open_folder)
        self.open_button.pack(side="bottom", pady=(0, 18))

    def _refresh_buttons(self) -> None:
        installed = self.install_state is not None
        self.installed_label.configure(
            text=f"Установлено: {self.install_state.version}" if installed else "Клиент ещё не установлен")
        target = self.install_state.install_dir if installed else core.default_install_dir()
        self.path_label.configure(text=f"Папка: {target}")
        self.install_button.configure(text="Переустановить" if installed else "Установить",
                                      state="disabled" if self.busy else "normal")
        can_update = installed and not self.busy
        self.update_button.configure(state="normal" if can_update else "disabled",
                                     fg_color=ACCENT if can_update and self._update_available() else "#2A2A36")
        self.open_button.configure(state="normal" if installed else "disabled")

    def _update_available(self) -> bool:
        return bool(self.install_state and self.latest
                    and core.parse_version(self.latest.version) > core.parse_version(self.install_state.version))

    # ------------------------------------------------------------ действия

    def _on_install(self) -> None:
        self._start(force=True)

    def _on_update(self) -> None:
        self._start(force=False)

    def _start(self, force: bool) -> None:
        if self.busy:
            return
        self.busy = True
        self.progress.set(0)
        self._refresh_buttons()
        install_dir = self.install_state.install_dir if self.install_state else None
        self._run_in_background(lambda: core.install(
            install_dir,
            progress=lambda p: self.events.put(("progress", p)),
            log=lambda msg: self.events.put(("status", msg)),
            force=force,
        ), on_done="installed")

    def _check_latest(self) -> core.Release:
        return core.fetch_latest_release()

    def _open_folder(self) -> None:
        if not self.install_state:
            return
        path = str(self.install_state.install_dir)
        if sys.platform == "win32":
            os.startfile(path)  # noqa: S606 — открываем проводник
        else:
            subprocess.Popen(["xdg-open", path])

    # ------------------------------------------------------------ фоновые задачи

    def _run_in_background(self, job, on_done: str = "latest") -> None:
        def worker():
            try:
                self.events.put((on_done, job()))
            except core.InstallerError as e:
                self.events.put(("error", str(e)))
            except Exception as e:  # неожиданная ошибка — показываем, а не молча падаем
                traceback.print_exc()
                self.events.put(("error", f"Непредвиденная ошибка: {e}"))

        threading.Thread(target=worker, daemon=True).start()

    def _pump_events(self) -> None:
        try:
            while True:
                kind, value = self.events.get_nowait()
                if kind == "progress":
                    self.progress.set(max(0.0, min(1.0, value)))
                elif kind == "status":
                    self.status_label.configure(text=value, text_color=MUTED)
                elif kind == "latest":
                    self.latest = value
                    self.latest_label.configure(text=f"Последняя версия: {value.version}")
                    self._refresh_buttons()
                elif kind == "installed":
                    self.busy = False
                    self.install_state = value
                    self.status_label.configure(
                        text=f"Готово! Запустите лаунчер Minecraft и выберите профиль «{core.CLIENT_NAME}».",
                        text_color="#7EE787")
                    self._refresh_buttons()
                elif kind == "error":
                    self.busy = False
                    self.status_label.configure(text=value, text_color="#FF7B72")
                    if self.latest is None:
                        self.latest_label.configure(text="Последняя версия: нет связи с GitHub")
                    self._refresh_buttons()
        except queue.Empty:
            pass
        self.after(50, self._pump_events)


if __name__ == "__main__":
    InstallerApp().mainloop()
