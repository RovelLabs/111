"""Проверка лаунчера в CI: ставит всё с нуля, запускает игру и смотрит, что клиент загрузился.

Использование: python ci_test.py <секунд ожидания>
Окружение: VC_HOME — куда ставить, VC_LOCAL_RELEASE — папка с manifest.json и jar клиента.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import game  # noqa: E402
from common import home_dir  # noqa: E402

wait = int(sys.argv[1]) if len(sys.argv) > 1 else 120
last = [-1]


def progress(p: float) -> None:
    step = int(p * 10)
    if step != last[0]:
        last[0] = step
        print(f"  {step * 10}%", flush=True)


release = game.fetch_release()
print(f"Релиз: {release.version}, Minecraft {release.minecraft}, Fabric {release.loader}")
started = time.time()
game.prepare(release, progress, lambda s: print(s, flush=True))
print(f"Установка заняла {time.time() - started:.0f} с")

# Повторная подготовка не должна ничего качать
started = time.time()
game.prepare(release, lambda p: None, lambda s: None)
print(f"Повторная проверка файлов: {time.time() - started:.1f} с")

# Программный OpenGL (Mesa) для машины без видеокарты — если положили рядом
mesa = os.environ.get("VC_MESA_DIR")
info = {"minecraft": release.minecraft, "loader": release.loader}
java_bin = game.java_executable(17).parent
if mesa:
    for dll in Path(mesa).glob("*.dll"):
        shutil.copy2(dll, java_bin / dll.name)
    print("Mesa скопирована:", sorted(p.name for p in Path(mesa).glob("*.dll")))

game.ensure_default_options()
command = game.build_command(info, "CiTester", 2048, console=True)
command.insert(1, "-Dpulseclient.selftest=true")  # клиент сам создаст мир, включит модули и откроет меню
log_path = home_dir() / "ci-game.log"
with open(log_path, "w", encoding="utf-8", errors="replace") as log:
    process = subprocess.Popen(command, cwd=str(game.game_dir()), stdout=log, stderr=subprocess.STDOUT)
    seen = set()
    markers = {"Pulse главное меню активно": "главное меню", "Pulse self-test: в мире": "вошли в мир",
               "Pulse self-test OK": "автотест в мире"}
    for second in range(wait):
        if process.poll() is not None:
            break
        current = log_path.read_text(encoding="utf-8", errors="replace")
        for marker, name in markers.items():
            if marker not in seen and marker in current:
                seen.add(marker)
                print(f"{second:4d} с: {name}", flush=True)
        if "Pulse self-test OK" in seen:
            break
        time.sleep(1)
    # ждём, пока игра дойдёт до главного меню (на программном OpenGL это долго), но не дольше wait
    alive = process.poll() is None
    shot = os.environ.get("VC_SCREENSHOT")
    if alive and shot and sys.platform == "win32":
        subprocess.run(["powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
                        "$b=[System.Windows.Forms.Screen]::PrimaryScreen.Bounds;"
                        "$bmp=New-Object Drawing.Bitmap $b.Width,$b.Height;"
                        "$g=[Drawing.Graphics]::FromImage($bmp);"
                        "$g.CopyFromScreen($b.Location,[Drawing.Point]::Empty,$b.Size);"
                        f"$bmp.Save('{shot}')"], check=False)
        try:  # уменьшенная копия скриншота прямо в лог — чтобы посмотреть без скачивания артефактов
            import base64, io
            from PIL import Image
            small = Image.open(shot).convert("RGB")
            small.thumbnail((640, 360))
            buf = io.BytesIO()
            small.save(buf, "JPEG", quality=70)
            print("SCREENSHOT-BASE64-BEGIN")
            print(base64.b64encode(buf.getvalue()).decode())
            print("SCREENSHOT-BASE64-END")
        except Exception as e:
            print("скриншот в лог не попал:", e)
    if alive:
        process.kill()

text = log_path.read_text(encoding="utf-8", errors="replace")
print("----- лог игры (конец) -----")
print("\n".join(text.splitlines()[-80:]))
print("----------------------------")

checks = {
    "Fabric загрузил моды": "Loading" in text and "mods" in text,
    "клиент в списке модов": "pulseclient" in text,
    "клиент инициализирован": f"Pulse Client {release.version} " in text,
    "открылось главное меню Pulse": "Pulse главное меню активно" in text,
    "создан мир, включены все модули": "Pulse self-test: в мире" in text,
    "ClickGUI открылся, автотест пройден": "Pulse self-test OK" in text,
    "нет ошибок mixin": "Mixin apply failed" not in text and "InvalidInjectionException" not in text,
    "игра не упала": alive or process.returncode == 0,
}
for name, ok in checks.items():
    print(("OK   " if ok else "FAIL ") + name)
reached_menu = "Created:" in text and "atlas" in text
print(("OK   " if reached_menu else "INFO ") + "загрузились текстуры (дошли до меню)")
sys.exit(0 if all(checks.values()) else 1)
