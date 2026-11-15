# -*- coding: utf-8 -*-
"""Иконка приложения Vikunja Voice: фиолетовый градиент + говорящая мордочка.

Генерирует PNG-иконки для всех плотностей (mipmap-*) и превью в design/.
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")

VIOLET_HI = (139, 123, 255)   # #8B7BFF
VIOLET_LO = (91, 75, 224)     # #5B4BE0
INK = (10, 14, 20, 255)       # #0A0E14

S = 2048                      # мастер-размер (сглаживание при уменьшении)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def vertical_gradient(size, top, bottom):
    grad = Image.new("RGB", (1, size))
    px = grad.load()
    for y in range(size):
        t = y / max(size - 1, 1)
        px[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return grad.resize((size, size))


def draw_face(draw, cx, cy, scale):
    """Глаза + приоткрытый рот; мордочка ~51% ширины иконки."""
    eye_r = 0.055 * scale
    for dx in (-0.20, 0.20):
        ex = cx + scale * dx
        ey = cy - 0.16 * scale
        draw.ellipse((ex - eye_r, ey - eye_r, ex + eye_r, ey + eye_r), fill=INK)
    mouth_w = 0.22 * scale
    mouth_h = 0.26 * scale
    top = cy + 0.03 * scale
    draw.rounded_rectangle(
        (cx - mouth_w / 2, top, cx + mouth_w / 2, top + mouth_h),
        radius=mouth_w / 2,
        fill=INK,
    )


def make_icon(shape):
    """shape: 'squircle' (лаунчер замаскирует сам) или 'circle'."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    if shape == "circle":
        md.ellipse((0, 0, S - 1, S - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=255)
    img.paste(vertical_gradient(S, VIOLET_HI, VIOLET_LO), (0, 0), mask)

    # мордочка ~51% ширины, оптически по центру
    draw_face(ImageDraw.Draw(img), S / 2, S / 2 - S * 0.0375, S)
    return img


def main():
    squircle = make_icon("squircle")
    round_icon = make_icon("circle")

    for dpi, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(folder, exist_ok=True)
        squircle.resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher.png")
        )
        round_icon.resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_round.png")
        )

    squircle.resize((512, 512), Image.LANCZOS).save(
        os.path.join(HERE, "app_icon.png")
    )
    print("icons written")


if __name__ == "__main__":
    main()
