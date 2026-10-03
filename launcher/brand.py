"""Фирменный стиль: цвета и логотип (рисуется кодом, отдельные картинки не нужны)."""

from __future__ import annotations

from PIL import Image, ImageDraw

ACCENT = "#8A5CF6"
ACCENT_HOVER = "#7443F0"
ACCENT_DARK = "#5B33C9"
BG = "#0E0E13"
CARD = "#17171F"
CARD_BORDER = "#24242F"
FIELD = "#1F1F2A"
TEXT = "#F2F2F7"
MUTED = "#8B8B9C"
SUCCESS = "#7EE787"
ERROR = "#FF7B72"


def _hex(color: str) -> tuple[int, int, int]:
    color = color.lstrip("#")
    return tuple(int(color[i:i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def make_logo(size: int = 256) -> Image.Image:
    """Скруглённый квадрат с вертикальным градиентом и белой линией пульса."""
    scale = 4  # рисуем крупнее и уменьшаем — так края гладкие
    s = size * scale
    top, bottom = _hex("#A47BFF"), _hex(ACCENT_DARK)
    gradient = Image.new("RGBA", (s, s))
    draw = ImageDraw.Draw(gradient)
    for y in range(s):
        t = y / (s - 1)
        draw.line([(0, y), (s, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))

    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.24), fill=255)
    logo = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    logo.paste(gradient, (0, 0), mask)

    # Линия пульса (кардиограмма) — символ Pulse Client
    d = ImageDraw.Draw(logo)
    w = max(2, int(s * 0.075))
    mid = s * 0.54
    pts = [(s * 0.14, mid), (s * 0.34, mid), (s * 0.41, mid - s * 0.10), (s * 0.48, mid + s * 0.22),
           (s * 0.56, mid - s * 0.34), (s * 0.63, mid + s * 0.06), (s * 0.69, mid), (s * 0.86, mid)]
    d.line(pts, fill="white", width=w, joint="curve")
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill="white")
    return logo.resize((size, size), Image.LANCZOS)


def save_icon(path: str) -> None:
    make_logo(256).save(path, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])


if __name__ == "__main__":
    import sys

    save_icon(sys.argv[1] if len(sys.argv) > 1 else "icon.ico")
