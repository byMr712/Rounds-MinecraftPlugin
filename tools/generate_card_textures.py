#!/usr/bin/env python3
"""
Automated card texture generator for Rounds Minecraft Plugin.
Reads YAML card definitions (ru/en names and descriptions) and bakes
formatted text directly onto semantically matching card face textures.

Standard resolution is 160x250 (the original source art size): no resizing
is ever needed downstream, and every card variation gets its own texture
named card<id>-<rarity>.png (e.g. card5-common.png) with description
placeholders ({0}, {1}, {v}) filled from the variation's "values" list.
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

# Standard card texture resolution: 160x250 (matches original source art).
CARD_SIZE = (160, 250)

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

def wrap_text_px(text, font, max_w, draw):
    """Wraps text by measured pixel width so lines use the full available card width."""
    clean = re.sub(r"&[0-9a-fk-or]", "", text).strip()
    words = clean.split()
    lines = []
    cur = []
    for w_ in words:
        test = " ".join(cur + [w_]) if cur else w_
        if draw.textlength(test, font=font) <= max_w:
            cur.append(w_)
        else:
            if cur:
                lines.append(" ".join(cur))
            cur = [w_]
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

def parse_variations(text):
    """Parses the 'variations:' list into dicts {rarity, values, art, descriptions}."""
    variations = []
    lines = text.splitlines()
    in_variations = False
    current = None
    sub = None  # None | "values_block" | "desc_map"
    for line in lines:
        if re.match(r"^\s*variations\s*:\s*$", line):
            in_variations = True
            continue
        if not in_variations:
            continue
        m = re.match(r"^\s*-\s+rarity\s*:\s*([A-Za-z]+)", line)
        if m:
            current = {"rarity": m.group(1).upper(), "values": [], "art": None, "descriptions": {}}
            variations.append(current)
            sub = None
            continue
        if current is None:
            continue
        m = re.match(r"^\s*art\s*:\s*[\"']?([^\"'\n]+)", line)
        if m and current["art"] is None:
            current["art"] = m.group(1).strip()
            sub = None
            continue
        m = re.match(r"^\s*description\s*:\s*$", line)
        if m:
            sub = "desc_map"
            continue
        if sub == "desc_map":
            m = re.match(r"^\s*(ru|en)\s*:\s*[\"']?([^\"'\n]+)", line)
            if m:
                current["descriptions"][m.group(1)] = m.group(2).strip()
                continue
            sub = None
        m = re.match(r"^\s*value\s*:\s*[\"']?([^\"'\n]+)", line)
        if m and not current["values"]:
            # Singular "value:" (e.g. doki-doki, insanely-first) fills {v}/{sec} only.
            current["values"] = [m.group(1).strip()]
            sub = None
            continue
        m = re.match(r"^\s*values\s*:\s*\[(.*)\]\s*$", line)
        if m:
            raw = m.group(1)
            current["values"] = [v.strip().strip('"\'') for v in raw.split(",") if v.strip()]
            sub = None
            continue
        m = re.match(r"^\s*values\s*:\s*$", line)
        if m:
            current["values"] = []
            sub = "values_block"
            continue
        if sub == "values_block":
            m = re.match(r"^\s*-\s*[\"']?([^\"'\n]+)", line)
            if m:
                current["values"].append(m.group(1).strip().strip('"\''))
                continue
            sub = None
    return variations

def fill_template(desc, values):
    if not desc:
        return desc
    for i, v in enumerate(values):
        desc = desc.replace("{" + str(i) + "}", v)
    if values:
        desc = desc.replace("{v}", values[0]).replace("{sec}", values[0])
    return desc

def render_card(base_image_path, title, desc, rarity, out_path, target_size=CARD_SIZE):
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

    # 2. Description block in the lower third: anchored at a fixed top and flowing downwards,
    #    using the full card width; the font shrinks for long text instead of climbing upwards.
    if desc and desc.strip():
        clean_desc = desc.strip()
        box_pad_x = int(8 * sx)
        max_text_w = w - box_pad_x * 2 - int(8 * sx)
        area_top = int(155 * sy)
        area_bottom = h - int(10 * sy)

        desc_font_size = max(7, int(10.0 * sy))
        desc_font = get_font(desc_font_size, bold=False)
        desc_lines = wrap_text_px(clean_desc, desc_font, max_text_w, draw)
        while desc_font_size > 6:
            line_h = int(desc_font_size * 1.25)
            if len(desc_lines) * line_h <= area_bottom - area_top:
                break
            desc_font_size -= 1
            desc_font = get_font(desc_font_size, bold=False)
            desc_lines = wrap_text_px(clean_desc, desc_font, max_text_w, draw)

        line_spacing = int(desc_font_size * 1.25)
        dy = area_top
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
    # Quantize to 256-color palette with FastOctree & Floyd-Steinberg dithering:
    # Drops texture size from ~45 KB to ~9 KB (~80% compression) with near-zero perceptual loss.
    quantized = final_img.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
    quantized.save(out_path, "PNG", optimize=True, compress_level=9)

def main():
    base_images = list(SRC_IMAGES_DIR.glob("*.png"))
    if not base_images:
        print("No base PNG images found in", SRC_IMAGES_DIR)
        return

    base_images_dict = {p.name: p for p in base_images}
    card_files = list(CARDS_DIR.rglob("*.yml"))
    processed = 0
    variation_count = 0

    # Clean regeneration at the standard 160x250 resolution.
    for d in (RU_OUT_DIR, EN_OUT_DIR):
        for p in d.glob("*.png"):
            p.unlink()

    print(f"Generating localized 160x250 card textures for {len(card_files)} card definitions...")

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

        variations = parse_variations(text)
        if variations:
            for var in variations:
                rar = var["rarity"]
                vd = var["descriptions"]
                if vd:
                    d_ru = vd.get("ru", desc_ru)
                    d_en = vd.get("en", desc_en)
                else:
                    d_ru = fill_template(desc_ru, var["values"])
                    d_en = fill_template(desc_en, var["values"])
                out_name = f"card{cid}-{rar.lower()}.png"
                render_card(base_img, name_ru, d_ru, rar, RU_OUT_DIR / out_name)
                render_card(base_img, name_en, d_en, rar, EN_OUT_DIR / out_name)
                variation_count += 1
        else:
            render_card(base_img, name_ru, desc_ru, rarity, RU_OUT_DIR / f"card_{cid}.png")
            render_card(base_img, name_en, desc_en, rarity, EN_OUT_DIR / f"card_{cid}.png")

        processed += 1

    print(f"Successfully baked textures for {processed} cards ({variation_count} variations) into 'cards/ru' and 'cards/en' at 160x250.")

if __name__ == "__main__":
    main()
