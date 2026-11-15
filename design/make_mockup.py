# -*- coding: utf-8 -*-
"""Мокап главного экрана Vikunja Voice — философия «Тихий сигнал»."""
import math
import random
from PIL import Image, ImageDraw, ImageFont

W, H = 1760, 1500
BG_OUT = (5, 7, 11)
PHONE_TOP = (10, 14, 20)      # 0A0E14
PHONE_BOT = (16, 24, 40)      # 101828
CARD = (20, 27, 38)           # 141B26
BORDER = (27, 37, 52)         # 1B2534
VIOLET_HI = (139, 123, 255)   # 8B7BFF
VIOLET_LO = (91, 75, 224)     # 5B4BE0
TEXT_MAIN = (232, 236, 244)   # E8ECF4
TEXT_SUB = (122, 133, 153)    # 7A8599
TEXT_DIM = (74, 85, 104)      # 4A5568

F_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
F_LIGHT = "C:/Windows/Fonts/segoeuil.ttf"

canvas = Image.new("RGB", (W, H), BG_OUT)
draw = ImageDraw.Draw(canvas, "RGBA")


def vgrad(size, top, bot):
    """Вертикальный градиент."""
    w, h = size
    img = Image.new("RGB", (1, h))
    px = img.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
    return img.resize((w, h))


def circle_gradient(img, cx, cy, r, top, bot):
    """Круг с вертикальным градиентом."""
    grad = vgrad((2 * r, 2 * r), top, bot)
    mask = Image.new("L", (2 * r, 2 * r), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 2 * r - 1, 2 * r - 1), fill=255)
    img.paste(grad, (cx - r, cy - r), mask)


def spaced_text(draw_img, cx, y, text, font, tracking, fill):
    """Капители с разрядкой, по центру."""
    d = ImageDraw.Draw(draw_img)
    widths = []
    for ch in text:
        bb = d.textbbox((0, 0), ch, font=font)
        widths.append(bb[2] - bb[0] + tracking)
    total = sum(widths) - tracking
    x = cx - total / 2
    for ch, w in zip(text, widths):
        d.text((x, y), ch, font=font, fill=fill)
        x += w


def mic_glyph(img, cx, cy, size, color):
    """Иконка микрофона из примитивов: капсула + дуга + ножка."""
    d = ImageDraw.Draw(img, "RGBA")
    mw = int(size * 0.34)          # половина ширины капсулы
    mh = int(size * 0.42)          # полувысота капсулы
    top = cy - mh
    d.rounded_rectangle((cx - mw, top, cx + mw, cy + mh), radius=mw, fill=color)
    arc_r = int(size * 0.46)
    bb = (cx - arc_r, cy - int(size * 0.22), cx + arc_r, cy + int(size * 0.62))
    d.arc(bb, start=30, end=150, fill=color, width=int(size * 0.05))
    stem_h = int(size * 0.20)
    d.line((cx, cy + int(size * 0.30), cx, cy + int(size * 0.30) + stem_h),
           fill=color, width=int(size * 0.05))
    d.line((cx - int(size * 0.12), cy + int(size * 0.30) + stem_h,
            cx + int(size * 0.12), cy + int(size * 0.30) + stem_h),
           fill=color, width=int(size * 0.04))


def phone(x, y, w, h, listening):
    """Один телефон: тёмное поле, задача, кнопка, волна."""
    body = vgrad((w, h), PHONE_TOP, PHONE_BOT).convert("RGBA")
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=64, fill=255)
    canvas.paste(body, (x, y), mask)

    pd = ImageDraw.Draw(canvas, "RGBA")
    cx = x + w // 2

    # бренд
    pd.ellipse((cx - 160, y + 86, cx - 146, y + 100), fill=VIOLET_HI)
    spaced_text(canvas, cx + 12, y + 78, "VIKUNJA VOICE", ImageFont.truetype(F_LIGHT, 26), 8, TEXT_DIM)

    # карточка с задачей
    font_task = ImageFont.truetype(F_BOLD, 44)
    font_sub = ImageFont.truetype(F_LIGHT, 30)
    card_top, card_bot = y + 300, y + 560
    pd.rounded_rectangle((x + 56, card_top, x + w - 56, card_bot), radius=36,
                         fill=CARD, outline=BORDER, width=2)
    line1 = "Позвонить в налоговую"
    line2 = "послезавтра к 10:00"
    pd.text((cx, y + 360), line1, font=font_task, fill=TEXT_MAIN, anchor="mm")
    pd.text((cx, y + 430), line2, font=font_task, fill=VIOLET_HI, anchor="mm")
    status = "Слушаю…" if listening else "Готово · задача уйдёт в Vikunja"
    pd.text((cx, y + 500), status, font=font_sub, fill=TEXT_SUB, anchor="mm")

    # кнопка + кольца
    r = 170
    cy = y + 900
    if listening:
        random.seed(7)
        for k, alpha in ((0.35, 70), (0.65, 40), (0.95, 20)):
            rr = int(r * (1 + k))
            pd.ellipse((cx - rr, cy - rr, cx + rr, cy + rr),
                       outline=VIOLET_HI + (alpha,), width=3)
    circle_gradient(canvas, cx, cy, r, VIOLET_HI, VIOLET_LO)
    mic_glyph(canvas, cx, cy, 150, PHONE_TOP)

    # волна голоса (только в состоянии «слушаю»)
    if listening:
        random.seed(11)
        n, bw, gap = 26, 9, 9
        total = n * bw + (n - 1) * gap
        bx = cx - total // 2
        by = cy + r + 70
        for i in range(n):
            t = i / (n - 1)
            amp = (math.sin(t * math.pi) ** 0.7) * 46
            hgt = int(14 + amp + random.uniform(-8, 14))
            hgt = max(12, min(hgt, 92))
            pd.rounded_rectangle((bx, by - hgt / 2, bx + bw, by + hgt / 2),
                                 radius=5, fill=VIOLET_HI + (210,))
            bx += bw + gap

    # подпись под кнопкой
    hint = "говори — я слушаю" if listening else "Нажми и скажи задачу"
    pd.text((cx, y + 1210), hint, font=font_sub, fill=TEXT_SUB, anchor="mm")
    pd.text((cx, y + h - 56), "M1 · голос → текст", font=ImageFont.truetype(F_LIGHT, 22),
            fill=TEXT_DIM, anchor="mm")


phone(120, 70, 680, 1360, listening=False)
phone(960, 70, 680, 1360, listening=True)
canvas.save("C:/Users/User/.zcode/workspace/3ced86fb-6fa1-4ac9-95b1-ed35efedd95f/vikunja-voice/design/mockup_main.png")
print("saved")
