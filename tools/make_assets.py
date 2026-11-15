#!/usr/bin/env python3
"""
Генерирует текстуры и картинки для игры из фото и скриншотов, лежащих в корне репозитория.

    python3 tools/make_assets.py

Нужны: Pillow, numpy, opencv-python.
Результат попадает в app/src/main/assets/{tex,ui} и app/src/main/res/mipmap-*.
Все игровые текстуры имеют размеры степени двойки (требование OpenGL ES 2.0 для mipmap/REPEAT).
"""
import math
import random
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
RES = ROOT / "app/src/main/res"
TEX = ASSETS / "tex"
UI = ASSETS / "ui"
for d in (TEX, UI):
    d.mkdir(parents=True, exist_ok=True)

HERO_PHOTO = "IMG_20260629_154954.jpg"     # кудрявый герой (помада)
ENEMY_PHOTO = "IMG_20261003_171056.jpg"    # кудрявый враг с сумкой
SHOT_HOOP = "Screenshot_20261113_214326_ru_yandex_yandexmaps_MapActivity.jpg"
SHOT_FACADE = "Screenshot_20261113_214333_ru_yandex_yandexmaps_MapActivity.jpg"
SHOT_GOAL = "Screenshot_20261113_214338_ru_yandex_yandexmaps_MapActivity.jpg"
SHOT_PLAY = "Screenshot_20261113_214342_ru_yandex_yandexmaps_MapActivity.jpg"

SHOT_K = 2412 / 1280.0  # скриншоты: «миниатюрные» координаты -> реальные


def load(name):
    return ImageOps.exif_transpose(Image.open(ROOT / name)).convert("RGB")


def shot_box(img, x0, y0, x1, y1):
    return img.crop((int(x0 * SHOT_K), int(y0 * SHOT_K), int(x1 * SHOT_K), int(y1 * SHOT_K)))


def save_jpg(img, path, q=90):
    img.convert("RGB").save(path, quality=q, optimize=True)


# ---------------------------------------------------------------- маски и RGBA

def egg_mask(size, cx, cy, rx, ry_top, ry_bot, feather=0.05, power=2.4):
    """Мягкая «яйцевидная» маска головы (значения 0..1)."""
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx = (xx / w - cx) / rx
    ry = np.where(yy / h < cy, ry_top, ry_bot)
    ny = (yy / h - cy) / ry
    d = (np.abs(nx) ** power + np.abs(ny) ** power) ** (1.0 / power)
    return np.clip((1.0 - d) / feather, 0, 1).astype(np.float32)


def bleed(rgb, alpha, iters=10):
    """Растягивает цвет в прозрачные пиксели, чтобы фильтрация не давала цветной кайм."""
    rgb = rgb.astype(np.float32).copy()
    m = alpha > 0.5
    k = np.ones((3, 3), np.float32)
    for _ in range(iters):
        num = cv2.filter2D(rgb * m[..., None], -1, k)
        den = cv2.filter2D(m.astype(np.float32), -1, k)
        fill = (den > 0) & (~m)
        rgb[fill] = num[fill] / den[fill][:, None]
        m = m | fill
    return rgb


def to_rgba(rgb, alpha):
    rgb = bleed(rgb, alpha)
    out = np.dstack([np.clip(rgb, 0, 255).astype(np.uint8), (np.clip(alpha, 0, 1) * 255).astype(np.uint8)])
    return Image.fromarray(out, "RGBA")


def plate(src, box, size, mask_args, grade=None):
    im = src.crop(box).resize((size, size), Image.LANCZOS)
    if grade:
        im = grade(im)
    a = egg_mask((size, size), *mask_args)
    return to_rgba(np.asarray(im), a)


# ---------------------------------------------------------------- лица героя и врага

def make_characters():
    hero = load(HERO_PHOTO)
    enemy = load(ENEMY_PHOTO)

    # Герой: лицо + кудри (помада на губах видна).
    p = plate(hero, (640, 900, 1740, 2060), 256, (0.50, 0.47, 0.47, 0.45, 0.50, 0.05))
    p.save(TEX / "hero_face.png")

    # волосы героя (на коробку-голову) и «задняя» пластина для вида сзади.
    # Ночью они выглядели серо-белым шаром, поэтому подтягиваем насыщенность и контраст (золотистый блонд).
    def hair_grade(im):
        im = ImageEnhance.Color(im).enhance(1.9)
        im = ImageEnhance.Contrast(im).enhance(1.25)
        return ImageEnhance.Brightness(im).enhance(0.8)

    hair = hair_grade(hero.crop((890, 960, 1540, 1300)))
    save_jpg(hair.resize((128, 128), Image.LANCZOS), TEX / "hero_hair.jpg")
    hb = hair.resize((256, 256), Image.LANCZOS)
    a = egg_mask((256, 256), 0.5, 0.5, 0.47, 0.47, 0.47, feather=0.06)
    to_rgba(np.asarray(hb), a).save(TEX / "hero_back.png")

    # Враг: лицо с открытым ртом (сильно кропнутое, чуть мутное — это даже хорошо для хоррора)
    def enemy_grade(im):
        im = ImageOps.autocontrast(im, cutoff=1)
        return im.filter(ImageFilter.UnsharpMask(radius=2, percent=80, threshold=2))

    p = plate(enemy, (720, 900, 1140, 1320), 256, (0.50, 0.52, 0.41, 0.40, 0.44, 0.06), enemy_grade)
    p.save(TEX / "enemy_face.png")

    # боковая прядь кудрей врага -> текстура волос (тёмная, густая)
    ehair = enemy.crop((815, 1090, 880, 1250)).resize((128, 128), Image.LANCZOS)
    ehair = ImageOps.autocontrast(ehair, cutoff=1).point(lambda v: int(v * 0.8))
    save_jpg(ehair, TEX / "enemy_hair.jpg")
    eb = ehair.resize((256, 256), Image.LANCZOS)
    a = egg_mask((256, 256), 0.5, 0.5, 0.47, 0.47, 0.47, feather=0.06)
    to_rgba(np.asarray(eb), a).save(TEX / "enemy_back.png")

    # --- UI: портрет героя (круг) и картинка скримера
    sq = hero.crop((640, 880, 1740, 1980)).resize((256, 256), Image.LANCZOS)
    yy, xx = np.mgrid[0:256, 0:256].astype(np.float32)
    r = np.sqrt((xx - 127.5) ** 2 + (yy - 127.5) ** 2) / 127.5
    a = np.clip((1.0 - r) / 0.03, 0, 1)
    to_rgba(np.asarray(sq), a).save(UI / "ui_hero.png")

    scare = enemy.crop((100, 880, 2050, 3000)).resize((780, 848), Image.LANCZOS)
    save_jpg(scare, UI / "ui_scare.jpg", 88)

    # маленькая иконка врага для HUD (лицо)
    ef = enemy.crop((720, 900, 1140, 1320)).resize((128, 128), Image.LANCZOS)
    yy, xx = np.mgrid[0:128, 0:128].astype(np.float32)
    r = np.sqrt((xx - 63.5) ** 2 + (yy - 63.5) ** 2) / 63.5
    to_rgba(np.asarray(ef), np.clip((1.0 - r) / 0.05, 0, 1)).save(UI / "ui_enemy.png")

    return hero


# ---------------------------------------------------------------- фасады, песок

def flat_field(arr, sigma=40):
    """Убирает тени/градиент освещения из текстуры."""
    arr = arr.astype(np.float32)
    blur = cv2.GaussianBlur(arr, (0, 0), sigma)
    mean = arr.reshape(-1, 3).mean(0)
    return np.clip(arr / np.maximum(blur, 1.0) * mean, 0, 255)


def make_world_photos():
    s_hoop = load(SHOT_HOOP)
    s_fac = load(SHOT_FACADE)
    s_goal = load(SHOT_GOAL)
    s_play = load(SHOT_PLAY)

    # песок: чистый квадрат справа внизу первого скриншота, выравниваем освещение, зеркально тайлим
    raw = s_hoop.crop((800, 661, 1130, 992))
    arr = flat_field(np.asarray(raw), 35)
    t = Image.fromarray(arr.astype(np.uint8)).resize((256, 256), Image.LANCZOS)
    big = Image.new("RGB", (512, 512))
    big.paste(t, (0, 0))
    big.paste(ImageOps.mirror(t), (256, 0))
    big.paste(ImageOps.flip(t), (0, 256))
    big.paste(ImageOps.flip(ImageOps.mirror(t)), (256, 256))
    save_jpg(big, TEX / "sand.jpg", 90)

    # фасады
    a = shot_box(s_fac, 740, 45, 960, 300)
    a = a.crop((0, 0, a.width, 390)).resize((512, 512), Image.LANCZOS)
    save_jpg(a, TEX / "facade_a.jpg", 88)

    c = shot_box(s_play, 590, 60, 960, 300)
    c = c.crop((270, 0, c.width, 330)).resize((512, 512), Image.LANCZOS)
    save_jpg(c, TEX / "facade_c.jpg", 88)

    b = shot_box(s_goal, 1030, 140, 1262, 300).resize((512, 256), Image.LANCZOS)
    save_jpg(b, TEX / "facade_b.jpg", 88)

    # фон меню: двор, убираем маркер «6», ночная цветокоррекция
    bg = shot_box(s_goal, 135, 50, 1115, 420).resize((1280, 483), Image.LANCZOS)
    arr = cv2.cvtColor(np.asarray(bg), cv2.COLOR_RGB2BGR)
    mask = np.zeros(arr.shape[:2], np.uint8)
    cv2.circle(mask, (1075, 253), 46, 255, -1)
    arr = cv2.inpaint(arr, mask, 5, cv2.INPAINT_TELEA)
    bg = Image.fromarray(cv2.cvtColor(arr, cv2.COLOR_BGR2RGB)).crop((0, 0, 1280, 386))
    bg = bg.filter(ImageFilter.GaussianBlur(1.4))
    f = np.asarray(bg).astype(np.float32) / 255.0
    f = f * np.array([0.20, 0.27, 0.46]) + np.array([0.01, 0.015, 0.04])
    h, w = f.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    v = 1.0 - 0.75 * (((xx / w - 0.5) * 1.5) ** 2 + ((yy / h - 0.5) * 1.6) ** 2)
    f = f * np.clip(v, 0.05, 1)[..., None]
    save_jpg(Image.fromarray((np.clip(f, 0, 1) * 255).astype(np.uint8)), UI / "ui_bg.jpg", 85)


# ---------------------------------------------------------------- процедурные текстуры

def make_fence():
    S = 128
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    col = (38, 92, 58, 255)
    step = S // 5
    for i in range(5):
        p = i * step + 1
        d.rectangle([p, 0, p + 2, S], fill=col)
        d.rectangle([0, p, S, p + 2], fill=col)
    im.save(TEX / "fence.png")


def make_grass():
    rnd = np.random.RandomState(7)
    S = 256
    base = np.zeros((S, S, 3), np.float32)
    base[:] = (44, 74, 32)
    n = cv2.GaussianBlur(rnd.randn(S, S).astype(np.float32), (0, 0), 3) * 40
    base += n[..., None] * np.array([0.5, 0.9, 0.3])
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    r = random.Random(3)
    for _ in range(2600):
        x, y = r.randrange(S), r.randrange(S)
        L = r.randint(3, 8)
        g = r.randint(60, 120)
        d.line([(x, y), (x + r.randint(-2, 2), y - L)], fill=(g // 2, g, g // 3), width=1)
    save_jpg(img, TEX / "grass.jpg", 90)


def make_detail():
    rnd = np.random.RandomState(11)
    n = rnd.randn(128, 128).astype(np.float32)
    n = cv2.GaussianBlur(n, (0, 0), 1.0) * 0.5 + cv2.GaussianBlur(rnd.randn(128, 128).astype(np.float32), (0, 0), 6) * 2.5
    n = 0.82 + n * 0.08
    g = (np.clip(n, 0.55, 1.0) * 255).astype(np.uint8)
    save_jpg(Image.fromarray(np.dstack([g, g, g])), TEX / "detail.jpg", 92)
    Image.new("RGB", (8, 8), (255, 255, 255)).save(TEX / "white.png")


def make_puffer():
    S = 128
    img = Image.new("RGB", (S, S), (26, 26, 33))
    d = ImageDraw.Draw(img)
    for y in range(0, S, 32):
        d.rectangle([0, y, S, y + 2], fill=(10, 10, 14))
        d.rectangle([0, y + 3, S, y + 6], fill=(48, 48, 58))
    arr = np.asarray(img).astype(np.float32)
    arr += np.random.RandomState(5).randn(S, S, 1).astype(np.float32) * 3
    save_jpg(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)), TEX / "puffer.jpg", 90)


def make_tree():
    W, H, ss = 512, 1024, 2
    rnd = random.Random(21)
    im = Image.new("RGBA", (W * ss, H * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    def branch(x, y, ang, length, width, depth):
        if depth == 0 or length < 7 * ss:
            return
        x2 = x + math.sin(ang) * length
        y2 = y - math.cos(ang) * length
        col = (214, 214, 204, 255) if width > 6 * ss else (66, 58, 50, 255)
        d.line([(x, y), (x2, y2)], fill=col, width=max(1, int(width)))
        if width > 6 * ss:
            for _ in range(int(length / (26 * ss)) + 1):
                t = rnd.random()
                mx, my = x + (x2 - x) * t, y + (y2 - y) * t
                d.line([(mx - width * 0.35, my), (mx + width * 0.35, my + 2 * ss)], fill=(30, 30, 30, 255), width=max(1, int(3 * ss)))
        n = 2 if depth > 3 else rnd.choice([2, 3])
        for i in range(n):
            na = ang + rnd.uniform(-0.75, 0.75) + (i - (n - 1) / 2) * 0.25
            branch(x2, y2, na, length * rnd.uniform(0.60, 0.78), width * 0.68, depth - 1)

    cx, base = W * ss // 2, H * ss - 2
    # ствол
    seg = 0
    x, y = cx, base
    for k in range(5):
        ang = rnd.uniform(-0.06, 0.06)
        L = 130 * ss
        wdt = (30 - k * 4) * ss
        x2, y2 = x + math.sin(ang) * L, y - math.cos(ang) * L
        d.line([(x, y), (x2, y2)], fill=(214, 214, 204, 255), width=int(wdt))
        for _ in range(4):
            t = rnd.random()
            mx, my = x + (x2 - x) * t, y + (y2 - y) * t
            d.line([(mx - wdt * 0.4, my), (mx + wdt * 0.4, my + 3 * ss)], fill=(25, 25, 25, 255), width=int(4 * ss))
        # боковые ветви
        if k >= 2:
            for side in (-1, 1):
                branch(x2, y2, side * rnd.uniform(0.5, 1.0), 120 * ss * rnd.uniform(0.7, 1.0), wdt * 0.5, 5)
        x, y = x2, y2
    branch(x, y, 0.0, 110 * ss, 12 * ss, 5)
    im = im.resize((W, H), Image.LANCZOS)
    arr = np.asarray(im).astype(np.float32)
    a = arr[..., 3] / 255.0
    arr[..., :3] = bleed(arr[..., :3], a)
    Image.fromarray(arr.astype(np.uint8), "RGBA").save(TEX / "tree.png")


def make_glow():
    S = 128
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    r = np.sqrt((xx - S / 2 + 0.5) ** 2 + (yy - S / 2 + 0.5) ** 2) / (S / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2
    img = np.dstack([np.full((S, S), 255, np.uint8)] * 3 + [(a * 255).astype(np.uint8)])
    Image.fromarray(img, "RGBA").save(TEX / "glow.png")

    # луч ключа: вертикальный мягкий столб
    W, H = 64, 256
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx = (xx - W / 2 + 0.5) / (W / 2)
    fy = yy / H
    a = np.clip(1 - np.abs(cx), 0, 1) ** 1.6 * np.clip(fy, 0, 1) ** 1.2
    img = np.dstack([np.full((H, W), 255, np.uint8)] * 3 + [(a * 255).astype(np.uint8)])
    Image.fromarray(img, "RGBA").save(TEX / "beam.png")


# ---------------------------------------------------------------- иконка приложения

def make_icons(hero):
    face = hero.crop((640, 880, 1740, 1980)).resize((512, 512), Image.LANCZOS)
    S = 512
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    bg = np.zeros((S, S, 3), np.float32)
    rr = np.sqrt((xx - S / 2) ** 2 + (yy - S / 2) ** 2) / (S / 2)
    bg[..., 0] = 120 * np.clip(1 - rr, 0, 1) + 14
    bg[..., 1] = 8
    bg[..., 2] = 18
    base = Image.fromarray(bg.astype(np.uint8))
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse([40, 40, S - 40, S - 40], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(2))
    base.paste(face.resize((S - 80, S - 80), Image.LANCZOS), (40, 40), m.crop((40, 40, S - 40, S - 40)))
    ring = ImageDraw.Draw(base)
    ring.ellipse([36, 36, S - 36, S - 36], outline=(190, 20, 30), width=14)
    # скругление углов
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=110, fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    for name, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        out.resize((px, px), Image.LANCZOS).save(d / "ic_launcher.png")


if __name__ == "__main__":
    hero_img = make_characters()
    make_world_photos()
    make_fence()
    make_grass()
    make_detail()
    make_puffer()
    make_tree()
    make_glow()
    make_icons(hero_img)
    print("OK")
