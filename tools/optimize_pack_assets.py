#!/usr/bin/env python3
"""
Resource pack asset optimizer for Rounds-MinecraftPlugin.
1. Quantizes card PNG textures to 256-color palette (FastOctree + Floyd-Steinberg dithering).
   Reduces PNG sizes from ~45 KB to ~9 KB (~80% savings) with imperceptible visual loss.
2. Optimizes background OGG music tracks via ffmpeg libvorbis at transparent game audio quality.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow is required: pip install Pillow")
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
CARDS_DIR = ROOT / "test-servers" / "original_mod" / "cards"
SOUNDS_DIR = ROOT / "test-servers" / "original_mod" / "sounds"

def find_ffmpeg():
    cmd = shutil.which("ffmpeg")
    if cmd:
        return cmd
    custom_candidates = [
        Path(r"C:\Users\Mr712\Documents\MR-CLI-FOR-YT-DLP\configs\ffmpeg.exe"),
        Path(r"C:\ffmpeg\bin\ffmpeg.exe"),
        Path(r"C:\Program Files\ffmpeg\bin\ffmpeg.exe"),
    ]
    for c in custom_candidates:
        if c.exists():
            return str(c)
    return None

def optimize_png_file(path: Path) -> tuple[int, int]:
    orig_size = path.stat().st_size
    try:
        with Image.open(path) as im:
            # Skip if already in P mode with 256 or fewer colors
            if im.mode == "P" and orig_size < 15 * 1024:
                return orig_size, orig_size
            quantized = im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
            tmp_path = path.with_suffix(".tmp.png")
            quantized.save(tmp_path, "PNG", optimize=True, compress_level=9)
            new_size = tmp_path.stat().st_size
            if new_size < orig_size:
                tmp_path.replace(path)
                return orig_size, new_size
            else:
                tmp_path.unlink(missing_ok=True)
                return orig_size, orig_size
    except Exception as e:
        print(f"Error optimizing {path.name}: {e}")
        return orig_size, orig_size

def optimize_pngs():
    dirs = [CARDS_DIR, CARDS_DIR / "ru", CARDS_DIR / "en"]
    total_before = 0
    total_after = 0
    count = 0

    print("--- Optimizing Card PNG Textures ---")
    for d in dirs:
        if not d.exists():
            continue
        pngs = sorted(list(d.glob("*.png")))
        print(f"Processing {len(pngs)} PNGs in {d.name}...")
        for p in pngs:
            b, a = optimize_png_file(p)
            total_before += b
            total_after += a
            count += 1

    saved = total_before - total_after
    pct = (saved / total_before * 100) if total_before else 0
    print(f"PNG Optimization complete: {count} files processed.")
    print(f"Before: {total_before / (1024*1024):.2f} MB -> After: {total_after / (1024*1024):.2f} MB (Saved: {saved / (1024*1024):.2f} MB, {pct:.1f}%)")

def optimize_ogg_files():
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        print("ffmpeg not found, skipping OGG audio optimization.")
        return

    print("--- Optimizing OGG Music Tracks ---")
    music_files = ["music1.ogg", "music2.ogg", "music3.ogg", "music4.ogg"]
    total_before = 0
    total_after = 0

    for name in music_files:
        p = SOUNDS_DIR / name
        if not p.exists():
            continue
        orig_size = p.stat().st_size
        tmp = SOUNDS_DIR / (name + ".tmp.ogg")
        # Transcode to high quality libvorbis (q:a 1.5 ~ 80-90 kbps)
        cmd = [ffmpeg, "-y", "-i", str(p), "-c:a", "libvorbis", "-q:a", "1.5", str(tmp)]
        res = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if res.returncode == 0 and tmp.exists():
            new_size = tmp.stat().st_size
            if new_size < orig_size:
                tmp.replace(p)
                total_before += orig_size
                total_after += new_size
                print(f"{name}: {orig_size / (1024*1024):.2f} MB -> {new_size / (1024*1024):.2f} MB (saved {((orig_size - new_size)/orig_size)*100:.1f}%)")
            else:
                tmp.unlink(missing_ok=True)
                total_before += orig_size
                total_after += orig_size
        else:
            total_before += orig_size
            total_after += orig_size
            if tmp.exists():
                tmp.unlink(missing_ok=True)

    saved = total_before - total_after
    pct = (saved / total_before * 100) if total_before else 0
    print(f"OGG Optimization complete.")
    print(f"Before: {total_before / (1024*1024):.2f} MB -> After: {total_after / (1024*1024):.2f} MB (Saved: {saved / (1024*1024):.2f} MB, {pct:.1f}%)")

def main():
    optimize_pngs()
    optimize_ogg_files()

if __name__ == "__main__":
    main()
