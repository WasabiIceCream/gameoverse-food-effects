#!/usr/bin/env python3
"""Builds the food effect table for gameoverse-food-effects.

Inputs (from the dev-only gameoverse-recipe-audit mod, run on the local server):
  /gameoverse_food_audit  -> recipe-audit/foods.tsv
  /gameoverse_recipe_dump -> recipe-audit/recipes.jsonl, recipe-audit/item_tags.tsv
plus families.json (families, effects, tiers) and overrides.json (per-item fixes) next to this script.

Outputs:
  ../src/main/resources/gameoverse_food_effects/food_effects.json  (read by the mod)
  review.tsv  (every food: tier, families, final effects, where they came from)

Usage: python3 generate.py [path/to/recipe-audit]   (default: the local server's recipe-audit/)
"""
import collections
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
AUDIT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parents[2] / 'fabric 26.1' / 'recipe-audit'
OUT = HERE.parent / 'src/main/resources/gameoverse_food_effects/food_effects.json'
CFG = json.loads((HERE / 'families.json').read_text())
OVR = json.loads((HERE / 'overrides.json').read_text()) if (HERE / 'overrides.json').exists() else {}
OVR = {k: v for k, v in OVR.items() if not k.startswith('_')}

FAMS = [(f['id'], re.compile(f['pattern'])) for f in CFG['families']]
FAM_EFFECT = {f['id']: f['effect'] for f in CFG['families']}
FAM_MAX = {f['id']: f['max_seconds'] for f in CFG['families'] if 'max_seconds' in f}
STAPLES = set(CFG['staples'])
NEUTRAL = re.compile(CFG['neutral'])
DEAD = set(CFG['dead_effects'])
TIERS = ['raw', 'prepared', 'dish', 'meal']
PREP = set(CFG['prep_types'])
MEAL = set(CFG['meal_types'])
SKIP = re.compile(CFG['skip_recipe'])
SKIP_ING = re.compile(CFG['skip_ingredient'])
NAME_TIERS = [(TIERS.index(k), re.compile(v)) for k, v in CFG['name_tiers'].items()]
RES_KEYS = ('result', 'output', 'results', 'outputs')
SKIP_KEYS = {'type', 'category', 'group', 'sound', 'tool', 'container', 'carrier', 'model_id', 'finished_texture',
             'soup_base', 'recipe_book_tab', 'subtype', 'fluid_ingredient', 'fluid_ingredients', 'fluid_results'}
ID = re.compile(r'^#?[a-z0-9_.-]+:[a-z0-9_./-]+$')

# --- inputs -----------------------------------------------------------------------------------------------------
foods = {}
for line in (AUDIT / 'foods.tsv').read_text().splitlines()[1:]:
    c = line.split('\t')
    foods[c[0]] = {'name': c[1], 'nutrition': int(c[2]), 'effects': c[6]}

if any('gameoverse_food_effects:' in f['effects'] for f in foods.values()):
    sys.exit('foods.tsv was dumped with gameoverse-food-effects installed: its effects would count as the mods\' own. '
             'Take the jar out of mods/, re-run /gameoverse_food_audit, then generate.')

tags = {}
for line in (AUDIT / 'item_tags.tsv').read_text().splitlines():
    k, _, v = line.partition('\t')
    tags[k] = set(v.split(',')) if v else set()
item_tags = collections.defaultdict(set)
for k, v in tags.items():
    for m in v:
        item_tags[m].add(k)


def results(rec):
    out = []

    def take(v):
        if isinstance(v, str):
            out.append(v)
        elif isinstance(v, dict):
            if isinstance(v.get('id'), str):
                out.append(v['id'])
            elif isinstance(v.get('item'), dict):
                take(v['item'])
            elif isinstance(v.get('item'), str):
                out.append(v['item'])
        elif isinstance(v, list):
            for x in v:
                take(x)
    for k in RES_KEYS:
        if k in rec:
            take(rec[k])
    return [o for o in out if not o.startswith('#')]


def ingredients(rec):
    slots = []

    def walk(v, top=False):
        if isinstance(v, str):
            if ID.match(v):
                slots.append(v)
        elif isinstance(v, dict):
            if isinstance(v.get('tag'), str):
                slots.append('#' + v['tag'])
                return
            if isinstance(v.get('item'), str):
                slots.append(v['item'])
                return
            for k, x in v.items():
                if top and (k in RES_KEYS or k in SKIP_KEYS):
                    continue
                walk(x)
        elif isinstance(v, list):
            for x in v:
                walk(x)
    if 'pattern' in rec:
        key = rec.get('key', {})
        for row in rec['pattern']:
            for ch in row:
                if ch != ' ' and ch in key:
                    walk(key[ch])
    else:
        walk(rec, True)
    return slots


by_result = collections.defaultdict(list)
for line in (AUDIT / 'recipes.jsonl').read_text().splitlines():
    r = json.loads(line)
    rec = r.get('recipe')
    if not rec or SKIP.search(r['id']):
        continue
    ing = ingredients(rec)
    if any(SKIP_ING.search(s) for s in ing):
        continue
    for x in results(rec):
        by_result[x].append((r['id'], rec.get('type'), ing))


# --- classification ---------------------------------------------------------------------------------------------
def family_of(name):
    path = name.split(':', 1)[-1]
    for fid, rx in FAMS:
        if rx.search(path):
            return fid
    return None


def natural(item):
    return any(t.startswith(tuple(CFG['natural_tag_prefixes'])) for t in item_tags[item])


def members(slot):
    return tags.get(slot[1:], set()) if slot.startswith('#') else {slot}


info = {}


def analyze(item, stack=()):
    """(tier index, Counter of families among its ingredients, recipe id used)."""
    if item in info:
        return info[item]
    if item in stack or NEUTRAL.search(item):
        return 0, collections.Counter(), ''
    sig = family_of(item)
    recipes = by_result.get(item, [])
    if not recipes and not natural(item):
        path = item.split(':', 1)[-1]
        tier = max([t for t, rx in NAME_TIERS if rx.search(path)] + [0])
        r = (tier, collections.Counter([sig] if sig else []), 'name')
        info[item] = r
        return r
    cooked = any(rx.search(item.split(':', 1)[-1]) for _, rx in NAME_TIERS)
    if not recipes or (sig and natural(item) and not cooked):
        r = (0, collections.Counter([sig] if sig else []), '')
        info[item] = r
        return r
    best = None
    for rid, rtype, ing in recipes:
        slots = [s for s in ing if not NEUTRAL.search(s)]
        fams = collections.Counter()
        sub = 0
        for s in slots:
            if s.startswith('#'):
                d = family_of(s)
                if d:
                    fams[d] += 1
                    continue
                ms = sorted(members(s))[:40]
                cc = collections.Counter(f for m in ms for f in analyze(m, stack + (item,))[1])
                for f, n in cc.items():
                    if n >= 0.6 * len(ms):
                        fams[f] += 1
            else:
                st, sc, _ = analyze(s, stack + (item,))
                sub = max(sub, st)
                for f in sc:
                    fams[f] += 1
        distinct = len(set(slots))
        if rtype in MEAL:
            tier = 3
        elif rtype in PREP or distinct <= 1:
            tier = max(1, sub)
        else:
            tier = 2 if sub < 2 else 3
        if sig and sig not in fams:
            fams[sig] += 1
        key = (tier, 0 if (sig and sig in fams) else 1)
        if best is None or key < best[0]:
            best = (key, (tier, fams, rid))
    info[item] = best[1]
    return best[1]


def parse_existing(s):
    """foods.tsv effects column -> list of (effect, amplifier, seconds, probability); other consume effects dropped."""
    out = []
    for part in filter(None, (p.strip() for p in s.split(';'))):
        prob = 1.0
        if '@' in part:
            part, p = part.rsplit('@', 1)
            prob = float(p)
        for e in part.split(','):
            bits = e.split()
            if len(bits) == 3 and ':' in bits[0]:
                out.append((bits[0], int(bits[1]) - 1, int(bits[2][:-1]), prob))
    return out


def nutrition_scale(n):
    return min(1.5, max(0.75, 0.75 + n / 16))


table = {}
review = [['id', 'name', 'tier', 'families', 'effects', 'kept_from_mod', 'dropped', 'recipe']]
for item, f in sorted(foods.items()):
    if OVR.get(item, {}).get('skip'):
        continue
    src = item
    if not by_result.get(item) and item + '_block' in by_result:
        src = item + '_block'
    tier, fams, rid = analyze(src)
    if src != item:
        tier = 3
    o = OVR.get(item, {})
    if o.get('skip'):
        continue
    if 'tier' in o:
        tier = TIERS.index(o['tier'])
    if 'families' in o:
        fams = collections.Counter(o['families'])
    sig = o.get('families', [family_of(item)])[0] if o.get('families') else family_of(item)
    ranked = sorted(fams, key=lambda x: (x != sig, x in STAPLES, -fams[x], x))
    tcfg = CFG['tiers'][TIERS[tier]]
    existing = parse_existing(f['effects'])
    kept = [e for e in existing if e[0] not in DEAD]
    dropped = [e[0] for e in existing if e[0] in DEAD]
    seconds = round(tcfg['seconds'] * nutrition_scale(f['nutrition']))
    final = [{'effect': e, 'amplifier': a, 'seconds': d, 'probability': p} for e, a, d, p in kept]
    have = {e['effect'] for e in final}
    added = []
    for fam in ranked:
        if len(final) >= tcfg['max_effects']:
            break
        eff = FAM_EFFECT.get(fam)
        if not eff or eff in have:
            continue
        final.append({'effect': eff, 'amplifier': 0, 'seconds': min(seconds, FAM_MAX.get(fam, seconds)), 'probability': 1.0, 'family': fam})
        have.add(eff)
        added.append(fam)
    changed = bool(added or dropped)
    if changed:
        table[item] = [{k: v for k, v in e.items() if k != 'family'} for e in final]
    review.append([item, f['name'], TIERS[tier], ','.join(ranked),
                   ', '.join(f"{e['effect'].split(':')[1]} {e['seconds']}s" for e in final),
                   ','.join(e[0].split(':')[1] for e in kept), ','.join(d.split(':')[1] for d in dropped), rid])

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({'_generated_by': 'tools/generate.py', 'foods': table}, indent=1, sort_keys=True) + '\n')
(HERE / 'review.tsv').write_text('\n'.join('\t'.join(r) for r in review) + '\n')
none = [r[0] for r in review[1:] if not r[4]]
print(f'{len(foods)} foods, {len(table)} changed, {len(none)} without any effect')
print('tiers:', dict(collections.Counter(r[2] for r in review[1:])))
if none:
    print('no effect:', ' '.join(none))
