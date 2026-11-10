#!/usr/bin/env python3
"""Derives verified NPC pins for the route from Quest Helper's step data.

Guide Overlay finds an NPC by name in a community spawn database. That database
stops around mid-2024 and only knows one NPC per name, so newer NPCs get no
arrow and a shared name ("Bartender", "Cook", "Veos") can point at the wrong
town. Quest Helper (BSD-2-Clause, github.com/Zoinkwiz/quest-helper) stores the
tile of every quest step, tested in game. This tool looks up each "Talk to X"
step of the route in the Quest Helper steps of the same quest and writes a pin
only where the plugin would otherwise show nothing or the wrong place.

Run by hand when the route or Quest Helper changes (needs network checkouts):

  git clone --depth 1 https://github.com/Zoinkwiz/quest-helper qh
  curl -LO https://raw.githubusercontent.com/mejrs/data_osrs/<pinned>/NPCList_OSRS.json
  python guide-tools/quest_helper_pins.py --quest-helper qh --npc-list NPCList_OSRS.json \
      --seed <guide-overlay>/src/main/resources/com/hcimguide/npc-locations-seed.json

Output: guide/source/verified_npc_pins.json, read by build_atlas.py. The file is
committed, so the normal build needs none of the inputs above.
"""
import argparse
import collections
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_atlas import DATA, steps_in  # noqa: E402

OUT = DATA / 'source' / 'verified_npc_pins.json'
STEP = re.compile(r'(\w+)\s*=\s*new\s+(\w*Step)\(\s*this\s*,\s*(.*?)\);', re.S)
POINT = re.compile(r'new\s+WorldPoint\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)')
NPC_STEPS = ('NpcStep', 'NpcEmoteStep', 'NpcFollowerStep')
# Verb case-insensitive, name case-sensitive: the name is the capitalised run.
VERB = re.compile(r"\b(?i:(talk(?:ing)? to|talk with|speak to|speak with|trade|kill|attack|pickpocket))\s+(?i:the\s+)?"
                  r"([A-Z][A-Za-z'’_-]*(?:\s+(?:[A-Z][A-Za-z'’_-]*|the|of|de))*)")
QUEST_WORDS = re.compile(r"(?i)\b(?:start|complete)\s+([A-Za-z][A-Za-z'’ &-]{2,45})")
SAME_PLACE = 20     # tiles: Quest Helper steps this close are one location
NEAR = 40           # tiles: the plugin's own location counts as agreeing


def norm(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


def quest_helper_steps(root):
    out = []
    for f in sorted((root / 'src/main/java/com/questhelper/helpers').rglob('*.java')):
        for m in STEP.finditer(f.read_text(errors='ignore')):
            if m.group(2) not in NPC_STEPS:
                continue
            p = POINT.search(m.group(3))
            text = re.search(r'"((?:[^"\\]|\\.)*)"', m.group(3)[p.end():]) if p else None
            if p and text:
                out.append({'quest': f.parent.name, 'point': (int(p[1]), int(p[2]), int(p[3])), 'text': text.group(1)})
    return out


def clusters(points):
    found = []
    for p in points:
        for c in found:
            if c[0][2] == p[2] and abs(c[0][0] - p[0]) <= SAME_PLACE and abs(c[0][1] - p[1]) <= SAME_PLACE:
                c.append(p)
                break
        else:
            found.append([p])
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--quest-helper', required=True, type=Path)
    ap.add_argument('--npc-list', required=True, type=Path)
    ap.add_argument('--seed', required=True, type=Path)
    args = ap.parse_args()

    qh = quest_helper_steps(args.quest_helper)
    quests = {s['quest'] for s in qh}
    spawns = collections.defaultdict(list)
    for e in json.loads(args.npc_list.read_text()):
        spawns[norm(e.get('name', ''))].append((e['x'], e['y'], e['p']))
    seed = {norm(k): tuple(v) for k, v in json.loads(args.seed.read_text()).items()}

    def quest_dir(name):
        k = norm(name)
        for c in (k, 'the' + k, re.sub(r'quest$', '', k), 'the' + re.sub(r'quest$', '', k)):
            if c in quests:
                return c
        return None

    def points(name, quest=None):
        pat = re.compile(r'(?<![A-Za-z])' + re.escape(name) + r'(?![A-Za-z])', re.I)
        return [s['point'] for s in qh if pat.search(s['text']) and (quest is None or s['quest'] == quest)]

    route = json.loads((DATA / 'HCIM_ATLAS_VOLLEDIGE_ROUTE.json').read_text('utf8'))
    pins, stats = [], collections.Counter()
    for chapter, section, _, _, text, _ in steps_in(route):
        m = VERB.search(text)
        if not m or text.startswith('Withdraw'):
            continue
        if re.search(r'[.!?]\s+\S', text) or re.search(r'(?i)\bwilderness\b', text):
            # Guide Overlay splits multi-sentence steps (other keys); never pin into the Wilderness.
            stats['skipped (multi-sentence or Wilderness)'] += 1
            continue
        verb = m.group(1).lower()
        name = re.sub(r'\s+(?:the|of|de)$', '', m.group(2)).strip()
        tagged = [quest_dir(b) for b in re.findall(r'\[([^\]]{2,80})\]', text)]
        named = QUEST_WORDS.search(text)
        if named:
            words = named.group(1).split()
            tagged += [quest_dir(' '.join(words[:n])) for n in range(len(words), 0, -1)]
        point = source = None
        for quest in filter(None, tagged):
            found = clusters(points(name, quest))
            if len(found) == 1:
                point, source = found[0][0], quest
                break
            if found:
                source = 'moves'    # the NPC stands in several places during this quest
                break
        if point is None and source is None and verb.startswith(('talk', 'speak', 'trade')) and ' ' in name:
            found = clusters(points(name))
            if len(found) == 1:
                point, source = found[0][0], 'any quest'
        if point is None:
            stats['no single Quest Helper location'] += 1
            continue

        def near(a):
            return abs(a[0] - point[0]) <= NEAR and abs(a[1] - point[1]) <= NEAR

        key = norm(name)
        if key in seed:
            reason = None if near(seed[key]) else 'plugin points at another place'
        elif not spawns.get(key):
            reason = 'NPC missing from the spawn database'
        elif all(near(s) for s in spawns[key]):
            reason = None
        elif any(near(s) for s in spawns[key]):
            reason = 'several NPCs share this name'
        else:
            reason = 'plugin points at another NPC with this name'
        if reason is None:
            stats['plugin already right'] += 1
            continue
        stats[reason] += 1
        pins.append({'chapter': chapter['title'], 'section': section['title'], 'text': text, 'npc': name,
                     'x': point[0], 'y': point[1], 'plane': point[2],
                     'source': f'Quest Helper ({source})', 'reason': reason})

    OUT.write_text(json.dumps({'note': 'Generated by guide-tools/quest_helper_pins.py from Quest Helper (BSD-2-Clause) step data. '
                                       'Edit MANUAL_PINS in build_atlas.py for hand-made pins, not this file.',
                               'pins': pins}, ensure_ascii=False, indent=1) + '\n', encoding='utf8')
    print(json.dumps({'pins': len(pins), **stats}, indent=2))


if __name__ == '__main__':
    main()
