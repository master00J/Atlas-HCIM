#!/usr/bin/env python3
"""Builds the English 2026 Guide Overlay route from the B0aty HCIM Guide V3 base.

Input
  guide/source/B0aty_HCIM_Guide_V3_base.json  Episodes 1-12 of B0aty's HCIM Guide V3
                                             (wiki route, as imported into Atlas)
  guide/source/endgame_2026_en.md            English endgame chapters (markdown)

Output
  guide/OSRS_HCIM_GuideOverlay_ENDGAME_2026.json         endgame only
  guide/OSRS_HCIM_GuideOverlay_VOLLEDIGE_ROUTE_2026.json patched Episodes 1-12 + endgame

Run guide-tools/build_atlas.py afterwards to regenerate the Atlas imports and pins.

Every edit below is anchored on the exact B0aty text and fails loudly when the
anchor is missing or ambiguous, so a changed base can never be patched silently
in the wrong place. Step text is written for RunelitePlugin/guide-overlay's
English parser: "Talk to <NPC>", "start/complete <Quest>", "to <level> <Skill>",
one action per sentence, dialogue numbers only where they were verified.

Sources for the 2026 changes: the Dutch macroquest guide in this repository's
history and the OSRS Wiki pages for Quest Month (3 Nov 2026), Mudfoot's Big
Adventure, The Writing's on the Wall, Pandemonium, Troubled Tortugans, The Red
Reef, Fallen From Grace and The Fractured Archive (CC BY-NC-SA 3.0).
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'guide'
BASE = DATA / 'source' / 'B0aty_HCIM_Guide_V3_base.json'
ENDGAME_MD = DATA / 'source' / 'endgame_2026_en.md'
ENDGAME_OUT = DATA / 'OSRS_HCIM_GuideOverlay_ENDGAME_2026.json'
ROUTE_OUT = DATA / 'OSRS_HCIM_GuideOverlay_VOLLEDIGE_ROUTE_2026.json'

ROUTE_TITLE = 'HCIM Atlas 2026 — In-game route + endgame'
ENDGAME_TITLE = 'HCIM Atlas Endgame 2026'

# Guide Overlay starts a secondary comma split above this length.
MAX_STEP_CHARS = 300


def step(text):
    return {'content': [{'text': text}]}


def text_of(st):
    return ''.join(run.get('text', '') for run in st.get('content', []) if isinstance(run, dict))


class Route:
    def __init__(self, data):
        self.data = data

    def chapter(self, prefix):
        found = [c for c in self.data['chapters'] if c['title'].startswith(prefix)]
        if len(found) != 1:
            raise KeyError(f'chapter {prefix!r}: {len(found)} matches')
        return found[0]

    def section(self, chapter_prefix, title):
        chapter = self.chapter(chapter_prefix)
        found = [s for s in chapter['sections'] if s['title'] == title]
        if len(found) != 1:
            raise KeyError(f'{chapter_prefix} / {title!r}: {len(found)} matches')
        return found[0]

    @staticmethod
    def index(section, anchor):
        """Index of the one step whose text equals anchor, or starts with it when
        anchor ends with '…'."""
        if anchor.endswith('…'):
            hits = [i for i, st in enumerate(section['steps']) if text_of(st).startswith(anchor[:-1])]
        else:
            hits = [i for i, st in enumerate(section['steps']) if text_of(st) == anchor]
        if len(hits) != 1:
            raise KeyError(f'{section["title"]}: anchor {anchor!r} matched {len(hits)} steps')
        return hits[0]

    def replace(self, ch, sec, anchor, new_text):
        s = self.section(ch, sec)
        s['steps'][self.index(s, anchor)] = step(new_text)

    def delete(self, ch, sec, *anchors):
        s = self.section(ch, sec)
        for anchor in anchors:
            del s['steps'][self.index(s, anchor)]

    def insert_after(self, ch, sec, anchor, texts):
        s = self.section(ch, sec)
        i = self.index(s, anchor) + 1
        s['steps'][i:i] = [step(t) for t in texts]

    def insert_before(self, ch, sec, anchor, texts):
        s = self.section(ch, sec)
        i = self.index(s, anchor)
        s['steps'][i:i] = [step(t) for t in texts]

    def set_steps(self, ch, sec, texts):
        self.section(ch, sec)['steps'] = [step(t) for t in texts]

    def insert_section_after(self, ch, after_title, title, texts):
        chapter = self.chapter(ch)
        idx = [i for i, s in enumerate(chapter['sections']) if s['title'] == after_title]
        if len(idx) != 1:
            raise KeyError(f'{ch} / {after_title!r}: {len(idx)} matches')
        chapter['sections'].insert(idx[0] + 1, {'title': title, 'steps': [step(t) for t in texts]})

    def insert_section_before(self, ch, before_title, title, texts):
        chapter = self.chapter(ch)
        idx = [i for i, s in enumerate(chapter['sections']) if s['title'] == before_title]
        if len(idx) != 1:
            raise KeyError(f'{ch} / {before_title!r}: {len(idx)} matches')
        chapter['sections'].insert(idx[0], {'title': title, 'steps': [step(t) for t in texts]})


E1, E2, E3, E4, E5, E6, E7, E8 = (f'Episode {n} ' for n in range(1, 9))
E10, E11, E12 = 'Episode 10 ', 'Episode 11 ', 'Episode 12 '

LAMP_PRAYER = 'on Prayer (Ranged once you have 43 Prayer)'


def episode_1(r):
    r.set_steps(E1, 'Starting Out', [
        "Route note — 2026 update: this is B0aty's HCIM Guide V3 updated for Sailing and Quest Month (November 2026); the combat tutors are gone, so Mudfoot's Big Adventure now gives your first weapons, runes and 2,000 combat XP.",
        'All Settings -> Disable level-up',
        "Talk to the Cook in the Lumbridge Castle kitchen and start Cook's Assistant (keep the Pot and Bucket from Tutorial Island)",
        "Talk to Captain Harlan outside the General Store north of the castle and start Mudfoot's Big Adventure (1)",
        'Talk to Cassius by the pond south of the windmill and start The Ides of Milk',
        'Cross the River Lum and talk to Gillie Groats in the cow field just north of the gate [The Ides of Milk]',
        'Talk to Seth Groats in the farmhouse next to the chicken coop [The Ides of Milk]',
        'Search the shelf on the south wall of the farmhouse for The Groats Principles [The Ides of Milk]',
        "Take an Egg [Cook's Assistant]",
        "Milk the Dairy cow in the cow field for a Bucket of milk [Cook's Assistant]",
        "Talk to Mudfoot by the building south of the cow field, without a pet following you [Mudfoot's Big Adventure]",
        "Confront the outlaw walking outside the Lumbridge General Store [Mudfoot's Big Adventure]",
        "Talk to Mudfoot again at the chicken field south of the Lumbridge windmill [Mudfoot's Big Adventure]",
        "Kill the Ranger Outlaw (level 6) with the Bronze dagger on stab and take his Shortbow & 80 Bronze arrows [Mudfoot's Big Adventure]",
        'Talk to Cassius to hand over The Groats Principles and get a Milk sample [The Ides of Milk]',
        'Drink the Milk sample (1 damage, no poison) and talk to Cassius again for a new Milk sample [The Ides of Milk]',
        "Pick Grain next to the windmill, put it in the hopper on the top floor and pull the lever [Cook's Assistant]",
        "Use the Pot on the flour bin on the ground floor for a Pot of flour [Cook's Assistant]",
        "Wield the Shortbow and kill the Mage Outlaw south of the H.A.M. Hideout, then take his Staff, Air runes & Mind runes [Mudfoot's Big Adventure]",
        "Wield the Staff and go to the Lumbridge graveyard behind the church; if you left the fight area, fetch Mudfoot from outside the H.A.M. Hideout first",
        "Kill the Warrior Outlaw with Wind Strike, because he prays Protect from Melee after your first hit, and take his Iron scimitar & Iron kiteshield [Mudfoot's Big Adventure]",
        'Talk to Father Aereck in the church and start The Restless Ghost',
        "Talk to Captain Harlan (1) and complete Mudfoot's Big Adventure",
        'Use all 4 combat lamps from Mudfoot on Hitpoints',
        'Sell the Bronze dagger, Bronze sword, Wooden shield & one Shortbow to the General Store and buy a Spade, Chisel & Hammer',
        "Talk to the Cook and complete Cook's Assistant",
        'Pickpocket a man/woman [Lumbridge Easy Diary]',
        'Head East & Start X Marks the Spot on Veos (2,1) & complete the first step (Install Quest Helper plug in on Runelite)',
        'Check playtime on Hans [Lumbridge Easy Diary]',
        'Go upstairs and talk to Duke Horacio to start Rune Mysteries',
        'Talk to Duke Horacio again and give him the Milk sample [The Ides of Milk]',
        'Go upstairs again and Collect 4x logs on the top floor [Tree Gnome Village]',
        'Bank at Lumbridge castle top floor and deposit all',
        'Keep the Shrimps in your bank for later [Family Crest Quest]',
        "Keep Mudfoot's Shortbow & all Bronze arrows in your bank for later [Underground Pass]",
        'Move Coins into first bank slot & Runes to the top slots. Click the lock to Always set Placeholders. Set Bank pin for Count Check randoms or delete the pin from Tutorial Island.',
    ])

    r.replace(E1, 'Bank 1', 'Routeadvies — Lumbridge → Wizards’ Tower → Draynor…',
              "Route note — Lumbridge, Wizards' Tower and Draynor loop: wear the leather gloves and boots; the goal is to collect quest items in one south-west loop, not to finish whole quests yet.")
    r.replace(E1, 'Bank 2', 'Routeadvies — Draynor → Port Sarim…',
              'Route note — Draynor, Port Sarim, Zeah, Lumbridge and the Stronghold: the Stronghold of Security is NOT a safe death, so bring extra food if your HP is low and leave when you run short of HP or run energy; it is fast early money, not a required HCIM step.')
    r.replace(E1, 'Bank 2', 'Talk to Count Check and be teleported to Stronghold of Security (3,1)',
              'Talk to Count Check and choose the teleport to the Stronghold of Security (his dialogue was rewritten in November 2026)')
    r.replace(E1, 'Bank 3', 'Routeadvies — Varrock-circuit…',
              "Route note — Varrock circuit with many quest starts: buy an air staff and combine Demon Slayer, Shield of Arrav, Gertrude's Cat, Children of the Sun, Priest in Peril and Daddy's Home; keep every quest item you make.")
    r.replace(E1, 'Bank 4', 'Routeadvies — Varrock: Daddy’s Home…',
              "Route note — Varrock, Daddy's Home and materials: combine the Construction unlock, plank making, rune shopping and Varrock diary steps, and check your free inventory slots for logs and planks.")
    r.replace(E1, 'Bank 5', 'Routeadvies — Varlamore…',
              "Route note — Varlamore materials in one visit: the Hunter's Guild gives new quest items and an early sellable resource; prices and stock can change.")

    r.insert_after(E1, 'Bank 9', 'Make 454 Headless Arrows', [
        "Route note — Pandemonium (Sailing, November 2025) has no requirements; you need 2 free inventory slots and empty hands, and it unlocks Sailing, a raft and the Captain's log.",
        'Unequip your weapon and the Chronicle, then talk to Will or Anne on the dock east of the Port Sarim Food Store to start Pandemonium',
        'Complete Pandemonium (follow Quest Helper)',
        'Wield the Chronicle again',
        'OPTIONAL: once you have 4 Sailing, do one courier task between Port Sarim and The Pandemonium',
    ])

    r.replace(E1, 'Bank 11', 'Routeadvies — Edgeville → Wilderness → Ardougne…',
              'Route note — Edgeville, Wilderness and Ardougne: HCIM WARNING, this segment uses the Wilderness and the lever, and a normal death to monsters or PKers costs your status; skip it if you want and do the diary and miniquest steps later.')
    r.replace(E1, 'Bank 12', 'Routeadvies — Knight’s Sword: blurite…',
              "Route note — Knight's Sword blurite: Ice Warriors can kill an early account quickly, so use a proven safespot, keep a teleport ready and never mine while you are taking damage.")
    r.replace(E1, 'Bank 13', 'Routeadvies — The Tourist Trap…',
              'Route note — The Tourist Trap: the Mercenary Captain needs safe positioning, and the fastest safespot route is not automatically the best choice if you are inexperienced.')


def episode_2(r):
    r.delete(E2, 'Bank 26', 'Collect an egg', 'Talk to Cassius to start The Ides of Milk/Quick guide (1)')

    r.insert_after(E2, 'Bank 28', 'Complete the Barcrawl at Port Sarim', [
        'Talk to Veos to reclaim your X Marks the Spot lamp and use it on Herblore',
    ])
    r.insert_after(E2, 'Bank 28', 'Bank West side of the Square and deposit everything', [
        "Route note — The Writing's on the Wall (Quest Month, November 2026) needs 22 Agility and 20 Thieving (not boostable), has no fights and gives 2,500 Agility, 2,500 Thieving and the Amulet of sight (+10 Ranged attack) before the fruit stalls and Brimhaven.",
        "Run south-west to Land's End and talk to Betty outside the northernmost house to start The Writing's on the Wall",
        "Talk to Sergeant Hudson and inspect the body parts on the bed, the shelves and the blood on the wall [The Writing's on the Wall]",
        "Talk to Lan the Butcher in the house south-west of the bank, search his bed and use the bloody knife on him [The Writing's on the Wall]",
        "Talk to Hugor in the eastern house, inspect your clues and talk to Betty and Sergeant Hudson again [The Writing's on the Wall]",
        "Run north-east and talk to Betty by the house north-west of the Tithe Farm [The Writing's on the Wall]",
        "Climb through the west window, open the door for Betty and search the basement and the bookshelf upstairs [The Writing's on the Wall]",
        "Search the compost bin north of the house and inspect the head [The Writing's on the Wall]",
        "Talk to Joyce in the potato field west of the house and the monk just south of it [The Writing's on the Wall]",
        "Talk to Betty again and accept her transport to Port Piscarilius [The Writing's on the Wall]",
        "Remove the plank north of the prison, climb the wall and sneak into the north-western house past the guards [The Writing's on the Wall]",
        "Head to the War Tent in the Shayzien Encampment [The Writing's on the Wall]",
        "Watch the cutscene and investigate the next victim's house: shelves, body and blood on the wall [The Writing's on the Wall]",
        "Talk to Kyle to the south and then to Sergeant Hudson [The Writing's on the Wall]",
        "Inspect the plant between the two trees on the island west of Watson's house in Hosidius and go down the trapdoor [The Writing's on the Wall]",
        "Complete The Writing's on the Wall and keep the Amulet of sight for your Ranged training",
        'Return to the Hosidius bank and deposit everything',
    ])
    r.replace(E2, 'Bank 28', 'Head East & thieve from the Hosidius fruit stall until 34512 Thieving Experience…',
              "Head East & thieve from the Hosidius fruit stall until 31,512 Thieving Experience (Twilight's Promise in Bank 29 adds the other 3,000). Bank 5x Redberries, all Golovanova's + Strange fruit and 1x Jangerberries [Fairytale Part I, possible item for Malignius Mortifer]")

    r.replace(E2, 'Bank 29', 'Withdraw: Coins, Enchanted Quill, Enchanted Scroll, Stew, Pickaxe, Chronicle, Axe, Tinderbox (8 Inventory Slots)',
              'Withdraw: Coins, Enchanted Quill, Enchanted Scroll, Stew, Pickaxe, Chronicle, Axe, Tinderbox, Shortbow, Bronze Arrows, Air Runes, Mind Runes, 10x Food (22 Inventory Slots)')
    r.insert_after(E2, 'Bank 29', 'Head to Varlamore', [
        "Route note — Twilight's Promise moved here from Episode 6: it gives 3,000 Thieving, the Civitas illa Fortis Teleport and the Quetzal Transport System, which shortens this trip and every later Varlamore trip.",
        "Talk to Ennius Tullus or Furia Tullus in front of the Sunrise Palace and start Twilight's Promise",
        "Let the guards kill the level 34 cultists in the Teomat [Twilight's Promise]",
        "HCIM: Mezan in the Colosseum is level 81 with a max hit of 8; switch between Magic and Ranged every 3 attacks so he never prays, and eat early [Twilight's Promise]",
        "Complete Twilight's Promise",
    ])

    r.replace(E2, 'Bank 30', 'Routeadvies — Agility in Brimhaven…',
              'Route note — Brimhaven Agility: watch out for repeated damage; this is a long XP grind, so keep checking your HP and have food ready.')
    r.replace(E2, 'Bank 34', 'Routeadvies — Agility Pyramid…',
              "Route note — Agility Pyramid for a big cash buffer: B0aty's efficiency goal is over 1 million GP while training Agility; it is a target, not a guaranteed amount.")
    r.replace(E2, 'Bank 37', 'Routeadvies — Blackjacking…',
              'Route note — Blackjacking to 50 Thieving: blackjacking can be dangerous at low HP, so keep an eye on your healing and avoid long AFK sessions.')
    r.replace(E2, 'Bank 38', 'Routeadvies — Jungle Potion + Shilo Village…',
              'Route note — Jungle Potion and Shilo Village: your escape is sometimes limited in tunnels and boss fights, so check your HP and keep some food slots before you go in.')

    r.insert_after(E2, 'Bank 42', 'At this point in the guide. Complete Shield of Arrav whenever convenient.', [
        'Shield of Arrav now also gives a 500 XP lamp: use it on Herblore',
    ])
    r.replace(E2, 'Bank 45', 'Withdraw: Adamant Scimitar, Egg, 2x Flour, Milk, Bucket of Water, Rotten Tomato, Greenmans Ale, Ashes, Fruit Blast, Eye of Newt (9 Inventory Slots)',
              'Withdraw: Adamant Scimitar, Flour, Bucket of Water, Rotten Tomato, Greenmans Ale, Ashes, Fruit Blast, Eye of Newt (7 Inventory Slots)')
    r.delete(E2, 'Bank 45', 'Complete Cooks Assistant (1,1)')
    r.insert_after(E2, 'Bank 50', 'Complete Ernest the Chicken & Vampyre Slayer when getting the skull', [
        'Use the 500 XP lamp from Ernest the Chicken on Herblore',
    ])
    r.insert_after(E2, 'Bank 51', 'BIS, AIR (the others are passive)', [
        'Save your most used fairy rings as Favourites now (right-click Favourites teleports in one click): AKR, AJR, ALQ, BKR, BLP, CIR, CIS, CKS, DJR, DKS and DLQ',
    ])

    r.delete(E2, 'Bank 52',
             'Talk to Gillie Groats (3) [Ides of Milk]',
             'Talk to Seth Groats (3) [Ides of Milk]',
             "Search the shelves in Seth's farmhouse for The groats principles book",
             'Head west and talk to Cassius to get a milk sample [The Ides of Milk]',
             'Drink the milk sample and talk to Cassius again [The Ides of Milk]')
    r.replace(E2, 'Bank 52', 'Talk to Seth Groats (2) [One Small Favour]', 'Talk to Seth Groats [One Small Favour]')
    r.replace(E2, 'Bank 52', 'Make sure that quest steps for One Small Favour and The Ides of Milk are completed after talking to Seth Groats',
              'Make sure the One Small Favour step is completed after talking to Seth Groats (The Ides of Milk was already advanced in Starting Out)')

    r.replace(E2, 'Bank 53', 'Powermine to 50 Mining at the Iron Ore',
              "Powermine Iron Ore until 97,833 Mining XP (level 49); Family Crest (+2,000) and Tale of the Righteous (+1,500) take you to 50 before Heroes' Quest needs it")

    r.insert_section_after(E2, 'Bank 60', 'Bank 60B', [
        "Route note — Tower of Life moved here from Bank 155: its 1,000 Construction XP saves Mahogany Homes contracts in Bank 61 and its 500 Crafting XP saves glass in Bank 68.",
        'Withdraw: Coins, Hammer, Saw, Beer, Teleport Runes (5 Inventory Slots)',
        'Tower of Life needs 10 Construction (not boostable); if you are lower, do Beginner Mahogany Homes contracts from Bank 61 until 10 Construction first',
        'Ardy Cloak to the Monastery and talk to Effigy just east of it to start Tower of Life',
        'Complete Tower of Life (1,000 Construction, 500 Crafting and 500 Thieving XP)',
        'Bank anywhere and deposit all',
    ])


def episode_3(r):
    r.insert_section_after(E3, 'Bank 75', 'Sailing A — 1 to 30 Sailing (optional)', [
        'Route note — Sailing block A is optional (about 3 to 4 hours): Sailing is not a Song of the Elves requirement, but shipbuilding gives Construction XP and port tasks give coins and sawmill coupons; skip to Bank 76 for the fastest route to Song of the Elves.',
        'Withdraw: Coins, Steel Bar, Hammer, Redberry Pie, Teleport Runes (5 Inventory Slots)',
        "Take two courier tasks at a time to the same port, or tasks that lie on each other's route",
        'Chart every sea charting spot you pass for one-off XP and faster rapids later',
        'Train to 12 Sailing with courier tasks and sea charting',
        "Talk to Squawking Steve Beanie in the pub on The Pandemonium and start Prying Times (needs 30 Smithing and The Knight's Sword)",
        'Complete Prying Times (crowbar, 800 Sailing, 1,000 Smithing and 25 oak sawmill coupons)',
        'Train to 15 Sailing',
        'Buy the skiff (15,000 coins) and build a cargo hold and a bronze salvaging hook',
        "Sort salvage at a salvaging station instead of dropping it: every tier can give the Sailors' amulet (cheap island teleports) and a 1/3,000 museum find with kudos and antique lamps (lamps on Herblore)",
        'Build the iron helm at 17 Sailing and 14 Construction (6 iron bars and 3 oak planks)',
        'Train to 22 Sailing',
        'Talk to Arhein on the Catherby docks and start Current Affairs (needs 10 Fishing and 50 coins)',
        'Complete Current Affairs and finish with your boat docked in Catherby (1,400 Sailing, 1,000 Fishing, the current duck and 25 oak sawmill coupons)',
        'Build the oak mast & linen sails at 24 Sailing, 11 Construction and 12 Crafting with the oak sawmill coupons',
        'Train to 30 Sailing',
        'Do The Tempor Tantrum (Barracuda Trials) until the Marlin rank',
        'Bank anywhere and deposit all',
    ])

    r.replace(E3, 'Bank 81', 'Make sure to do the steps for Dragon Slayer and Ides of Milk here',
              'Make sure to do the steps for Dragon Slayer here')
    r.delete(E3, 'Bank 81', 'Talk to Duke Horacio [The Ides of Milk]')
    r.insert_after(E3, 'Bank 81', 'Talk to Willow (Below Ice Mountain) (1) [Below Ice Mountain]', [
        'OPTIONAL: A Ruff Situation (15 Crafting) starts at the dog shelter near Ice Mountain; the grown dog digs up clue steps for you',
    ])
    r.insert_after(E3, 'Bank 81', 'Continue & Complete Black Knights Fortress', [
        f"Use the 2 Black Knights' Fortress lamps (1,000 combat XP each) {LAMP_PRAYER}",
    ])
    r.insert_after(E3, 'Bank 87', 'Complete Demon Slayer', [
        f'Talk to Aris in Varrock Square to claim the 2 Demon Slayer lamps (1,000 combat XP each) and use them {LAMP_PRAYER}',
    ])


def episode_4(r):
    r.delete(E4, 'Bank 105B', 'Complete The pandemonium quest while you are here to get sailing xp from Tears of Guthix')
    r.insert_after(E4, 'Bank 118', 'Head South to Library', [
        'Kourend Library shortcut: with 52 Agility you can jump down from the middle floor and with 62 Agility from the top floor',
    ])
    r.insert_after(E4, 'Bank 130', 'Open the pen in the north-east corner of the cow farm and fight the bull [The Ides of Milk]', [
        'HCIM: Brutus\'s special attacks scale with your HP and cannot be prayed against; step 2 tiles sideways when he snorts or growls and leave through the south-west gate if it goes wrong',
    ])
    r.insert_after(E4, 'Bank 130', 'Speak to Cassius to complete the Ides of Milk', [
        'Use the 1,000 XP combat lamp from The Ides of Milk on Prayer or Ranged',
    ])
    r.insert_after(E4, 'Bank 144', 'Talk to Romeo to complete Romeo & Juliet', [
        'Use the 500 XP lamp from Romeo & Juliet on Herblore',
    ])


def episode_5(r):
    r.replace(E5, 'Bank 155', 'Withdraw: Hammer, Saw, Beer, Teleport Runes, Duelling Ring, Ardy Cloak, Dramen Staff, Raw Chicken, Raw Swordfish, Chaos Runes (14 Inventory Slots) + Food',
              'Withdraw: Teleport Runes, Duelling Ring, Ardy Cloak, Dramen Staff, Raw Chicken, Raw Swordfish, Chaos Runes (11 Inventory Slots) + Food')
    r.replace(E5, 'Bank 155', 'Free some inventory at castle wars: Keep Chicken, Swordfish, Hammer, Saw, Beer',
              'Free some inventory at castle wars: Keep Chicken & Swordfish')
    r.replace(E5, 'Bank 155', 'Complete Tower of Life',
              'Use the Raw Chicken and Raw Swordfish on the altar in the Tower of Life basement (Tower of Life was done in Bank 60B)')


def episode_6_to_8(r):
    r.replace(E6, 'Bank 176', 'Routeadvies — Lange doorlopende midgame-checkpoint…',
              'Route note — Long midgame checkpoint: B0aty groups many separate combat preparation steps here; follow the sub-goals exactly and check the current death rules before every boss.')
    r.delete(E6, 'Bank 176', 'Complete Twilights Promise')
    r.insert_after(E6, 'Bank 176', 'Start A Great Brain Robbery & plant Watermelons in the patch on Harmony Island', [
        'Sailing B (optional, 30 to 52 Sailing): do it now that you have 51+ Slayer',
        'Train to 45 Sailing with The Tempor Tantrum or AFK salvaging during farm and birdhouse timers',
        'Troubled Tortugans needs 45 Sailing, 51 Slayer, 48 Construction, 45 Hunter, 40 Woodcutting and 34 Crafting; bring an axe, hammer, saw, food and combat gear',
        'Sail to the Remote Island north-west of The Great Conch and talk to the Injured Tortugan to start Troubled Tortugans',
        'HCIM: the Shellbane Gryphon is level 235; wear the Tortugan shield (cape slot) for the whole fight and pray Protect from Melee or Missiles',
        'HCIM: keep 40+ kg equipped or put trees or eggs between you and the charge, step away from the orange orb and keep moving during the whirlwinds [Troubled Tortugans]',
        'Complete Troubled Tortugans (10,000 Sailing, 8,000 Slayer and The Great Conch with fairy ring CJQ)',
        'Train to 52 Sailing (The Red Reef also needs 48 Smithing)',
        'Talk to Elder Raley at his house just south of the Summer Shore bank on The Great Conch and start The Red Reef',
        'HCIM: pray Protect from Missiles when the two pirate ships at Last Light appear and Protect from Melee against the six pirates; your boat sinking is safe, but dying yourself is a real death',
        'HCIM: Black Eye Bethel (level 191) can disable your protection prayers with her special, so step away from her charged attacks [The Red Reef]',
        'Move off the blue particles under you during the giant lobster fight [The Red Reef]',
        "Complete The Red Reef (15,000 Sailing, 5,000 Smithing, the Sacred Grove and the Bosun's workbench)",
    ])
    r.insert_after(E6, 'Bank 176', "Complete Fire Cape if you're confident Meleeing after Defender", [
        'The Fight Caves no longer have a cooldown after you leave, so you can retry later without waiting',
    ])
    r.insert_after(E7, 'Introductie', 'Complete Knight Waves (flinch with Zombie Axe)', [
        'Leaving Knight Waves through the door no longer resets your progress',
    ])
    r.insert_before(E8, 'Introductie', "Use Harralanders, Avantoe & Irits in Mastering Mixology (Don't grind them all incase the ratios aren't correct. 10k+ is good then top up)", [
        'Minigame Teleport now takes you straight to Mastering Mixology',
    ])
    r.insert_after(E8, 'Introductie', 'WHEN 60 PRAYER BANKED GET CHIVALRY WITH VARLAMORE PRAYER METHOD (Wave 1 colloseum for Sunfire Splinters)', [
        'Chivalry now drains as slowly as Deadeye and Mystic Vigour',
    ])


def episode_10_to_12(r):
    r.insert_after(E10, 'Bank 187', 'Head to Pollnivneach and train thieving until 71 (Blackjack Bandits until 65 then Menaphites to 71)', [
        'OPTIONAL: with 50 Sailing, stall thieving in Port Roberts (gems, ores and cannonballs) is a calmer alternative without knock-outs',
    ])
    r.insert_section_before(E10, 'Bank 193', 'Sailing C — 52 to 62 Sailing and Fallen From Grace', [
        'Route note — Sailing block C: Fallen From Grace unlocks golem crafting, the new alternative to glassblowing for Crafting from 60 to 85, plus Mortimer and the Mad Angel.',
        'Train to 47 Runecraft with a few extra games of Guardians of the Rift',
        'Train to 53 Mining',
        'Train to 62 Sailing',
        'Fallen From Grace needs 62 Sailing, 60 Crafting, 53 Mining and 47 Runecraft (none boostable) and your own raft or skiff, not a sloop',
        'Sail to Wyrmscraig and talk to Cormac in the centre hut of Auchrie to start Fallen From Grace',
        'HCIM: the Mad Angel (level 270) is weak to crush; pray Protect from Melee, stand 2+ tiles behind her for the dust cleave and switch to Protect from Magic for the lightning [Fallen From Grace]',
        'HCIM: you can climb the church pew to escape the Mad Angel at any time [Fallen From Grace]',
        'Complete Fallen From Grace (10,000 Crafting, 12,500 Sailing, 5,000 Mining and 5,000 Runecraft)',
        'Bank anywhere and deposit all',
    ])
    r.insert_after(E10, 'Bank 193', 'If you are 80 crafting from the gems & battlestaffs then you can immediately skip to the glory steps…', [
        'NEW option: after Fallen From Grace you can train Crafting with golem crafting (bring hunter furs) instead of Superglass Make glassblowing; pick one method',
    ])
    r.insert_after(E11, 'Bank 203', 'Tormented Synapse Order: Purging Staff -> Scorching Bow -> Emberlight…', [
        'NEW slayer master Mortimer (70 Slayer and 100 combat, after Fallen From Grace): you pick one of two tasks with superior monsters, but skips cost 100 points and Turael resets do not work, so keep using Duradel while you skip tasks',
    ])
    r.replace(E12, 'Bank 211', 'Routeadvies — Zenyte-jewellery → Zulrah-voorbereiding…',
              'Route note — Zenyte jewellery and Zulrah preparation: the route switches here from fixed bank stops to RNG and boss-based loops; separate expeditions follow in the endgame chapters.')


def parse_endgame(md_text):
    chapters = []
    title = None
    for raw in md_text.splitlines():
        line = raw.rstrip()
        if not line or line.startswith('<!--') or line.startswith('-->') or line.startswith(('Source for', 'Format:', 'Keep every', 'English translation')):
            continue
        if line.startswith('# '):
            title = line[2:].strip()
        elif line.startswith('## '):
            chapters.append({'title': line[3:].strip(), 'sections': []})
        elif line.startswith('### '):
            chapters[-1]['sections'].append({'title': line[4:].strip(), 'steps': []})
        elif line.startswith('- '):
            chapters[-1]['sections'][-1]['steps'].append(step(line[2:].strip()))
        else:
            raise ValueError(f'unexpected endgame line: {line!r}')
    return {'title': title or ENDGAME_TITLE, 'chapters': chapters}


DUTCH = re.compile(r'\b(?:Routeadvies|LET OP|voor|zodat|wanneer|je|jouw|niet|bewaar|haal|eerst|alleen|gevaarlijk|veilig)\b', re.I)


def validate(guide):
    seen = set()
    for chapter in guide['chapters']:
        for section in chapter['sections']:
            key = (chapter['title'], section['title'])
            if key in seen:
                raise ValueError(f'duplicate section {key}')
            seen.add(key)
            if not section['steps']:
                raise ValueError(f'empty section {key}')
            for st in section['steps']:
                t = text_of(st)
                if not t.strip():
                    raise ValueError(f'empty step in {key}')
                if DUTCH.search(t):
                    raise ValueError(f'Dutch text left in {key}: {t!r}')
    return guide


def build(write=True):
    base = json.loads(BASE.read_text('utf8'))
    route = Route(base)
    for patch in (episode_1, episode_2, episode_3, episode_4, episode_5, episode_6_to_8, episode_10_to_12):
        patch(route)

    endgame = validate(parse_endgame(ENDGAME_MD.read_text('utf8')))
    endgame['title'] = ENDGAME_TITLE
    full = validate({'title': ROUTE_TITLE, 'chapters': base['chapters'] + endgame['chapters']})

    # New and changed steps must stay short enough for Guide Overlay's sentence splitter.
    base_texts = {text_of(st) for c in json.loads(BASE.read_text('utf8'))['chapters'] for s in c['sections'] for st in s['steps']}
    for c in full['chapters']:
        for s in c['sections']:
            for st in s['steps']:
                t = text_of(st)
                if t not in base_texts and len(t) > MAX_STEP_CHARS:
                    raise ValueError(f'new step longer than {MAX_STEP_CHARS} chars in {s["title"]}: {t!r}')

    if write:
        ENDGAME_OUT.write_text(json.dumps(endgame, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
        ROUTE_OUT.write_text(json.dumps(full, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    return full, endgame


def count(guide):
    return sum(len(s['steps']) for c in guide['chapters'] for s in c['sections'])


if __name__ == '__main__':
    full, endgame = build()
    print(json.dumps({'route_steps': count(full), 'route_chapters': len(full['chapters']),
                      'endgame_steps': count(endgame), 'endgame_chapters': len(endgame['chapters'])}, indent=2))
