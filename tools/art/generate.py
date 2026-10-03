"""Генератор фирменной графики Pulse Client: фоны главного меню и логотип.

Всё рисуется кодом (numpy + Pillow), поэтому картинки можно пересоздать или поменять палитру:
    python tools/art/generate.py
Результат кладётся в ресурсы мода (src/main/resources/assets/pulseclient/textures/gui/)
и в лаунчер (launcher/assets/).
"""

from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
MOD_OUT = ROOT / "src/main/resources/assets/pulseclient/textures/gui"
LAUNCHER_OUT = ROOT / "launcher/assets"
W, H = 1920, 1080


# ------------------------------------------------------------------ утилиты


def rgb(hex_color: str) -> np.ndarray:
    h = hex_color.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def lerp(a, b, t):
    return a + (b - a) * t


def vertical_gradient(stops: list[tuple[float, str]]) -> np.ndarray:
    """Небо: вертикальный градиент по опорным точкам (0..1, цвет)."""
    ys = np.linspace(0, 1, H, dtype=np.float32)
    out = np.zeros((H, 3), dtype=np.float32)
    for i in range(len(stops) - 1):
        (p0, c0), (p1, c1) = stops[i], stops[i + 1]
        mask = (ys >= p0) & (ys <= p1)
        t = ((ys[mask] - p0) / max(p1 - p0, 1e-6))[:, None]
        out[mask] = lerp(rgb(c0), rgb(c1), t)
    return np.repeat(out[:, None, :], W, axis=1)


def to_image(arr: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def screen(base: Image.Image, light: Image.Image) -> Image.Image:
    return ImageChops.screen(base, light)


def glow(layer: Image.Image, radii=(4, 14, 40, 90), strength=(1.0, 0.9, 0.7, 0.5)) -> Image.Image:
    """Неоновое свечение: несколько размытых копий слоя, сложенных вместе."""
    acc = np.asarray(layer, dtype=np.float32).copy()
    for r, s in zip(radii, strength):
        acc += np.asarray(layer.filter(ImageFilter.GaussianBlur(r)), dtype=np.float32) * s
    return to_image(acc)


def stars(img: Image.Image, rng: random.Random, count: int, max_y: float) -> Image.Image:
    layer = Image.new("RGB", img.size, 0)
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        x, y = rng.uniform(0, W), rng.uniform(0, H * max_y) ** 1.0
        b = rng.random() ** 3
        r = 0.6 + b * 1.8
        c = tuple(int(v) for v in lerp(rgb("#9F8CFF"), rgb("#FFFFFF"), rng.random()) * (0.35 + 0.65 * b))
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    return screen(img, glow(layer, radii=(2, 6), strength=(0.8, 0.4)))


def pulse_points(y0: float, amp: float, x0=0.0, x1=float(W), beats=(0.5,), seed=1) -> list[tuple[float, float]]:
    """Линия кардиограммы: ровная, с «ударом» (P-QRS-T) в заданных местах."""
    rng = random.Random(seed)
    pts = []
    n = 900
    for i in range(n + 1):
        x = lerp(x0, x1, i / n)
        t = (x - x0) / (x1 - x0)
        y = y0 + math.sin(t * 9) * amp * 0.03
        for c in beats:
            u = (t - c) * 28  # ширина удара
            # P-зубец, QRS-комплекс, T-зубец
            y -= amp * 0.12 * math.exp(-((u + 2.2) ** 2) * 3)
            y += amp * 0.25 * math.exp(-((u + 0.45) ** 2) * 40)
            y -= amp * 1.00 * math.exp(-((u - 0.0) ** 2) * 55)
            y += amp * 0.55 * math.exp(-((u - 0.5) ** 2) * 45)
            y -= amp * 0.22 * math.exp(-((u - 2.3) ** 2) * 2.5)
        pts.append((x, y + rng.uniform(-0.3, 0.3)))
    return pts


def draw_pulse(img: Image.Image, pts, color: str, width: int = 5, core="#FFFFFF") -> Image.Image:
    layer = Image.new("RGB", img.size, 0)
    d = ImageDraw.Draw(layer)
    d.line(pts, fill=tuple(int(v) for v in rgb(color)), width=width * 3, joint="curve")
    d.line(pts, fill=tuple(int(v) for v in lerp(rgb(color), rgb(core), 0.7)), width=width, joint="curve")
    return screen(img, glow(layer, radii=(3, 12, 36, 110, 220), strength=(1.0, 1.0, 0.8, 0.55, 0.35)))


def aurora(img: Image.Image, rng: random.Random, colors: list[str], y: float, height: float) -> Image.Image:
    """Мягкие полосы северного сияния."""
    layer = Image.new("RGB", img.size, 0)
    d = ImageDraw.Draw(layer)
    for i, col in enumerate(colors):
        phase, freq = rng.uniform(0, 6), rng.uniform(1.5, 3)
        pts_top, pts_bot = [], []
        for k in range(0, W + 40, 40):
            t = k / W
            cy = y + math.sin(t * freq * math.pi + phase) * height * 0.35 + i * height * 0.12
            pts_top.append((k, cy - height * 0.08))
            pts_bot.append((k, cy + height * 0.22))
        d.polygon(pts_top + pts_bot[::-1], fill=tuple(int(v * 0.55) for v in rgb(col)))
    return screen(img, layer.filter(ImageFilter.GaussianBlur(60)))


def voxel_ridge(img: Image.Image, rng: random.Random, *, block: int, base_y: float, amp: float, color: str,
                rim: str, fog: str, fog_amount: float, roughness: float = 1.0, seed_shift: float = 0) -> Image.Image:
    """Горный хребет из кубов: высоты квантуются по сетке блоков, у каждого куба своя тень и подсветка сверху."""
    d = ImageDraw.Draw(img)
    base_c = lerp(rgb(color), rgb(fog), fog_amount)
    rim_c = lerp(rgb(rim), rgb(fog), fog_amount * 0.8)
    cols = W // block + 2
    phases = [rng.uniform(0, 100) for _ in range(5)]
    raw = []
    for cx in range(cols):
        t = cx / cols
        raw.append(abs(math.sin(t * 2.3 + phases[0] + seed_shift)) * 0.9 + math.sin(t * 6.1 + phases[1]) * 0.35 * roughness
                   + math.sin(t * 13.0 + phases[2]) * 0.18 * roughness + math.sin(t * 29.0 + phases[3]) * 0.06
                   + rng.uniform(-0.03, 0.03))
    lo, hi = min(raw), max(raw)
    for cx in range(cols):
        h = (raw[cx] - lo) / (hi - lo)
        top = base_y - h * amp
        top = math.floor(top / block) * block
        x = cx * block
        for yb in range(int(top), H + block, block):
            depth = (yb - top) / max(H - top, 1)
            shade = 1.0 - 0.45 * depth + rng.uniform(-0.05, 0.05)
            c = base_c * shade
            if yb == int(top):
                c = lerp(c, rim_c, 0.85)  # верхний блок подсвечен
            d.rectangle([x, yb, x + block - 1, yb + block - 1], fill=tuple(int(max(0, min(255, v))) for v in c))
        # тонкая светлая кромка сверху куба
        d.rectangle([x, int(top), x + block - 1, int(top) + max(1, block // 8)],
                    fill=tuple(int(min(255, v)) for v in lerp(rim_c, rgb("#FFFFFF"), 0.25)))
    return img


def fog_band(img: Image.Image, y: float, height: float, color: str, alpha: float) -> Image.Image:
    arr = np.asarray(img, dtype=np.float32)
    ys = np.arange(H, dtype=np.float32)
    a = np.exp(-((ys - y) / height) ** 2)[:, None, None] * alpha
    return to_image(arr * (1 - a) + rgb(color) * a)


def floating_island(img: Image.Image, rng: random.Random, cx: float, cy: float, block: int, width_blocks: int,
                    top: str, body: str, crystal: str | None) -> Image.Image:
    """Летающий остров: перевёрнутая «пирамида» из кубов, сверху трава, снизу свисают блоки."""
    d = ImageDraw.Draw(img)
    rows = width_blocks // 2 + 1
    for r in range(rows):
        w = width_blocks - r * 2 + rng.choice([0, 0, 1])
        if w <= 0:
            break
        x0 = cx - w * block / 2
        for k in range(w):
            shade = 1.0 - r * 0.12 + rng.uniform(-0.06, 0.06)
            c = (rgb(top) if r == 0 else rgb(body)) * shade
            x = x0 + k * block
            y = cy + r * block
            d.rectangle([x, y, x + block - 1, y + block - 1], fill=tuple(int(max(0, min(255, v))) for v in c))
    if crystal:
        layer = Image.new("RGB", img.size, 0)
        dl = ImageDraw.Draw(layer)
        for _ in range(rng.randint(1, 3)):
            x = cx + rng.uniform(-width_blocks * block * 0.35, width_blocks * block * 0.35)
            hgt = block * rng.uniform(1.2, 2.6)
            wd = block * 0.35
            dl.polygon([(x, cy - hgt), (x + wd, cy - hgt * 0.35), (x, cy), (x - wd, cy - hgt * 0.35)],
                       fill=tuple(int(v) for v in rgb(crystal)))
        img = screen(img, glow(layer, radii=(2, 10, 30), strength=(1.0, 0.8, 0.5)))
    return img


def particles(img: Image.Image, rng: random.Random, count: int, color: str, y_min=0.3) -> Image.Image:
    layer = Image.new("RGB", img.size, 0)
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        x, y = rng.uniform(0, W), rng.uniform(H * y_min, H)
        s = rng.uniform(2, 6)
        c = tuple(int(v * rng.uniform(0.4, 1.0)) for v in rgb(color))
        d.rectangle([x, y, x + s, y + s], fill=c)
    return screen(img, glow(layer, radii=(3, 10), strength=(0.9, 0.5)))


def vignette(img: Image.Image, strength: float = 0.55) -> Image.Image:
    arr = np.asarray(img, dtype=np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    k = 1 - strength * np.clip(r - 0.55, 0, 1) ** 1.6
    return to_image(arr * k[:, :, None])


def film_grain(img: Image.Image, seed: int, amount: float = 5.0) -> Image.Image:
    noise = np.random.default_rng(seed).normal(0, amount, (H, W, 1)).astype(np.float32)
    return to_image(np.asarray(img, dtype=np.float32) + noise)


# ------------------------------------------------------------------ сцены


def scene_pulse_night() -> Image.Image:
    """Ночь над воксельными горами, в небе — неоновая линия пульса."""
    rng = random.Random(7)
    img = to_image(vertical_gradient([(0, "#06051A"), (0.45, "#1B0F4A"), (0.75, "#4A1C7A"), (1, "#12082A")]))
    img = stars(img, rng, 900, 0.7)
    img = aurora(img, rng, ["#5B2BD9", "#B23CF0", "#3C6BFF"], H * 0.22, H * 0.5)
    img = draw_pulse(img, pulse_points(H * 0.38, H * 0.22, beats=(0.5,), seed=3), "#B66BFF", width=5)
    img = fog_band(img, H * 0.62, H * 0.12, "#7A3BD6", 0.35)
    img = floating_island(img, rng, W * 0.18, H * 0.30, 22, 9, "#7E5BD8", "#2C1A55", "#E2B8FF")
    img = floating_island(img, rng, W * 0.83, H * 0.22, 18, 7, "#6F4FCF", "#25164A", "#B9F1FF")
    img = voxel_ridge(img, rng, block=24, base_y=H * 0.78, amp=H * 0.34, color="#1E0F45", rim="#9C6BFF",
                      fog="#4A2390", fog_amount=0.45)
    img = fog_band(img, H * 0.74, H * 0.08, "#8E4DE8", 0.30)
    img = voxel_ridge(img, rng, block=36, base_y=H * 0.98, amp=H * 0.30, color="#0E0624", rim="#C084FC",
                      fog="#2A1260", fog_amount=0.15, seed_shift=2)
    img = particles(img, rng, 60, "#D6A8FF", y_min=0.05)
    return film_grain(vignette(img), 1)


def scene_neon_sunset() -> Image.Image:
    """Синтвейв-закат: огромное солнце в полосах, сетка-пол и кубические горы."""
    rng = random.Random(21)
    img = to_image(vertical_gradient([(0, "#0B0420"), (0.35, "#3A0D5E"), (0.58, "#B42A8A"), (0.62, "#2A0838"),
                                      (1, "#07020F")]))
    img = stars(img, rng, 500, 0.45)
    # солнце
    sun = Image.new("RGB", img.size, 0)
    ds = ImageDraw.Draw(sun)
    cx, cy, r = W / 2, H * 0.47, H * 0.24
    for y in range(int(cy - r), int(cy + r)):
        t = (y - (cy - r)) / (2 * r)
        if t > 0.5 and int((t - 0.5) * 40) % 3 == 2:
            continue  # полосы в нижней половине солнца
        half = math.sqrt(max(0.0, r * r - (y - cy) ** 2))
        c = lerp(rgb("#FFE36E"), rgb("#FF3DAE"), t)
        ds.line([(cx - half, y), (cx + half, y)], fill=tuple(int(v) for v in c))
    img = screen(img, glow(sun, radii=(6, 40, 120), strength=(0.25, 0.3, 0.3)))
    img = voxel_ridge(img, rng, block=20, base_y=H * 0.62, amp=H * 0.22, color="#22083A", rim="#FF5BC8",
                      fog="#5A1667", fog_amount=0.25)
    # пол-сетка в перспективе
    floor = Image.new("RGB", img.size, 0)
    df = ImageDraw.Draw(floor)
    horizon = H * 0.62
    ImageDraw.Draw(img).rectangle([0, horizon, W, H], fill=(10, 3, 22))
    for i in range(-30, 31):
        df.line([(W / 2 + i * 22, horizon), (W / 2 + i * 260, H)], fill=(255, 60, 200), width=2)
    for k in range(1, 22):
        y = horizon + (H - horizon) * (k / 21) ** 2.2
        df.line([(0, y), (W, y)], fill=(255, 60, 200), width=2)
    img = screen(img, glow(floor, radii=(2, 8, 24), strength=(0.8, 0.5, 0.3)))
    img = draw_pulse(img, pulse_points(H * 0.62, H * 0.18, beats=(0.24, 0.76), seed=5), "#FF6BD6", width=4)
    img = particles(img, rng, 90, "#FFB3EC", y_min=0.1)
    return film_grain(vignette(img, 0.6), 2)


def scene_crystal_void() -> Image.Image:
    """Пустота с летающими островами и огромным пульсирующим кольцом-порталом."""
    rng = random.Random(42)
    img = to_image(vertical_gradient([(0, "#03030C"), (0.5, "#0C0A2A"), (1, "#05030F")]))
    img = stars(img, rng, 1400, 1.0)
    # туманность
    neb = Image.new("RGB", img.size, 0)
    dn = ImageDraw.Draw(neb)
    for _ in range(26):
        x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(80, 320)
        col = rng.choice(["#3B1D8F", "#6A1FA0", "#1D3F9E", "#8A2BE2"])
        dn.ellipse([x - r, y - r, x + r, y + r], fill=tuple(int(v * 0.5) for v in rgb(col)))
    img = screen(img, neb.filter(ImageFilter.GaussianBlur(90)))
    # кольцо-портал
    ring = Image.new("RGB", img.size, 0)
    dr = ImageDraw.Draw(ring)
    cx, cy, r = W * 0.5, H * 0.45, H * 0.27
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(170, 110, 255), width=10)
    dr.ellipse([cx - r * 0.8, cy - r * 0.8, cx + r * 0.8, cy + r * 0.8], outline=(90, 60, 200), width=4)
    img = screen(img, glow(ring, radii=(4, 18, 60, 160), strength=(1.0, 0.9, 0.6, 0.4)))
    img = draw_pulse(img, pulse_points(cy, r * 0.9, x0=cx - r * 1.6, x1=cx + r * 1.6, beats=(0.5,), seed=9),
                     "#C9A2FF", width=4)
    for (x, y, b, w, crystal) in [(0.14, 0.62, 26, 9, "#9BE7FF"), (0.86, 0.66, 24, 8, "#E0B0FF"),
                                  (0.30, 0.86, 30, 7, "#B0A0FF"), (0.70, 0.18, 16, 6, "#FFC6F5"),
                                  (0.08, 0.20, 14, 5, None), (0.95, 0.38, 12, 4, None)]:
        img = floating_island(img, rng, W * x, H * y, b, w, "#5F4BB0", "#1F1640", crystal)
    img = particles(img, rng, 220, "#B6A4FF", y_min=0.0)
    return film_grain(vignette(img, 0.65), 3)


# ------------------------------------------------------------------ логотип

GLYPHS = {
    "P": ["1111.", "1...1", "1...1", "1111.", "1....", "1....", "1...."],
    "U": ["1...1", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."],
    "L": ["1....", "1....", "1....", "1....", "1....", "1....", "11111"],
    "S": [".1111", "1....", "1....", ".111.", "....1", "....1", "1111."],
    "E": ["11111", "1....", "1....", "1111.", "1....", "1....", "11111"],
    "C": [".1111", "1....", "1....", "1....", "1....", "1....", ".1111"],
    "I": ["111", ".1.", ".1.", ".1.", ".1.", ".1.", "111"],
    "N": ["1...1", "11..1", "1.1.1", "1..11", "1...1", "1...1", "1...1"],
    "T": ["11111", "..1..", "..1..", "..1..", "..1..", "..1..", "..1.."],
}


def block_text(text: str, cell: int, top: str, bottom: str, depth: int) -> Image.Image:
    """Надпись из объёмных кубиков, как у логотипа Minecraft, но в фирменных цветах."""
    gap = cell
    width = sum(len(GLYPHS[c][0]) * cell + gap for c in text) - gap + depth
    height = 7 * cell + depth
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = 0
    for ch in text:
        g = GLYPHS[ch]
        for row, line in enumerate(g):
            for col, v in enumerate(line):
                if v != "1":
                    continue
                px, py = x + col * cell, row * cell
                t = row / 6
                face = lerp(rgb(top), rgb(bottom), t)
                side = face * 0.45
                # «толщина» куба вниз-вправо
                d.polygon([(px + cell, py), (px + cell + depth, py + depth), (px + cell + depth, py + cell + depth),
                           (px + cell, py + cell)], fill=tuple(int(v) for v in side) + (255,))
                d.polygon([(px, py + cell), (px + cell, py + cell), (px + cell + depth, py + cell + depth),
                           (px + depth, py + cell + depth)], fill=tuple(int(v * 0.7) for v in side) + (255,))
                d.rectangle([px, py, px + cell - 1, py + cell - 1], fill=tuple(int(v) for v in face) + (255,))
                hl = lerp(face, rgb("#FFFFFF"), 0.35)
                d.rectangle([px, py, px + cell - 1, py + max(1, cell // 6)], fill=tuple(int(v) for v in hl) + (255,))
        x += len(g[0]) * cell + gap
    return img


def make_title_logo() -> Image.Image:
    """Логотип для главного меню: PULSE крупно, CLIENT мельче, с фиолетовым свечением."""
    big = block_text("PULSE", 24, "#E9D5FF", "#8B5CF6", 8)
    small = block_text("CLIENT", 10, "#FFFFFF", "#C4B5FD", 4)
    pad = 40
    w = max(big.width, small.width) + pad * 2
    h = big.height + small.height + 24 + pad * 2
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas.alpha_composite(big, ((w - big.width) // 2, pad))
    canvas.alpha_composite(small, ((w - small.width) // 2, pad + big.height + 24))
    # свечение под буквами
    alpha = canvas.split()[3].filter(ImageFilter.GaussianBlur(18))
    glow_layer = Image.new("RGBA", canvas.size, (150, 90, 255, 0))
    glow_layer.putalpha(alpha.point(lambda a: int(a * 0.7)))
    out = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    out.alpha_composite(glow_layer)
    out.alpha_composite(canvas)
    return out


def main() -> None:
    MOD_OUT.mkdir(parents=True, exist_ok=True)
    LAUNCHER_OUT.mkdir(parents=True, exist_ok=True)
    scenes = {"background_1": scene_pulse_night, "background_2": scene_neon_sunset, "background_3": scene_crystal_void}
    for name, fn in scenes.items():
        img = fn()
        img.save(MOD_OUT / f"{name}.png", optimize=True)
        img.resize((960, 540), Image.LANCZOS).save(LAUNCHER_OUT / f"{name}.jpg", quality=88)
        print("готово:", name)
    # Иконки окна игры и мода — тот же значок, что у лаунчера
    import sys
    sys.path.insert(0, str(ROOT / "launcher"))
    import brand
    icons = ROOT / "src/main/resources/assets/pulseclient/icons"
    icons.mkdir(parents=True, exist_ok=True)
    for size in (16, 32, 48, 64, 128):
        brand.make_logo(size).save(icons / f"icon_{size}.png", optimize=True)
    logo = make_title_logo()
    logo.save(MOD_OUT / "logo.png", optimize=True)
    logo.save(LAUNCHER_OUT / "logo.png", optimize=True)
    print("логотип:", logo.size)


if __name__ == "__main__":
    main()
