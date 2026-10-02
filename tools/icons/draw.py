#!/usr/bin/env python3
"""Draws the 18x18 mob effect icons from the pixel maps below (original art, MIT like the mod)."""
from pathlib import Path
from PIL import Image
OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/gameoverse_food_effects/textures/mob_effect'
PAL = {'.': None, 'k': (40, 30, 25), 'w': (250, 245, 230)}
ICONS = {
 # anchor-ish boot: steadfast
 'steadfast': ({'a': (139, 106, 62), 'b': (176, 140, 90), 'c': (95, 70, 40)}, [
  "..................",
  "......kkkkk.......",
  "......kbbbk.......",
  "......kbabk.......",
  "......kbabk.......",
  "......kbabk.......",
  "......kbabk.......",
  "......kbabk.......",
  "......kbaakkkk....",
  ".....kbaaaaaabk...",
  ".....kbaaaaaaabk..",
  ".....kbaaaaaaaak..",
  ".....kccccccccck..",
  ".....kkkkkkkkkkk..",
  "..................",
  "..................",
  "..................",
  ".................."]),
 'fresh': ({'a': (108, 194, 74), 'b': (160, 230, 120), 'c': (60, 130, 40)}, [
  "..................",
  "...........kkk....",
  ".........kkbbak...",
  ".......kkbbaaak...",
  "......kbbaaaaak...",
  ".....kbaaaacaak...",
  "....kbaaaacaaak...",
  "....kbaaacaaak....",
  "...kbaaacaaaak....",
  "...kbaacaaaak.....",
  "...kbacaaaakk.....",
  "...kacaaakk.......",
  "...kcakkk.........",
  "..kckk............",
  ".kck..............",
  ".kk...............",
  "..................",
  ".................."]),
 'keen': ({'a': (194, 48, 74), 'b': (240, 110, 130), 'c': (130, 20, 40)}, [
  "..................",
  "........kk........",
  ".......kbak.......",
  ".......kbak.......",
  "......kbaaak......",
  "......kbaaak......",
  ".....kbaaaaak.....",
  "kkkkkkbaaaaackkkkk",
  "kbbbbbaaaaaaaaaaak",
  "kkkkkcaaaaaacckkkk",
  ".....kcaaaaack....",
  "......kcaaack.....",
  "......kcaaack.....",
  ".......kcack......",
  ".......kcack......",
  "........kk........",
  "..................",
  ".................."]),
 'focused': ({'a': (122, 92, 214), 'b': (180, 160, 250), 'c': (80, 50, 160)}, [
  "..................",
  "........kk........",
  "........kbk.......",
  ".......kbak.......",
  ".......kbak.......",
  "..kkkkkbaaakkkkk..",
  "..kbbbbaaaaaaaak..",
  "...kcaaaaaaaack...",
  "....kcaaaaaack....",
  ".....kaaaaaak.....",
  "....kaaaaaaaak....",
  "....kaaackaaak....",
  "...kaack..kcaak...",
  "...kack....kcak...",
  "...kk........kk...",
  "..................",
  "..................",
  ".................."]),
 'mana_flow': ({'a': (58, 141, 222), 'b': (140, 200, 250), 'c': (30, 80, 160)}, [
  "..................",
  "........kk........",
  "........kk........",
  ".......kbak.......",
  ".......kbak.......",
  "......kbaaak......",
  "......kbaaak......",
  ".....kbaaaaak.....",
  "....kbaaaaaaak....",
  "....kbaaaaaaak....",
  "...kbwaaaaaaaak...",
  "...kbwaaaaaaaak...",
  "...kbbwaaaaaack...",
  "....kbaaaaaacck...",
  "....kcaaaaaacck...",
  ".....kkccccckk....",
  ".......kkkkk......",
  ".................."]),
 'well_fed': ({'a': (232, 163, 60), 'b': (250, 210, 130), 'c': (170, 110, 30), 'd': (220, 220, 210)}, [
  "..................",
  "..................",
  "......kk..kk......",
  ".....k.....k......",
  "......k...k.......",
  "..................",
  "..kkkkkkkkkkkkkk..",
  ".kbbbbbbbbbbbbbbk.",
  ".kaaaaaaaaaaaaaak.",
  "..kaaaaaaaaaaaak..",
  "..kaaaaaaaaaaaak..",
  "...kcaaaaaaaack...",
  "...kcaaaaaaaack...",
  "....kccaaaacck....",
  ".....kkkkkkkk.....",
  "......kddddk......",
  ".....kkkkkkkk.....",
  ".................."]),
}
OUT.mkdir(parents=True, exist_ok=True)
for name, (pal, rows) in ICONS.items():
    p = dict(PAL, **pal)
    img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 18, (name, y, len(row))
        for x, ch in enumerate(row):
            c = p[ch]
            if c:
                img.putpixel((x, y), c + (255,))
    img.save(OUT / f'{name}.png')
print('drew', ', '.join(ICONS))
