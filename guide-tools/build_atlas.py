#!/usr/bin/env python3
"""Builds deterministic RuneLite Guide Overlay 2.0 imports from a HCIM route.

Data-safe: never invents completion events or NPC IDs. Pins only explicitly
named locations in the upstream RuneLite Guide Overlay place directory.
Generated guide step keys mirror the upstream JSON parser's Java hashCode key
for unsplit, sanitized text. To avoid false pins, we skip compound/split steps.
"""
import csv
import json
import re
import shutil
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'guide'
REPORTS = ROOT / 'guide-tools' / 'reports'
GUIDE_ID = 'hcim-atlas-2026'
ENDGAME_ID = 'hcim-atlas-endgame-2026'

# A curated selection from guide-overlay 2.0's place-locations-seed.json
# https://github.com/RunelitePlugin/guide-overlay (BSD-2-Clause)
# Each row: display name|x|y|plane|aliases separated by semicolons.
PLACE_SEED = '''
Lumbridge|3222|3218|0|lumbridge;lumby
Lumbridge Castle|3208|3218|0|lumbridge castle;lumbridge castle bank;lumbridge bank
Lumbridge Swamp|3202|3169|0|lumbridge swamp
Draynor Village|3105|3251|0|draynor village;draynor
Draynor Village Bank|3092|3245|0|draynor bank;draynor village bank
Wizards' Tower|3113|3179|0|wizard's tower;wizards' tower;wizards tower;wizard tower
Port Sarim|3047|3236|0|port sarim
Falador|2964|3378|0|falador;fally
Falador East Bank|3012|3355|0|falador east bank;fally east bank
Falador West Bank|2946|3369|0|falador west bank;fally west bank
Falador Park|2995|3375|0|falador park;fally park
Crafting Guild|2933|3295|0|crafting guild
Taverley|2894|3454|0|taverley
Burthorpe|2898|3553|0|burthorpe;games room;burthorpe games room
Warriors' Guild|2882|3548|0|warriors' guild;warriors guild
Varrock|3212|3424|0|varrock
Varrock East Bank|3253|3420|0|varrock east bank;varrock eastbank;east varrock bank
Varrock West Bank|3185|3441|0|varrock west bank;varrock westbank;west varrock bank
Varrock Palace|3210|3470|0|varrock palace
Lumber Yard|3308|3491|0|lumber yard;varrock lumber yard
Champions' Guild|3191|3363|0|champions' guild;champions guild
Cooks' Guild|3143|3444|0|cooks' guild;cooks guild;cooking guild
Edgeville|3087|3496|0|edgeville
Edgeville Monastery|3052|3488|0|edgeville monastery
Grand Exchange|3164|3487|0|grand exchange;the ge
Al Kharid|3293|3163|0|al kharid
Emir's Arena|3316|3235|0|emir's arena;emirs arena;duel arena
Shantay Pass|3304|3117|0|shantay pass
Karamja|2918|3176|0|karamja;musa point
Brimhaven|2774|3177|0|brimhaven
Catherby|2804|3434|0|catherby
Camelot|2757|3479|0|camelot
Seers' Village|2725|3491|0|seers' village;seers village
Fishing Guild|2611|3390|0|fishing guild
Barbarian Outpost|2520|3571|0|barbarian outpost;barbarian assault
Tree Gnome Stronghold|2465|3495|0|tree gnome stronghold;gnome stronghold;grand tree
Tree Gnome Village|2528|3167|0|tree gnome village;gnome village
Castle Wars|2440|3089|0|castle wars
The Outpost|2430|3348|0|the outpost;kandarin outpost
Ardougne|2661|3300|0|ardougne;east ardougne;ardy
Ardougne North Bank|2616|3332|0|ardougne north bank;ardy north bank;north ardougne bank
Ardougne South Bank|2652|3283|0|ardougne south bank;ardy south bank;south ardougne bank
Ardougne Zoo|2611|3277|0|ardougne zoo;ardy zoo
Ardougne Monastery|2606|3210|0|ardougne monastery;ardy monastery;kandarin monastery
Hemenster|2635|3437|0|hemenster
McGrubor's Wood|2658|3488|0|mcgrubor's wood;mcgrubors wood
Waterfall|2512|3493|0|waterfall quest area;baxtorian waterfall;waterfall quest
Hosidius|1744|3597|0|hosidius;hosidius square
Hosidius South|1808|3478|0|hosidius south;hosidius south minecart
Woodcutting Guild|1662|3505|0|woodcutting guild;wc guild
Kourend Castle|1643|3673|0|kourend castle
Port Piscarilius|1804|3739|0|port piscarilius;piscarilius;piscarillius
Lovakengj|1504|3816|0|lovakengj
Arceuus|1680|3744|0|arceuus
Shayzien|1480|3635|0|shayzien;shayzien encampment
Land's End|1504|3415|0|land's end;lands end
Farming Guild|1248|3726|0|farming guild
Wintertodt Camp|1624|3938|0|wintertodt;wintertodt camp
Ferox Enclave|3151|3635|0|ferox enclave;ferox
Fossil Island|3740|3808|0|fossil island
Digsite|3345|3445|0|digsite;the dig site
Fossil Island Mushroom Meadow|3674|3856|0|mushroom meadow
Fossil Island Verdant Valley|3764|3755|0|verdant valley
Zanaris|2412|4434|0|zanaris
Port Phasmatys|3660|3524|0|port phasmatys
Canifis|3496|3487|0|canifis
Morytania Slayer Tower|3428|3535|0|slayer tower;morytania slayer tower
Mort'ton|3488|3289|0|mort'ton;morton
Nardah|3424|2918|0|nardah
Pollnivneach|3359|2970|0|pollnivneach
Quarry|3166|2912|0|desert quarry;sandstone quarry
Rogues' Den|3046|4972|1|rogues' den;rogues den
H.A.M. Hideout|3166|3251|0|ham hideout;h a m hideout
Giants' Foundry|3361|3148|0|giants' foundry;giants foundry
Hunter Guild|1557|3048|0|hunter guild;hunters guild
Sunset Coast|1446|2988|0|sunset coast
Aldarin|1392|2940|0|aldarin
Civitas illa Fortis|1686|3113|0|civitas illa fortis;civitas;fortis;varlamore
Quetzacalli Gorge|1516|3228|0|quetzacalli gorge;quetzacalli
Cam Torum|1435|9596|0|cam torum
Keldagrim|2838|10197|0|keldagrim
Arceuus Library|1632|3838|0|arceuus library;library of arceuus
Draynor Manor|3108|3352|0|draynor manor
Ranging Guild|2654|3441|0|ranging guild;ranged guild
Port Khazard|2653|3166|0|port khazard;khazard port
Temple of Ikov|2676|3404|0|temple of ikov
Varrock Sewers|3236|3458|0|varrock sewers;varrock sewer
Smoke Dungeon|3309|2962|0|smoke dungeon;smokey well;smoky well
Harmony Island|3797|2866|0|harmony island
Lletya|2330|3172|0|lletya;lletya bank
Mos Le'Harmless|3682|2951|0|mos le'harmless;mos le harmless
Rellekka|2639|3710|0|rellekka;relleka
Jatizso|2420|3780|0|jatizso;jatiso
Trollheim|2891|3678|0|trollheim
Theatre of Blood|3676|3219|0|theatre of blood;theater of blood
Tombs of Amascut|3357|2711|0|tombs of amascut
Mage Training Arena|3363|3317|0|mage training arena;magic training arena
Tempoross Cove|3137|2840|0|tempoross;tempoross cove
Soul Wars|2210|2857|0|soul wars;soul wars lobby
Last Man Standing|3151|3635|0|last man standing;lms lobby
Blast Furnace|1940|4963|0|blast furnace
Barbarian Village|3082|3421|0|barbarian village;gunnarsgrunn
Melzar's Maze|2931|3251|0|melzar's maze;melzars maze
Bandit Camp|3173|2982|0|bandit camp;desert bandit camp
Nature Grotto|3441|3337|0|nature grotto
Haunted Mine|3440|3232|0|haunted mine
Dwarven Mine|3019|3450|0|dwarven mine
Chambers of Xeric|1254|3565|0|chambers of xeric
Fortis Colosseum|1799|3108|0|fortis colosseum;fortis colloseum;colosseum entrance
Port Tyras|2142|3122|0|port tyras
Royal Titans|2995|3112|0|royal titans;royal titan entrance
Yama / Chasm of Fire|1452|3659|0|yama;yama's domain;yamas domain;chasm of fire
Moons of Peril|1435|9596|0|moons of peril;perilous moons;neypotzli
'''

PLACES=[]
for raw in PLACE_SEED.splitlines():
    if not raw.strip(): continue
    name,x,y,plane,aliases=raw.split('|',4)
    PLACES.append(dict(name=name,x=int(x),y=int(y),plane=int(plane),aliases=[a.strip().lower() for a in aliases.split(';')]))

# Ignore guide bracket annotations so quest names aren't mistaken for places.
QUEST_BRACKETS=re.compile(r'\[[^]]{1,100}\]')
NOTE=re.compile(r'^(?:Routeadvies|HCIM:|Note:|Warning:|IMPORTANT|Let op|OPTIONAL|optional|If you |\* )',re.I)
TRAVEL=re.compile(r'^(?:(?:Go|Head|Run|Walk|Return|Travel|Sail|Teleport|Bank|Rebank|Enter|Leave|Take (?:boat|ship|minecart|portal|carpet)|Minigame teleport|Charter|Go up|Go down)\b)', re.I)
TASK_COMPOUND=re.compile(r'\b(?:talk to|speak to|buy|kill|trade|pickpocket|collect|withdraw|take everything|loot|fill|give |make |deposit|complete|start|continue)\b',re.I)
RISK=re.compile(r'\b(?:wilderness|stronghold of security|revs?|dragon slayer|pyramid|wintertodt|zulrah|vorkath|gauntlet|colosseum|inferno|theatre of blood|tombs of amascut|raid|yama|doom|venenatis|vet.?ion|calvar|callisto|dagannoth|hydra|nightmare|sire|leviathan|vardorvis|whisperer|duke sucellus)\b', re.I)
# Do not override the plugin's dynamic scene tracking for NPCs or multi-task rows.

def normalize(text):
    return re.sub(r'\s+', ' ', re.sub(r'</?[^>]+>', '',text).replace('<','').replace('>','')).strip()

def javahash(text):
    h=0
    for ch in text.encode('utf-16-be'):
        pass
    units=text.encode('utf-16-be')
    for i in range(0,len(units),2): h=(31*h+int.from_bytes(units[i:i+2],'big'))&0xffffffff
    return format(h,'x')

def candidate_pin(text):
    txt=normalize(text)
    if NOTE.search(txt) or not TRAVEL.search(txt) or len(txt)>170: return (None,'not_safe_to_pin')
    if re.search(r'[.!?]\s+\S',txt): return (None,'multi_sentence')
    if re.search(r'\b(?:wilderness|lever to the wilderness|abyss|revenue cave|ice warriors|skeletons|mage bank)\b',txt,re.I):return (None,'dangerous_area')
    clean=QUEST_BRACKETS.sub('',txt).lower()
    # A bank marker may still be useful for 'bank + deposit' instructions;
    # arrival never completes the compound bank action.
    is_bank=bool(re.match(r'^(?:bank|rebank)\b',clean))
    if TASK_COMPOUND.search(clean) and not is_bank:return (None,'compound_task')
    if is_bank and re.search(r'\b(?:withdraw|buy|take|collect|kill|talk|start|complete|continue)\b',clean):
        return (None,'compound_bank_action')
    if re.search(r'\b(?:avoid|do not|beware|careful of|dangerous)\b',clean):return (None,'safety_avoidance')
    # The very short destination-only bank steps get an explicit tile.
    matches=[]
    for place in PLACES:
        for alias in place['aliases']:
            for match in re.finditer(r'(?<![a-z])'+re.escape(alias)+r'(?![a-z])',clean):
                matches.append((match.start(),len(alias),place))
    if not matches:return (None,'unresolved_location')
    # Prefer final explicit place; ties favor the most specific alias.
    matches.sort(key=lambda m:(m[0],m[1]))
    place=matches[-1][2].copy()
    chosen_start,chosen_length,_=matches[-1]
    unmatched_suffix=clean[chosen_start+chosen_length:].strip()
    if re.match(r'^(?:church|chuch|loom|altar|tower|castle|dungeon|mine|sewers|stairs|shop|furnace|forge|gate|wall|dock|docks|building|inn|tavern)\b',unmatched_suffix):
        return (None,'sub_location_not_mapped')
    if is_bank and place['name'] in ('Falador','Varrock','Ardougne','Seers\' Village','Catherby'):
        return (None,'unspecified_bank')
    if is_bank and place['name']=='Draynor Village':
        place=next(p.copy() for p in PLACES if p['name']=='Draynor Village Bank')
    if is_bank and place['name']=='Lumbridge':
        place=next(p.copy() for p in PLACES if p['name']=='Lumbridge Castle')
    if len(matches)>1:
        selected_start,selected_length,_=matches[-1]
        other_nonoverlap=[m for m in matches[:-1]
                          if not (selected_start<=m[0] and m[0]+m[1]<=selected_start+selected_length)]
        if other_nonoverlap:
            return (None,'multiple_locations')
    # BANK EXCEPTION: The Lumbridge bank is on the second floor (plane=2);
    # Guide Overlay's generic named-place seed describes only ground level.
    if place['name']=='Lumbridge Castle' and re.search(r'(?:bank|top floor|top of lumbridge castle|upstairs)', clean):
        place['plane']=2
        place['x']=3208
        place['y']=3219
        place['name']='Lumbridge Castle bank (2e verdieping)'
    return (place,'explicit_location')

def categorize(t):
    if NOTE.search(t): return 'note/handmatig'
    if re.search(r'^Withdraw:|^Deposit |^Bank |^Bank at',t,re.I):return 'bank-items of bankactie'
    if re.search(r'\b(?:[2-9]\d?|99)\s+(?:Agility|Attack|Defence|Ranged|Magic|Strength|Herblore|Prayer|Woodcutting|Thieving|Slayer|Fishing|Crafting|Sailing|Mining|Hunter|Runecraft|Farming|Construction|Firemaking|Cooking|Fletching|Smithing)\b',t,re.I):return 'skilldoel; controleer autocheck'
    if re.search(r'^Complete [A-Z]|^Start [A-Z]',t):return 'quest/event; controleer autocheck'
    if TRAVEL.search(t):return 'route/locatie'
    if re.search(r'\b(?:Talk to|Speak to|Pickpocket|Trade|Buy |Kill |Collect|Take |Loot|Search|Mine)\b',t,re.I):return 'NPC/object/item; controleer highlight'
    return 'handmatig of contextueel'

def steps_in(data):
    for chapter_num,chapter in enumerate(data['chapters'],1):
        for section_num,section in enumerate(chapter['sections'],1):
            seen=Counter()
            for si,step in enumerate(section['steps'],1):
                text=normalize(''.join(z.get('text','') for z in step.get('content',[]) if isinstance(z,dict)))
                occur=seen[text];seen[text]+=1
                # upstream splits long paragraphs into multiple step keys; skip these for pins.
                key=f'C{chapter_num}.S{section_num}#{javahash(text)}#{occur}'
                yield chapter,section,si,step,text,key

def build(source_file,dest_file,pins_file,audit_file,guide_id):
    data=json.loads(source_file.read_text('utf8'))
    data['title']='HCIM Atlas 2026 — In-game route + endgame' if guide_id==GUIDE_ID else 'HCIM Atlas Endgame 2026'
    dest_file.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
    pins={'schemaVersion':1,'guides':{guide_id:{}}}
    audit=[]; seenkeys=set(); n=0
    for ch,sec,idx,step,t,key in steps_in(data):
        n+=1
        if key in seenkeys: raise ValueError('Duplicate key: '+key)
        seenkeys.add(key)
        place,reason=candidate_pin(t)
        if place:
            pins['guides'][guide_id][key]={'waypoints':[{'id':'manual:'+guide_id+':'+key+':0','label':place['name'],'x':place['x'],'y':place['y'],'plane':place['plane'],'radius':5}]}
        audit.append({'chapter':ch['title'],'section':sec['title'],'step':idx,'key':key,'type':categorize(t),
                      'risk_scan':'review_handmatig' if RISK.search(t) else '',
                      'pin_status':'gecontroleerde plaatsbron' if place else reason,
                      'destination':place['name'] if place else '',
                      'worldpoint':f"{place['x']},{place['y']},{place['plane']}" if place else '',
                      'text':t})
    pins_file.write_text(json.dumps(pins,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    if len(pins_file.read_text('utf8'))>120000:raise ValueError('Too many pins for Guide Overlay config sync')
    with audit_file.open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=list(audit[0]));w.writeheader();w.writerows(audit)
    counts=Counter(a['pin_status'] for a in audit)
    return {'steps':n,'chapters':len(data['chapters']),'sections':sum(len(x['sections']) for x in data['chapters']),
            'pins':len(pins['guides'][guide_id]),'pin_bytes':pins_file.stat().st_size,'risk_flags':sum(bool(a['risk_scan']) for a in audit),'pin_reasons':dict(counts)}

if __name__=='__main__':
    ROOT.mkdir(parents=True,exist_ok=True)
    report={}
    for label,src_name,out_name,gid in [
        ('volledige route','OSRS_HCIM_GuideOverlay_VOLLEDIGE_ROUTE_2026.json','HCIM_ATLAS_VOLLEDIGE_ROUTE.json',GUIDE_ID),
        ('endgame','OSRS_HCIM_GuideOverlay_ENDGAME_2026.json','HCIM_ATLAS_ENDGAME.json',ENDGAME_ID)]:
        source=DATA/src_name
        if not source.exists(): raise FileNotFoundError(source)
        tag='VOLLEDIGE_ROUTE' if gid==GUIDE_ID else 'ENDGAME'
        report[label]=build(source,DATA/out_name,DATA/f'HCIM_ATLAS_PINNEN_{tag}.json',REPORTS/f'HCIM_ATLAS_AUDIT_{tag}.csv',gid)
    (REPORTS/'HCIM_ATLAS_COVERAGE.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf8')
    print(json.dumps(report,indent=2,ensure_ascii=False))
