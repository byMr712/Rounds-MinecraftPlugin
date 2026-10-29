#!/usr/bin/env python3
"""
Automated card texture generator for Rounds Minecraft Plugin.
Reads YAML card definitions (ru/en names and descriptions) and bakes
formatted text directly onto semantically matching card face textures.
"""

import os
import re
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("Pillow is required. Run: pip install Pillow")
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
CARDS_DIR = ROOT / "rounds-core" / "src" / "main" / "resources" / "cards"
SRC_IMAGES_DIR = ROOT / "test-servers" / "original_mod" / "cards"
RU_OUT_DIR = SRC_IMAGES_DIR / "ru"
EN_OUT_DIR = SRC_IMAGES_DIR / "en"

RU_OUT_DIR.mkdir(parents=True, exist_ok=True)
EN_OUT_DIR.mkdir(parents=True, exist_ok=True)

ID_RE = re.compile(r"^\s*id\s*:\s*(\d+)", re.MULTILINE)
NAME_RU_RE = re.compile(r"name:\s*(?:.*\n)*?\s*ru:\s*[\"']?([^\"'\n]+)[\"']?", re.MULTILINE)
NAME_EN_RE = re.compile(r"name:\s*(?:.*\n)*?\s*en:\s*[\"']?([^\"'\n]+)[\"']?", re.MULTILINE)
DESC_RU_RE = re.compile(r"description:\s*(?:.*\n)*?\s*ru:\s*[\"']?([^\"'\n]+)[\"']?", re.MULTILINE)
DESC_EN_RE = re.compile(r"description:\s*(?:.*\n)*?\s*en:\s*[\"']?([^\"'\n]+)[\"']?", re.MULTILINE)
RARITY_RE = re.compile(r"rarity:\s*([A-Z]+)", re.MULTILINE)

# Rarity accent colors
# Visual layout settings
DRAW_DESC_BOX = False # Set True to render a dark semi-transparent box behind description

RARITY_COLORS = {
    "COMMON": (255, 255, 255, 255),
    "UNCOMMON": (85, 255, 85, 255),
    "RARE": (85, 255, 255, 255),
    "EPIC": (255, 85, 255, 255),
    "LEGENDARY": (255, 215, 0, 255),
}

# Semantic archetype pools for thematic artwork matching
ARCHETYPE_POOLS = {
    "SUMMON": ["card1.png", "card37.png", "card49.png", "card20.png"],
    "SHIELD": ["card16.png", "card47.png", "card48.png", "card21.png"],
    "EXPLOSIVE": ["card4.png", "card22.png", "card45.png"],
    "MOBILITY": ["card10.png", "card24.png", "card5.png"],
    "HEALTH": ["card27.png", "card30.png", "card32.png", "card15.png"],
    "SHOOTING": ["card2.png", "card7.png", "card8.png", "card12.png", "card18.png", "card3.png", "card13.png", "card17.png"]
}

def get_font(size, bold=False):
    font_names = [
        "arialbd.ttf" if bold else "arial.ttf",
        "segui_sb.ttf" if bold else "segoeui.ttf",
        "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    ]
    for fn in font_names:
        try:
            return ImageFont.truetype(fn, size)
        except OSError:
            pass
    return ImageFont.load_default()

def wrap_text(text, max_chars=22):
    clean = re.sub(r"&[0-9a-fk-or]", "", text).strip()
    words = clean.split()
    lines = []
    cur = []
    cur_len = 0
    for w in words:
        if cur_len + len(w) + (1 if cur else 0) <= max_chars:
            cur.append(w)
            cur_len += len(w) + (1 if cur else 0)
        else:
            if cur:
                lines.append(" ".join(cur))
            cur = [w]
            cur_len = len(w)
    if cur:
        lines.append(" ".join(cur))
    return lines

def resolve_base_image(cid, text, base_images_dict):
    """
    Determines card visual suitability:
    1. Explicit 'art:' field defined in card YAML (e.g. art: card1.png).
    2. Fallback to default neutral card background if art is unmapped.
    """
    # 1. Explicit art property in YAML
    art_m = re.search(r"^\s*art\s*:\s*[\"']?([^\"'\n]+)[\"']?", text, re.M)
    if art_m:
        art_val = art_m.group(1).strip()
        for candidate in [art_val, f"{art_val}.png", f"card_{art_val}.png", f"card{art_val}.png"]:
            if candidate in base_images_dict:
                return base_images_dict[candidate]

    # 2. Fallback to default card art if not explicitly found (black-and-purple missing texture)
    default_candidates = ["card_default.png", "card_missing.png"]
    for c in default_candidates:
        if c in base_images_dict:
            return base_images_dict[c]

    all_imgs = list(base_images_dict.values())
    return all_imgs[0] if all_imgs else None

def render_card(base_image_path, title, desc, rarity, out_path, target_size=(320, 500)):
    img = Image.open(base_image_path).convert("RGBA")
    if target_size and img.size != target_size:
        img = img.resize(target_size, Image.Resampling.LANCZOS)
    w, h = img.size
    sx = w / 160.0
    sy = h / 250.0

    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # 1. Title bar at top (ultra-high position, dynamic 2-line wrap and auto-sizing)
    clean_title = re.sub(r"&[0-9a-fk-or]", "", title).strip()
    max_title_w = w - int(14 * sx)

    font_size = max(10, int(11.5 * sy))
    title_font = get_font(font_size, bold=True)

    bbox = draw.textbbox((0, 0), clean_title, font=title_font)
    tw = bbox[2] - bbox[0]

    if tw <= max_title_w:
        title_lines = [clean_title]
        ty = int(3.5 * sy)
        line_h = int(font_size * 1.1)
    else:
        title_lines = wrap_text(clean_title, max_chars=17)
        font_size = max(9, int(9.5 * sy))
        while font_size > int(7 * sy):
            title_font = get_font(font_size, bold=True)
            fits = True
            for line in title_lines[:2]:
                l_bbox = draw.textbbox((0, 0), line, font=title_font)
                if (l_bbox[2] - l_bbox[0]) > max_title_w:
                    fits = False
                    break
            if fits:
                break
            font_size -= 1
        ty = int(1.0 * sy)
        line_h = int(font_size * 1.05)

    title_bottom = ty
    for line in title_lines[:2]:
        bbox = draw.textbbox((0, 0), line, font=title_font)
        tw = bbox[2] - bbox[0]
        tx = max(int(4 * sx), (w - tw) // 2)
        # Drop shadow
        draw.text((tx + max(1, int(1 * sx)), ty + max(1, int(1 * sy))), line, font=title_font, fill=(0, 0, 0, 230))
        # Text with rarity tint
        tint = RARITY_COLORS.get(rarity.upper(), (255, 255, 255, 255))
        draw.text((tx, ty), line, font=title_font, fill=tint)
        title_bottom = ty + (bbox[3] - bbox[1])
        ty += line_h

    # 2. Description box in second half (anchored at the top of the lower half, expanding downwards)
    if desc and desc.strip():
        clean_desc = desc.strip()
        if len(clean_desc) > 120:
            desc_pt = 8.5
            max_chars = int(32 * (sx / 2.0)) if sx >= 1.5 else 22
        elif len(clean_desc) > 60:
            desc_pt = 9.5
            max_chars = int(28 * (sx / 2.0)) if sx >= 1.5 else 21
        else:
            desc_pt = 10.0
            max_chars = int(24 * (sx / 2.0)) if sx >= 1.5 else 20

        desc_font_size = max(7, int(desc_pt * sy))
        desc_font = get_font(desc_font_size, bold=False)
        desc_lines = wrap_text(clean_desc, max_chars=max(18, max_chars))

        line_spacing = int(desc_font_size * 1.25)
        box_pad_y = int(6 * sy)
        box_pad_x = int(8 * sx)
        box_h = len(desc_lines) * line_spacing + box_pad_y * 2

        base_start_y = int(155 * sy)
        if base_start_y + box_h > h - int(12 * sy):
            box_y1 = max(int(135 * sy), h - int(12 * sy) - box_h)
        else:
            box_y1 = base_start_y

        box_y2 = min(h - int(10 * sy), box_y1 + box_h)

        if DRAW_DESC_BOX:
            draw.rounded_rectangle([box_pad_x, box_y1, w - box_pad_x, box_y2], radius=max(2, int(4 * sx)), fill=(10, 12, 18, 205), outline=(50, 55, 70, 170))
            dy = box_y1 + box_pad_y
        else:
            dy = box_y1

        for line in desc_lines:
            bbox = draw.textbbox((0, 0), line, font=desc_font)
            lw = bbox[2] - bbox[0]
            lx = max(box_pad_x + int(4 * sx), (w - lw) // 2)
            if not DRAW_DESC_BOX:
                # 6-point shadow outline for crisp text visibility without background box
                for ox, oy in [(-1, 0), (1, 0), (0, -1), (0, 1), (1, 1), (2, 2)]:
                    draw.text((lx + ox, dy + oy), line, font=desc_font, fill=(0, 0, 0, 240))
                draw.text((lx, dy), line, font=desc_font, fill=(245, 245, 250, 255))
            else:
                draw.text((lx + max(1, int(1 * sx)), dy + max(1, int(1 * sy))), line, font=desc_font, fill=(0, 0, 0, 220))
                draw.text((lx, dy), line, font=desc_font, fill=(240, 240, 245, 255))
            dy += line_spacing

    final_img = Image.alpha_composite(img, overlay)
    final_img.save(out_path, "PNG")

def main():
    base_images = list(SRC_IMAGES_DIR.glob("*.png"))
    if not base_images:
        print("No base PNG images found in", SRC_IMAGES_DIR)
        return

    base_images_dict = {p.name: p for p in base_images}
    card_files = list(CARDS_DIR.rglob("*.yml"))
    processed = 0

    print(f"Generating localized card textures for {len(card_files)} card definitions with semantic matching...")

    for cfile in card_files:
        text = cfile.read_text(encoding="utf-8")
        id_m = ID_RE.search(text)
        if not id_m:
            continue
        cid = int(id_m.group(1))

        name_ru_m = NAME_RU_RE.search(text)
        name_en_m = NAME_EN_RE.search(text)
        desc_ru_m = DESC_RU_RE.search(text)
        desc_en_m = DESC_EN_RE.search(text)
        rarity_m = RARITY_RE.search(text)

        name_ru = name_ru_m.group(1) if name_ru_m else f"Карта #{cid}"
        name_en = name_en_m.group(1) if name_en_m else f"Card #{cid}"
        desc_ru = desc_ru_m.group(1) if desc_ru_m else ""
        desc_en = desc_en_m.group(1) if desc_en_m else ""
        rarity = rarity_m.group(1) if rarity_m else "COMMON"

        # Resolve semantically appropriate base artwork
        base_img = resolve_base_image(cid, text, base_images_dict)

        # Render RU
        render_card(base_img, name_ru, desc_ru, rarity, RU_OUT_DIR / f"card_{cid}.png")
        # Render EN
        render_card(base_img, name_en, desc_en, rarity, EN_OUT_DIR / f"card_{cid}.png")

        processed += 1

    print(f"Successfully baked textures for {processed} cards into 'cards/ru' and 'cards/en'.")

if __name__ == "__main__":
    main()
