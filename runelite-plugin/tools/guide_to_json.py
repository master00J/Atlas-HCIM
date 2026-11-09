#!/usr/bin/env python3
"""
Zet de HCIM Macroquest Guide (Markdown-export van de Claude Doc) om naar
route.json voor de RuneLite-plugin.

Gebruik:
    python3 tools/guide_to_json.py tools/guide.md \
        src/main/resources/com/hcimroute/route.json

Wat het script herkent:
  ## Kop              -> hoofdstuk (Fase 0 ... Fase 6, HCIM-veiligheid, ...)
  ### Kop             -> sectie (Bijlagen) of groepslabel (Fase 4 Deel A/B)
  **Bank X – ...**    -> sectie met afvinkbare stappen (genummerde lijst)
  **Titel**           -> sectie (gevolgd door lijst, tabel of tekst)
  1. tekst            -> afvinkbare stap
  - tekst             -> stap of notitie (notitie in info-secties)
  | tabel |           -> tabel (in "Nieuwe content" worden rijen stappen)

Per stap haalt het script ook op:
  complete  -> quests die in deze stap voltooid worden ("Voltooi X")
  targets   -> skill-doelen ("tot 62 Farming", "tot 31.512 Thieving XP")
  flags     -> hcim / optional / new
Questnamen worden gecontroleerd tegen tools/runelite_quests.txt
(de namen uit net.runelite.api.Quest), zodat de plugin ze kan opzoeken.
"""
import difflib
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

SKILLS = [
    "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic",
    "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting",
    "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming",
    "Runecraft", "Hunter", "Construction", "Sailing",
]
SKILL_RE = r"(?P<skill>" + "|".join(SKILLS) + r")(?:ing)?"

# Fase 2 loopt parallel met de hoofdroute; "volgende stap" slaat haar over.
PARALLEL_CHAPTERS = ("Fase 2",)
TRACKABLE_CHAPTERS = ("Fase 0", "Fase 1", "Fase 2", "Fase 3", "Fase 4", "Fase 5", "Fase 6")
INFO_SECTION_PREFIXES = ("Efficiëntie-check", "Veiligheid", "Hoe je leest", "Vaste", "Mijlpalen")

QUEST_ALIASES = {
    "mournings end i": "Mourning's End Part I",
    "mournings end ii": "Mourning's End Part II",
    "desert treasure": "Desert Treasure I",
    "dragon slayer": "Dragon Slayer I",
    "fairy tale i": "Fairytale I - Growing Pains",
    "fairy tale ii": "Fairytale II - Cure a Queen",
    "ribbiting tale of a lily pad dispute": "The Ribbiting Tale of a Lily Pad Labour Dispute",
    "digsite": "The Dig Site",
    "rfd": "Recipe for Disaster",
    "ethically acquired antiques": "Ethically Acquired Antiquities",
    "garden of tranquility": "Garden of Tranquillity",
    "barcrawl": "Alfred Grimhand's Barcrawl",
}
RFD_PARTS = [
    ("goblin", "Recipe for Disaster - Wartface & Bentnoze"),
    ("dwarf", "Recipe for Disaster - Mountain Dwarf"),
    ("evil dave", "Recipe for Disaster - Evil Dave"),
    ("pirate", "Recipe for Disaster - Pirate Pete"),
    ("lumbridge guide", "Recipe for Disaster - Lumbridge Guide"),
    ("skrach", "Recipe for Disaster - Skrach Uglogwee"),
    ("sir amik", "Recipe for Disaster - Sir Amik Varze"),
    ("awowogei", "Recipe for Disaster - King Awowogei"),
]


def norm(name: str) -> str:
    n = name.lower().strip()
    n = re.sub(r"^the\s+", "", n)
    n = n.replace("’", "'")
    n = re.sub(r"[^a-z0-9 ]", "", n)
    return re.sub(r"\s+", " ", n).strip()


class QuestIndex:
    def __init__(self, names):
        self.names = names
        self.by_norm = {norm(n): n for n in names}
        self.misses = set()

    def match(self, cand: str):
        c = norm(cand)
        if not c:
            return None
        if c in QUEST_ALIASES:
            return QUEST_ALIASES[c]
        if c in self.by_norm:
            return self.by_norm[c]
        best = difflib.get_close_matches(c, list(self.by_norm), n=1, cutoff=0.88)
        if best:
            return self.by_norm[best[0]]
        self.misses.add(cand)
        return None


# ---------------------------------------------------------------- inline text

def clean_inline(text: str):
    """Ontsnapte tekens terugzetten en links apart houden."""
    text = text.replace("\\[", "[").replace("\\]", "]").replace("\\~", "~")
    text = text.replace("&#91;", "[")
    links = []

    def repl(m):
        links.append({"title": m.group(1), "url": m.group(2)})
        return m.group(1)

    text = re.sub(r"\[([^\]]+)\]\((https?://[^)\s]+)\)", repl, text)
    return text.strip(), links


def plain(text: str) -> str:
    return text.replace("**", "").replace("*", "")


def parse_number(s: str) -> int:
    return int(s.replace(".", "").replace(",", ""))


# ---------------------------------------------------------------- extraction

def extract_completions(text: str, qi: QuestIndex):
    found = []
    flat = plain(text)
    stop = r"(?=[.;:]\s|[.;:]$|\s*\(|\s+met\s|\s+bij\s|\s+zodra\s|\s+in de\s|\s+samen\b|\s+–|$)"
    for m in re.finditer(r"[Vv]oltooi\s+(.+?)" + stop, flat):
        chunk = re.sub(r"^(tussendoor|meteen|nu)\s+", "", m.group(1).strip())
        if chunk.startswith("bij ") or chunk.startswith("die delen"):
            if chunk.startswith("die delen"):
                low = flat.lower()
                for key, q in RFD_PARTS:
                    if key in low and "rfd" in low:
                        found.append(q)
            continue
        parts = re.split(r",\s+|\s+en\s+", chunk)
        for p in parts:
            p = p.strip().rstrip(".!").strip()
            p = re.sub(r"^(het|de)\s+", "", p)
            low = p.lower()
            if "rfd" in low or "recipe for disaster" in low and "-" in low:
                hit = [q for key, q in RFD_PARTS if key in low]
                if hit:
                    found.extend(hit)
                    continue
            q = qi.match(p)
            if q:
                found.append(q)
    out = []
    for q in found:
        if q not in out:
            out.append(q)
    return out


TARGET_PATTERNS = [
    # tot 31.512 Thieving XP / tot ongeveer 627.000 Agility XP
    ("xp", re.compile(r"(?:tot|naar)\s+(?:ongeveer\s+)?(?P<n>\d{1,3}(?:\.\d{3})+|\d{4,})\s+" + SKILL_RE + r"\s+XP")),
    # "..., 65 Strength en 680.000 Defence XP" (opsomming na 'tot'; geen '+1.000 X XP'-beloning)
    ("xp", re.compile(r"(?:,|\sen)\s+(?P<n>\d{1,3}(?:\.\d{3})+)\s+" + SKILL_RE + r"\s+XP")),
    # Mining tot 97.833 XP
    ("xp", re.compile(SKILL_RE + r"\s+(?:tot|naar)\s+(?P<n>\d{1,3}(?:\.\d{3})+)\s+XP")),
    # tot 62 Farming / naar 83 Construction
    ("level", re.compile(r"(?:tot|naar|op)\s+(?:minstens\s+)?(?P<n>\d{1,2})\+?\s+" + SKILL_RE + r"\b(?!\s+XP)")),
    # Draynor Agility tot 5 / Woodcutting naar 15 / Slayer tot 40
    ("level", re.compile(SKILL_RE + r"\s+(?:tot|naar)\s+(?P<n>\d{1,2})\b(?![\d.]|\s*(?:XP|punten|coins|Ranged|Prayer|Attack|Strength|Defence|Magic))")),
]


def extract_targets(text: str):
    # "op weg naar 60 Ranged" is context, geen doel van deze stap
    flat = re.sub(r"op weg naar \d+ \w+", "", plain(text))
    targets, spans = [], []
    for kind, rx in TARGET_PATTERNS:
        for m in rx.finditer(flat):
            if any(m.start() < e and s < m.end() for s, e in spans):
                continue
            skill = m.group("skill")
            n = parse_number(m.group("n"))
            if kind == "level" and not (2 <= n <= 99):
                continue
            spans.append((m.start(), m.end()))
            targets.append({"skill": skill.upper(), kind: n, "at": m.start()})
    targets.sort(key=lambda t: t.pop("at"))
    return targets


def flags_for(text: str):
    flat = plain(text)
    flags = []
    if re.search(r"\bHCIM\b", flat) and not flat.startswith("HCIM-"):
        flags.append("hcim")
    if re.search(r"\b(OPTIONEEL|Optioneel|optioneel)\b", flat):
        flags.append("optional")
    if "NIEUW" in flat:
        flags.append("new")
    return flags


def tags_for(text: str):
    tags = []
    for m in re.finditer(r"\[([^\]\[]{2,60})\]", text):
        for t in m.group(1).split(","):
            t = t.strip()
            if t and t not in tags:
                tags.append(t)
    return tags


# ---------------------------------------------------------------- blocks

def blocks_of(md: str):
    lines = md.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            yield ("heading", level, line[level:].strip())
            i += 1
        elif re.match(r"^\d+\.\s", line):
            items = []
            while i < len(lines) and re.match(r"^\d+\.\s", lines[i]):
                items.append(re.sub(r"^\d+\.\s+", "", lines[i]))
                i += 1
            yield ("olist", items)
        elif line.startswith("- "):
            items = []
            while i < len(lines) and lines[i].startswith("- "):
                items.append(lines[i][2:])
                i += 1
            yield ("ulist", items)
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split(" | ")]
                if not all(re.fullmatch(r"-{3,}", c) for c in cells):
                    rows.append(cells)
                i += 1
            yield ("table", rows)
        else:
            yield ("para", line.strip())
            i += 1


def slug(s: str) -> str:
    s = unicodedata.normalize("NFKD", plain(s).lower())
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = s.replace("→", "-").replace("–", "-")
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")[:40]


def step_id(section_id: str, text: str, seen: set) -> str:
    h = hashlib.sha1(norm(plain(text)).encode("utf-8")).hexdigest()[:8]
    sid = f"{section_id}:{h}"
    n = 2
    while sid in seen:
        sid = f"{section_id}:{h}-{n}"
        n += 1
    seen.add(sid)
    return sid


def convert(md: str, quest_names):
    qi = QuestIndex(quest_names)
    blocks = list(blocks_of(md))
    route = {"title": "", "version": "", "chapters": []}
    chapter = section = None
    group = None
    seen_ids = set()
    sec_ids = set()

    def new_section(title, subtitle="", withdraw=""):
        nonlocal section
        title_c, _ = clean_inline(title)
        sub_c, links = clean_inline(subtitle)
        wd_c, _ = clean_inline(withdraw)
        base = f"{chapter['id']}/{slug(title_c) or 'intro'}"
        sid, n = base, 2
        while sid in sec_ids:
            sid = f"{base}-{n}"
            n += 1
        sec_ids.add(sid)
        info = (not chapter["trackable"]) or title_c.startswith(INFO_SECTION_PREFIXES)
        section = {"id": sid, "title": plain(title_c), "subtitle": plain(sub_c), "group": group,
                   "withdraw": plain(wd_c), "info": info, "notes": [], "steps": [], "tables": [], "footer": [], "links": links}
        chapter["sections"].append(section)
        return section

    def ensure_section():
        if section is None:
            new_section("Overzicht")
        return section

    def add_step(raw):
        text, links = clean_inline(raw)
        s = ensure_section()
        step = {"id": step_id(s["id"], text, seen_ids), "text": text}
        comp = extract_completions(text, qi)
        if comp:
            step["complete"] = comp
        tg = extract_targets(text)
        if tg:
            step["targets"] = tg
        tags = tags_for(text)
        if tags:
            step["tags"] = tags
        fl = flags_for(text)
        if fl:
            step["flags"] = fl
        if links:
            step["links"] = links
        s["steps"].append(step)

    def add_note(raw, target=None):
        text, links = clean_inline(raw)
        sec = target or ensure_section()
        # notities na de stappen of tabel komen onderaan de sectie
        (sec["footer"] if sec["steps"] or sec["tables"] else sec["notes"]).append(text)
        if links:
            sec["links"].extend(links)

    for idx, b in enumerate(blocks):
        nxt = blocks[idx + 1] if idx + 1 < len(blocks) else None
        kind = b[0]
        if kind == "heading":
            level, title = b[1], b[2]
            if level == 1:
                route["title"] = title
                continue
            if level == 2:
                t, _ = clean_inline(title)
                chapter = {"id": slug(t.split("–")[0]) or slug(t), "title": t,
                           "trackable": t.startswith(TRACKABLE_CHAPTERS),
                           "parallel": t.startswith(PARALLEL_CHAPTERS),
                           "intro": [], "sections": []}
                route["chapters"].append(chapter)
                section, group = None, None
                continue
            # ### binnen een fase = groepslabel, anders gewone sectie
            if chapter["trackable"]:
                group, section = clean_inline(title)[0], None
            else:
                new_section(title)
            continue

        if chapter is None:
            if kind == "para" and re.match(r"^[A-Z][a-z]{2} \d", b[1]):
                route["version"] = b[1].split("·")[0].strip()
            continue

        if kind == "para":
            p = b[1]
            m = re.match(r"^\*\*(.+?)\*\*(.*)$", p)
            all_bold = bool(m) and not m.group(2).strip()
            heads_list = bool(m) and nxt is not None and nxt[0] in ("olist", "ulist", "table")
            if m and (all_bold or heads_list) and not p.startswith("**NIEUW"):
                rest = m.group(2).strip()
                rest = re.sub(r"^[–:\-]\s*", "", rest)
                title = m.group(1).rstrip(".")
                sub, withdraw = "", ""
                if title.startswith(("Bank", "Blok")):
                    # "Bank 0 – Lumbridge" / "Bank 1** – Coins, ..." / "Bank 25 – X** (items)"
                    if " – " in title:
                        title, sub = title.split(" – ", 1)
                    if rest:
                        withdraw = re.sub(r"^\((.*)\)$", r"\1", rest)
                else:
                    sub = rest
                new_section(title, sub, withdraw)
            elif section is None and chapter["sections"] == []:
                t, _ = clean_inline(p)
                if t.startswith("&#91;embedded") or t.startswith("[embedded"):
                    continue
                chapter["intro"].append(t)
            else:
                add_note(p)
            continue

        if kind == "olist":
            for it in b[1]:
                if section is not None and section["info"]:
                    add_note(it)
                else:
                    add_step(it)
            continue

        if kind == "ulist":
            for it in b[1]:
                if chapter["trackable"] and section is not None and not section["info"]:
                    add_step(it)
                else:
                    add_note(it)
            continue

        if kind == "table":
            rows = b[1]
            header, body = rows[0], rows[1:]
            s = ensure_section()
            if chapter["trackable"] and s["title"].startswith("Nieuwe content"):
                for r in body:
                    parts = [f"**{plain(r[0])}**"] + [f"{h}: {c}" for h, c in zip(header[1:], r[1:]) if c not in ("–", "")]
                    add_step(". ".join(parts))
            else:
                th = [clean_inline(h)[0] for h in header]
                tb = [[clean_inline(c)[0] for c in r] for r in body]
                s["tables"].append({"header": th, "rows": tb})
            continue

    # lege groepssecties en lege hoofdstukken opruimen
    for ch in route["chapters"]:
        ch["sections"] = [s for s in ch["sections"] if s["steps"] or s["notes"] or s["tables"] or s["footer"]]
        for s in ch["sections"]:
            if not s["links"]:
                del s["links"]
            if not s["tables"]:
                del s["tables"]
            if not s["footer"]:
                del s["footer"]
            if not s["group"]:
                del s["group"]
            if not s["subtitle"]:
                del s["subtitle"]
            if not s["withdraw"]:
                del s["withdraw"]
    return route, qi.misses


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    quests = Path(__file__).with_name("runelite_quests.txt").read_text(encoding="utf-8").splitlines()
    route, misses = convert(src.read_text(encoding="utf-8"), [q for q in quests if q.strip()])
    dst.write_text(json.dumps(route, ensure_ascii=False, indent=1), encoding="utf-8")
    steps = sum(len(s["steps"]) for c in route["chapters"] for s in c["sections"])
    sections = sum(len(c["sections"]) for c in route["chapters"])
    print(f"{len(route['chapters'])} hoofdstukken, {sections} secties, {steps} stappen -> {dst}")
    if misses:
        print("Geen quest gevonden voor:", "; ".join(sorted(misses)))


if __name__ == "__main__":
    main()
